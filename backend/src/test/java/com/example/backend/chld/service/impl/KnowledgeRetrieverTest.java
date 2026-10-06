package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.AiProviderException;
import com.example.backend.chld.mapper.KnowledgeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 근거 검색: 출처·사용 권한이 확인되고(VERIFIED) 승인된 자료만. 목표 문장에 적용될 수 있는 조항 태그가 맞는 자료를 학생 결과와 겹치는 순으로.
 * 조항 자료는 자모만 같다고 쓰지 않는다. 조항 태그가 없는 안내 자료(조음 위치·방법 등)는 학생 결과의 자모가 겹칠 때만, 조항 자료보다 낮은 순위로 쓴다.
 */
class KnowledgeRetrieverTest {
    private final KnowledgeMapper mapper = mock(KnowledgeMapper.class);
    private final KnowledgeRetriever retriever = new KnowledgeRetriever(mapper, "APPROVED");

    private static Map<String,Object> chunk(String id, String location, String tags) {
        return Map.of("chunkId", id, "documentId", "std", "location", location, "content", location + " 내용", "tags", tags,
                "title", "표준 발음법", "citation", "고시", "version", 2, "reviewStatus", "APPROVED", "verificationStatus", "VERIFIED");
    }

    /** 문서 분류·확인 상태를 지정한 청크 */
    private static Map<String,Object> chunk(String id, String documentId, String category, String verification, String review, Long reviewedBy, String tags) {
        Map<String,Object> chunk = new HashMap<>(Map.of("chunkId", id, "documentId", documentId, "location", id + " 위치", "content", id + " 내용", "tags", tags,
                "title", documentId + " 제목", "citation", "출처 인용", "version", 1, "reviewStatus", review, "verificationStatus", verification));
        chunk.put("category", category); chunk.put("reviewedBy", reviewedBy);
        chunk.put("sourceVersion", "문화체육관광부 고시 제2017-13호"); chunk.put("licenseNote", "저작권법 제24조의2");
        return chunk;
    }

    private static List<String> ids(KnowledgeRetriever.Retrieval retrieval) {
        return retrieval.sources().stream().map(KnowledgeRetriever.Source::chunkId).toList();
    }

    @Test
    void detectsRulesFromTheTargetSpellingOnly() {
        assertEquals(List.of(20), PronunciationRuleDetector.detect("신라").stream().map(PronunciationRuleDetector.RuleMatch::rule).toList());
        assertTrue(PronunciationRuleDetector.detect("국물").stream().anyMatch(r -> r.rule() == 18));
        assertTrue(PronunciationRuleDetector.detect("국밥").stream().anyMatch(r -> r.rule() == 23));
        assertTrue(PronunciationRuleDetector.detect("같이").stream().anyMatch(r -> r.rule() == 17));
        assertTrue(PronunciationRuleDetector.detect("강릉").stream().anyMatch(r -> r.rule() == 19));
        assertTrue(PronunciationRuleDetector.detect("옷을").stream().anyMatch(r -> r.rule() == 13));
        assertTrue(PronunciationRuleDetector.detect("부엌").stream().anyMatch(r -> r.rule() == 9));
        assertTrue(PronunciationRuleDetector.detect("라디오").isEmpty(), "받침이 없으면 적용 조항이 없다");
        assertTrue(PronunciationRuleDetector.detect("좋아요").stream().noneMatch(r -> r.rule() == 13), "ㅎ 받침은 연음 규칙으로 보지 않는다");
        assertTrue(PronunciationRuleDetector.detect("산 라디오").stream().noneMatch(r -> r.rule() == 20), "어절 경계는 넘지 않는다");
    }

