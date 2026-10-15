package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.AiProviderException;
import com.example.backend.chld.mapper.KnowledgeMapper;
import com.example.backend.chld.service.impl.PronunciationRuleDetector.RuleMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 근거 검색(MariaDB, 외부 호출 없음). 출처·사용 권한이 확인되고(VERIFIED) 내용이 승인된(APPROVED + 승인 기록) 자료만 쓴다.
 * - 승인 조건(SQL·Java 두 단계에서 같은 기준): verification_status=VERIFIED, review_status=APPROVED,
 *   승인 기록(reviewed_by·reviewed_at)이 있고 reviewed_by가 실제 계정(users)일 것. 교사 설명 예시는 승인자가 TEACHER 계정일 것.
 * - 설정(app.ai.rag.allowed-statuses)은 검색 대상을 좁히기만 한다. APPROVED가 아닌 값(DRAFT 등)은 무시하고,
 *   APPROVED가 없으면 검색하지 않는다(근거 자료 부족). 설정만으로 미승인 자료를 검색에 넣을 수 없다.
 * - 조항 경로: 목표 문장에 적용될 수 있는 표준 발음법 조항(PronunciationRuleDetector)의 태그가 맞는 청크.
 *   조항 태그가 있는데 맞지 않으면 관련성이 없다고 보고 버린다(자모가 같다는 이유만으로 쓰지 않는다).
 *   학생 결과(자동 후보·교사 확정 오류)가 그 조항 위치와 겹치면 순위를 올린다.
 * - 안내 경로: 조항 태그가 없는 안내 자료(조음 위치·방법, 자음·모음·받침, 교사 설명 예시)는 학생 결과의 자모가 태그와 겹칠 때만 쓴다.
 *   점수는 조항 경로보다 항상 낮고, 같은 점수면 다루는 자모가 적은(더 구체적인) 청크를 먼저 둔다.
 */
@Slf4j
@Component
public class KnowledgeRetriever {
    static final int MAX_SOURCES = 3;
    static final String VERIFIED = "VERIFIED";
    /** 내용 승인 상태. 설정과 무관하게 이 상태의 자료만 검색한다. */
    static final String APPROVED = "APPROVED";
    static final String TEACHER_EXAMPLE = "TEACHER_EXAMPLE";
    /** 교사 설명 예시의 승인자 역할(승인 절차: 승인 교사 user_id, docs/design/ai-feedback.md 10.5) */
    static final String TEACHER_ROLE = "TEACHER";
    /** 조항 태그 없이 자모로 찾는 안내 자료 분류 */
    static final Set<String> GUIDE_CATEGORIES = Set.of("ARTICULATION_PLACE", "ARTICULATION_MANNER", "CONSONANT", "VOWEL", "CODA", TEACHER_EXAMPLE);
    private static final int MAX_GUIDE_SCORE = 2;

    /** 검색된 근거. marker는 프롬프트·응답에서 쓰는 번호([S1]). documentId가 출처 ID(source_id)다. */
    public record Source(String marker, String chunkId, String documentId, String title, String location, String citation,
                         String url, int version, String reviewStatus, String content, int score,
                         String category, String sourceVersion, String licenseNote) {
        public Source(String marker, String chunkId, String documentId, String title, String location, String citation,
                      String url, int version, String reviewStatus, String content, int score) {
            this(marker, chunkId, documentId, title, location, citation, url, version, reviewStatus, content, score, null, null, null);
        }

        Source withMarker(String value) {
            return new Source(value, chunkId, documentId, title, location, citation, url, version, reviewStatus, content, score, category, sourceVersion, licenseNote);
        }
    }

    public record Retrieval(List<RuleMatch> rules, List<Source> sources, long elapsedMs) {
        boolean empty() { return sources.isEmpty(); }
    }

    private record Candidate(Source source, boolean rulePath, int phonemeTags) { }

    private final KnowledgeMapper mapper;
    private final List<String> allowedStatuses;