    @Test
    void returnsOnlyChunksWhoseRuleAppliesRankedByTheLearnersFocus() {
        when(mapper.findSearchableChunks(List.of("APPROVED"))).thenReturn(List.of(
                chunk("c19", "제19항", "rule:19,phoneme:ㄹ,phoneme:ㄴ"),
                chunk("c20", "제20항", "rule:20,phoneme:ㄴ,phoneme:ㄹ"),
                chunk("c08", "제8항", "rule:8,phoneme:ㄴ")));
        KnowledgeRetriever.Retrieval result = retriever.retrieve("신라", Set.of("신"), Set.of("ㄴ", "ㄹ"));
        assertEquals(List.of("c20"), result.sources().stream().map(KnowledgeRetriever.Source::chunkId).toList(), "자모가 같아도 적용되지 않는 조항(19·8항)은 버린다");
        assertEquals("S1", result.sources().get(0).marker());
        assertEquals(3 + 2 + 1, result.sources().get(0).score());
    }

    @Test
    void noApplicableRuleOrNoApprovedChunkMeansNoEvidenceAndADatabaseErrorIsDistinct() {
        assertTrue(retriever.retrieve("라디오", Set.of("라"), Set.of()).empty());
        verifyNoInteractions(mapper); // 적용 조항도, 안내 자료를 찾을 학생 결과 자모도 없으면 조회하지 않는다
        when(mapper.findSearchableChunks(List.of("APPROVED"))).thenReturn(List.of(chunk("c20", "제20항", "rule:20,phoneme:ㄴ,phoneme:ㄹ")));
        assertTrue(retriever.retrieve("라디오", Set.of("라"), Set.of("ㄹ")).empty(), "적용 조항이 없으면 조항 자료는 자모가 같아도 쓰지 않는다");
        when(mapper.findSearchableChunks(List.of("APPROVED"))).thenReturn(List.of());
        assertTrue(retriever.retrieve("신라", Set.of(), Set.of()).empty());
        when(mapper.findSearchableChunks(List.of("APPROVED"))).thenThrow(new DataAccessResourceFailureException("db"));
        assertEquals("RAG_UNAVAILABLE", assertThrows(AiProviderException.class, () -> retriever.retrieve("신라", Set.of(), Set.of())).getCode());
    }

    @Test
    void onlyVerifiedAndApprovedSourcesAreUsedEvenIfTheQueryReturnedMore() {
        when(mapper.findSearchableChunks(List.of("APPROVED"))).thenReturn(List.of(
                chunk("pending", "doc-p", "PRONUNCIATION_RULE", "PENDING", "APPROVED", 9L, "rule:20,phoneme:ㄴ"),
                chunk("rejected", "doc-r", "PRONUNCIATION_RULE", "REJECTED", "APPROVED", 9L, "rule:20,phoneme:ㄴ"),
                chunk("draft", "doc-d", "PRONUNCIATION_RULE", "VERIFIED", "DRAFT", null, "rule:20,phoneme:ㄴ"),
                chunk("retired", "doc-x", "PRONUNCIATION_RULE", "VERIFIED", "RETIRED", 9L, "rule:20,phoneme:ㄴ"),
                chunk("verified", "nikl-pron-assimilation", "PRONUNCIATION_RULE", "VERIFIED", "APPROVED", 9L, "rule:20,phoneme:ㄴ,phoneme:ㄹ")));
        KnowledgeRetriever.Retrieval result = retriever.retrieve("신라", Set.of("신"), Set.of("ㄴ", "ㄹ"));
        assertEquals(List.of("verified"), ids(result), "출처·권한 미확인(PENDING·REJECTED), 검수 전(DRAFT), 사용 중지(RETIRED) 자료는 쓰지 않는다");
        KnowledgeRetriever.Source source = result.sources().get(0);
        assertEquals("nikl-pron-assimilation", source.documentId(), "출처 ID를 그대로 보존한다");
        assertEquals("nikl-pron-assimilation 제목", source.title());
        assertEquals("verified 위치", source.location());
        assertEquals("PRONUNCIATION_RULE", source.category());
        assertEquals("문화체육관광부 고시 제2017-13호", source.sourceVersion());
        assertEquals("저작권법 제24조의2", source.licenseNote());
    }