    public KnowledgeRetriever(KnowledgeMapper mapper, @Value("${app.ai.rag.allowed-statuses:APPROVED}") String allowedStatuses) {
        this.mapper = mapper;
        List<String> configured = allowedStatuses == null ? List.of()
                : Arrays.stream(allowedStatuses.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        // 설정은 좁히기만 한다: APPROVED 외의 값은 무시(미승인 자료를 설정으로 검색에 넣지 못하게)
        this.allowedStatuses = configured.contains(APPROVED) ? List.of(APPROVED) : List.of();
        List<String> ignored = configured.stream().filter(s -> !APPROVED.equals(s)).distinct().toList();
        if (!ignored.isEmpty()) log.warn("RAG 검색 허용 상태 설정의 {}는 무시합니다(내용 승인 APPROVED 자료만 검색).", ignored);
        if (this.allowedStatuses.isEmpty()) log.warn("RAG 검색 허용 상태 설정에 APPROVED가 없어 자료 검색을 하지 않습니다(AI 설명은 근거 자료 부족).");
    }

    /**
     * @param target        목표 문장(정규화 전 원문도 됨)
     * @param focusSyllables 학생 결과가 가리키는 음절(자동 후보의 목표 음절·교사 확정 위치)
     * @param focusPhonemes  학생 결과의 자모(자동 후보의 기대·인식 자모, 교사 확정 음소)
     */
    public Retrieval retrieve(String target, Set<String> focusSyllables, Set<String> focusPhonemes) {
        long started = System.nanoTime();
        List<RuleMatch> rules = PronunciationRuleDetector.detect(TranscriptComparator.normalize(target));
        if ((rules.isEmpty() && focusPhonemes.isEmpty()) || allowedStatuses.isEmpty()) return new Retrieval(rules, List.of(), elapsed(started));
        List<Map<String,Object>> chunks;
        try {
            chunks = mapper.findSearchableChunks(allowedStatuses);
        } catch (DataAccessException e) {
            throw new AiProviderException(AiProviderException.RAG_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "설명에 쓸 교육 자료를 찾지 못했어요(자료 검색 오류). 잠시 뒤 다시 시도해 주세요.");
        }
        List<Candidate> scored = new ArrayList<>();
        for (Map<String,Object> chunk : chunks) {
            if (!approved(chunk)) continue;
            Set<String> tags = Arrays.stream(String.valueOf(chunk.get("tags")).split(",")).map(String::trim).collect(Collectors.toSet());
            Set<String> phonemeTags = tags.stream().filter(t -> t.startsWith("phoneme:")).collect(Collectors.toSet());
            boolean hasRuleTag = tags.stream().anyMatch(t -> t.startsWith("rule:"));
            int score;
            if (hasRuleTag) {
                List<RuleMatch> matched = rules.stream().filter(r -> tags.contains("rule:" + r.rule())).toList();
                if (matched.isEmpty()) continue; // 관련 조항이 아니면 근거로 쓰지 않는다
                score = 3 * (int) matched.stream().map(RuleMatch::rule).distinct().count();
                if (matched.stream().anyMatch(r -> focusSyllables.stream().anyMatch(s -> r.syllables().contains(s)))) score += 2;
                if (focusPhonemes.stream().anyMatch(p -> tags.contains("phoneme:" + p))) score += 1;
            } else if (GUIDE_CATEGORIES.contains(String.valueOf(chunk.get("category")))) {
                int matched = (int) focusPhonemes.stream().filter(p -> phonemeTags.contains("phoneme:" + p)).count();
                if (matched == 0) continue; // 학생 결과의 자모를 다루지 않는 안내 자료는 쓰지 않는다
                score = Math.min(MAX_GUIDE_SCORE, matched);
            } else {
                continue; // 조항 태그도 안내 분류도 없는 자료는 관련성을 판단할 수 없다
            }
            scored.add(new Candidate(new Source(null, String.valueOf(chunk.get("chunkId")), String.valueOf(chunk.get("documentId")), String.valueOf(chunk.get("title")),
                    String.valueOf(chunk.get("location")), String.valueOf(chunk.get("citation")), (String) chunk.get("sourceUrl"),
                    chunk.get("version") instanceof Number v ? v.intValue() : 1, String.valueOf(chunk.get("reviewStatus")),
                    String.valueOf(chunk.get("content")), score, (String) chunk.get("category"), (String) chunk.get("sourceVersion"),
                    (String) chunk.get("licenseNote")), hasRuleTag, phonemeTags.size()));
        }
        scored.sort(Comparator.comparingInt((Candidate c) -> c.source().score()).reversed()
                .thenComparing(c -> !c.rulePath())
                .thenComparingInt(c -> c.rulePath() ? 0 : c.phonemeTags()));
        List<Source> top = new ArrayList<>();
        for (Candidate c : scored.subList(0, Math.min(MAX_SOURCES, scored.size()))) top.add(c.source().withMarker("S" + (top.size() + 1)));
        return new Retrieval(rules, top, elapsed(started));
    }

    /**
     * 조회 조건(Mapper)을 믿지 않고 같은 승인 기준을 다시 확인한다(SQL이 바뀌거나 잘못돼도 미승인 자료를 쓰지 않게).
     * 출처·권한 확인, 내용 승인(APPROVED, 설정과 무관), 승인 기록(승인자·승인 시각), 승인자가 실제 계정인지,
     * 교사 설명 예시면 승인자가 TEACHER인지.
     */
    static boolean approved(Map<String,Object> chunk) {
        if (!VERIFIED.equals(chunk.get("verificationStatus"))) return false;
        if (!APPROVED.equals(chunk.get("reviewStatus"))) return false;
        if (!(chunk.get("reviewedBy") instanceof Number approver) || chunk.get("reviewedAt") == null) return false;
        if (!(chunk.get("reviewerUserId") instanceof Number account) || account.longValue() != approver.longValue()) return false;
        return !TEACHER_EXAMPLE.equals(chunk.get("category")) || TEACHER_ROLE.equals(chunk.get("reviewerRole"));
    }

    private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