    @Test
    void guideSourcesMatchTheLearnersJamoRankBelowRulesAndPreferSpecificChunks() {
        when(mapper.findSearchableChunks(List.of("APPROVED"))).thenReturn(List.of(
                chunk("consonant-list", "nikl-pron-consonant", "CONSONANT", "VERIFIED", "APPROVED", 9L,
                        "slot:ONSET,phoneme:ㄱ,phoneme:ㄴ,phoneme:ㄷ,phoneme:ㄹ,phoneme:ㅁ,phoneme:ㅂ,phoneme:ㅅ,phoneme:ㅇ"),
                chunk("place-alveolar", "nikl-pron-place", "ARTICULATION_PLACE", "VERIFIED", "APPROVED", 9L, "phoneme:ㄷ,phoneme:ㄴ,phoneme:ㄹ"),
                chunk("manner-liquid", "nikl-pron-manner", "ARTICULATION_MANNER", "VERIFIED", "APPROVED", 9L, "phoneme:ㄹ"),
                chunk("place-velar", "nikl-pron-place", "ARTICULATION_PLACE", "VERIFIED", "APPROVED", 9L, "phoneme:ㄱ,phoneme:ㅇ"),
                chunk("untagged", "misc", null, "VERIFIED", "APPROVED", 9L, "phoneme:ㄹ"),
                chunk("rule-20", "nikl-pron-assimilation", "PRONUNCIATION_RULE", "VERIFIED", "APPROVED", 9L, "rule:20,phoneme:ㄴ,phoneme:ㄹ")));
        // 적용 조항이 없는 낱말(라디오)의 첫소리 후보 ㄹ→ㄴ: 두 자모를 다 다루는 안내 자료가 먼저, 같은 점수면 더 구체적인 자료가 먼저
        KnowledgeRetriever.Retrieval onset = retriever.retrieve("라디오", Set.of("라"), Set.of("ㄹ", "ㄴ"));
        assertEquals(List.of("place-alveolar", "consonant-list", "manner-liquid"), ids(onset));
        assertTrue(onset.sources().stream().allMatch(s -> s.score() <= 2), "안내 자료 점수는 조항 자료(3점 이상)보다 낮다");
        assertFalse(ids(onset).contains("place-velar"), "학생 결과의 자모를 다루지 않는 안내 자료는 쓰지 않는다");
        assertFalse(ids(onset).contains("untagged"), "분류가 없는 무태그 자료는 관련성을 판단할 수 없어 쓰지 않는다");
        // 조항이 적용되는 낱말(신라): 조항 자료가 맨 앞이고, 남은 자리에 안내 자료가 온다
        KnowledgeRetriever.Retrieval rule = retriever.retrieve("신라", Set.of("신"), Set.of("ㄴ", "ㄹ"));
        assertEquals("rule-20", rule.sources().get(0).chunkId());
        assertEquals(List.of("S1", "S2", "S3"), rule.sources().stream().map(KnowledgeRetriever.Source::marker).toList());
    }

    @Test
    void teacherExamplesNeedARecordedApproverOnTopOfApprovalAndVerification() {
        when(mapper.findSearchableChunks(List.of("APPROVED"))).thenReturn(List.of(
                chunk("example-no-approver", "teacher-ex-1", "TEACHER_EXAMPLE", "VERIFIED", "APPROVED", null, "phoneme:ㄹ"),
                chunk("example-unverified", "teacher-ex-2", "TEACHER_EXAMPLE", "PENDING", "APPROVED", 5L, "phoneme:ㄹ"),
                chunk("example-approved", "teacher-ex-3", "TEACHER_EXAMPLE", "VERIFIED", "APPROVED", 5L, "phoneme:ㄹ")));
        KnowledgeRetriever.Retrieval result = retriever.retrieve("라디오", Set.of("라"), Set.of("ㄹ"));
        assertEquals(List.of("example-approved"), ids(result));
        assertEquals("TEACHER_EXAMPLE", result.sources().get(0).category());
    }
}
