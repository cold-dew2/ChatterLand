package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.AiProviderException;
import com.example.backend.chld.exception.ConsentRequiredException;
import com.example.backend.chld.mapper.SpeechFeedbackMapper;
import com.example.backend.chld.service.ConsentPolicy;
import com.example.backend.chld.service.ConsentService;
import com.example.backend.chld.service.SpeechFeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 분석 근거 → AI 학습 피드백.
 * - 근거가 부족하면(판정 보류, 분석 미완료, 교사 확인 전 언어재활 녹음 등) AI를 부르지 않고 NOT_EVALUABLE과 이유를 돌려준다.
 * - AI에는 분석 근거 문장만 보낸다(이름·ID·음성·교사 메모는 보내지 않는다).
 * - 발음 정확도는 '미평가', 말하기 속도·유창성은 검증된 지표가 없어 근거로 쓰지 않는다. 텍스트 일치율은 발음 점수가 아님을 명시한다.
 * - 응답에 점수·등급·진단 표현이 있으면 저장하지 않고 오류로 처리한다.
 */
@Service
public class SpeechFeedbackServiceImpl implements SpeechFeedbackService {
    static final String PROMPT_VERSION = "feedback-v1";
    static final String STATUS_NOT_GENERATED = "NOT_GENERATED";
    static final String STATUS_READY = "READY";
    static final String STATUS_NOT_EVALUABLE = "NOT_EVALUABLE";
    static final int MAX_TEXT = 400;
    private static final Pattern SCORE_LIKE = Pattern.compile("\\d+\\s*(점|%|퍼센트|등급|위)|점수|등급|진단|장애|치료가 필요");
    private static final Map<String,String> JUDGEMENT = Map.of("ACCEPTABLE", "목표 발음으로 들림", "NEEDS_PRACTICE", "더 연습이 필요함", "UNCLEAR", "녹음만으로는 판단하기 어려움");
    private static final Map<String,String> ERROR_TYPE = Map.of("SUBSTITUTION", "다른 소리로 바뀜", "OMISSION", "소리가 빠짐", "DISTORTION", "소리가 정확하지 않음", "ADDITION", "소리가 더해짐");
    private static final Map<String,String> SLOT = Map.of("ONSET", "첫소리", "NUCLEUS", "가운데 소리", "CODA", "받침", "SYLLABLE", "음절");

    static final String SYSTEM_INSTRUCTION = String.join("\n",
            "너는 초등학생의 한국어 말하기 연습을 도와주는 다정한 선생님이야.",
            "아래 '분석 근거'에 적힌 사실만 사용해서, 아이에게 직접 말하듯 쉬운 존댓말로 2~3문장(150자 안팎) 피드백을 써.",
            "규칙:",
            "1. 분석 근거에 없는 발음 오류나 학습 문제를 만들어 내지 마.",
            "2. 점수, 숫자, 퍼센트, 등급을 쓰지 마.",
            "3. 발음이 정확하다거나 틀렸다고 판정하지 마. 발음 정확도는 아직 평가하지 않아.",
            "4. 말하기 속도나 유창성에 대해 평가하지 마.",
            "5. '자동 후보'는 확정이 아니야. '컴퓨터에는 ~처럼 들렸어요'처럼 조심스럽게 말해.",
            "6. '선생님 확인 결과'가 있으면 그 내용을 그대로 따르고 바꾸거나 반대로 말하지 마.",
            "7. 진단, 장애, 치료 같은 말을 쓰지 마.",
            "8. 잘한 점 한 가지와 다음에 해 볼 연습 한 가지를 말해.",
            "9. 마크다운, 목록, 이모지 없이 문장만 써.");

    /** 분석에서 뽑은 근거. notEvaluable이 있으면 AI를 부르지 않는다. */
    record Evidence(String notEvaluable, List<String> lines, List<String> sources) {
        static Evidence notEvaluable(String reason) { return new Evidence(reason, List.of(), List.of()); }
        String text() { return String.join("\n", lines); }
        String hash() { return IdempotencyKeys.sha256((PROMPT_VERSION + "\n" + text()).getBytes(StandardCharsets.UTF_8)); }
    }

    private final SpeechFeedbackMapper mapper;
    private final AiTextClient ai;
    private final ConsentService consents;

    public SpeechFeedbackServiceImpl(SpeechFeedbackMapper mapper, AiTextClient ai, ConsentService consents) {
        this.mapper = mapper; this.ai = ai; this.consents = consents;
    }

    @Override
    public Map<String,Object> feedback(long userId, Map<String,Object> analysis) {
        Evidence evidence = evidence(analysis);
        String analysisId = String.valueOf(analysis.get("analysisId"));
        if (evidence.notEvaluable() != null) return notEvaluable(analysisId, evidence.notEvaluable());
        Map<String,Object> saved = mapper.findFeedback(analysisId);
        if (saved != null && evidence.hash().equals(saved.get("evidenceHash"))) return ready(analysisId, saved, evidence);
        Map<String,Object> response = base(analysisId, STATUS_NOT_GENERATED);
        response.put("basedOn", evidence.sources());
        response.put("available", ai.isConfigured());
        response.put("consentRequired", !hasConsent(userId));
        return response;
    }

    @Override
    public Map<String,Object> generate(long userId, Map<String,Object> analysis) {
        Evidence evidence = evidence(analysis);
        String analysisId = String.valueOf(analysis.get("analysisId"));
        if (evidence.notEvaluable() != null) return notEvaluable(analysisId, evidence.notEvaluable());
        Map<String,Object> saved = mapper.findFeedback(analysisId);
        if (saved != null && evidence.hash().equals(saved.get("evidenceHash"))) return ready(analysisId, saved, evidence);
        // 분석 근거(글자)를 외부 AI로 보내므로 AI 외부 전송 동의가 필요하다.
        consents.requireConsent(userId, ConsentPolicy.AI_CHAT);
        String text = sanitize(ai.generate(SYSTEM_INSTRUCTION, List.of(new AiTextClient.Turn("user", "분석 근거:\n" + evidence.text())), 0.3, 2048));
        mapper.upsertFeedback(analysisId, text, evidence.hash(), ai.modelName(), PROMPT_VERSION);
        return ready(analysisId, mapper.findFeedback(analysisId), evidence);
    }

    /** 분석 응답에서 AI에 보낼 근거를 만든다. 근거가 부족하면 이유를 담은 NOT_EVALUABLE. */
    static Evidence evidence(Map<String,Object> a) {
        if (!"COMPLETED".equals(a.get("status"))) return Evidence.notEvaluable("분석이 끝나지 않았거나 실패한 녹음이라 설명할 수 없어요.");
        String mode = String.valueOf(a.get("evaluationMode"));
        if ("HOLD".equals(a.get("assessmentStatus")))
            return Evidence.notEvaluable("녹음이 또렷하지 않아 결과를 판단하지 않았어요. 다시 녹음하면 설명을 볼 수 있어요.");
        String target = text(a.get("targetText")), transcript = text(a.get("transcript"));
        if (target.isEmpty() || transcript.isEmpty()) return Evidence.notEvaluable("컴퓨터가 알아들은 말이 없어 설명할 수 없어요.");
        List<String> lines = new ArrayList<>();
        lines.add("- 목표 문장: \"" + target + "\"");
        if ("PRONUNCIATION_REVIEW".equals(mode)) {
            // 언어재활 녹음은 선생님이 확인한 결과만 근거로 쓴다(자동 후보는 쓰지 않는다).
            if (!"REVIEWED".equals(a.get("reviewStatus")) || a.get("teacherJudgement") == null)
                return Evidence.notEvaluable("선생님이 녹음을 확인한 뒤에 설명을 볼 수 있어요.");
            lines.add("- 선생님 확인 결과(그대로 따를 것): " + JUDGEMENT.getOrDefault(String.valueOf(a.get("teacherJudgement")), "선생님이 확인함"));
            if (a.get("teacherConfirmedErrors") instanceof List<?> errors && !errors.isEmpty()) {
                for (Object item : errors.subList(0, Math.min(5, errors.size()))) {
                    if (!(item instanceof Map<?,?> e)) continue;
                    String position = text(e.get("position")), produced = text(e.get("produced"));
                    lines.add("- 선생님이 확정한 오류: " + (position.isEmpty() ? "" : position + "의 ") + text(e.get("phoneme")) + " 소리가 "
                            + ERROR_TYPE.getOrDefault(text(e.get("errorType")), "다르게 남") + (produced.isEmpty() ? "" : "(" + produced + "처럼 남)"));
                }
            } else lines.add("- 선생님이 확정한 오류: 없음");
            lines.add("- 발음 정확도: 미평가(점수 없음)");
            return new Evidence(null, lines, List.of("TEACHER_CONFIRMED"));
        }
        if (!"SENTENCE_MATCH".equals(mode)) return Evidence.notEvaluable("이 분석 방식은 AI 설명을 지원하지 않아요.");
        lines.add("- 컴퓨터가 알아들은 문장(음성 인식 결과이며 발음 판정이 아님): \"" + transcript + "\"");
        BigDecimal rate = a.get("textMatchRate") instanceof Number n ? new BigDecimal(n.toString()) : null;
        if (rate != null && rate.compareTo(BigDecimal.valueOf(100)) == 0) lines.add("- 알아들은 문장이 목표 문장과 같음");
        if (a.get("comparison") instanceof Map<?,?> comparison && comparison.get("words") instanceof List<?> words) {
            for (Object item : words) {
                if (!(item instanceof Map<?,?> w)) continue;
                String type = text(w.get("type"));
                if ("MISSING".equals(type)) lines.add("- 알아듣지 못한 낱말: '" + text(w.get("expected")) + "'");
                else if ("INSERTED".equals(type)) lines.add("- 목표 문장에 없는데 들린 낱말: '" + text(w.get("recognized")) + "'");
                else if (!"MATCH".equals(type)) lines.add("- 다르게 들린 낱말: '" + text(w.get("expected")) + "' → '" + text(w.get("recognized")) + "'");
            }
        }
        if (a.get("phonemeCandidates") instanceof List<?> candidates) {
            int count = 0;
            for (Object item : candidates) {
                if (!(item instanceof Map<?,?> c) || count >= 5) continue;
                String expected = text(c.get("expected")), produced = text(c.get("produced"));
                if (expected.isEmpty() && produced.isEmpty()) continue;
                lines.add("- 자동 후보(확정 아님): '" + text(c.get("targetSyllable")) + "'의 " + SLOT.getOrDefault(text(c.get("slot")), "소리") + " "
                        + (expected.isEmpty() ? "-" : expected) + " → " + (produced.isEmpty() ? "없음" : produced));
                count++;
            }
        }
        if ("WORD".equals(a.get("analysisType"))) lines.add("- 참고: 낱말 하나는 컴퓨터가 다르게 알아듣는 경우가 많음");
        if (a.get("recognitionConfidence") instanceof Number confidence && confidence.doubleValue() < 0.6)
            lines.add("- 참고: 컴퓨터가 확실하게 알아듣지 못함(인식 결과가 틀렸을 수 있음)");
        lines.add("- 발음 정확도: 미평가(점수 없음)");
        lines.add("- 말하기 속도·유창성: 평가 지표 없음");
        return new Evidence(null, lines, List.of("AUTO_ANALYSIS"));
    }

    /** AI 응답 확인: 마크다운 기호를 지우고, 비었거나 너무 길거나 점수·진단 표현이 있으면 저장하지 않는다. */
    static String sanitize(String raw) {
        String text = raw == null ? "" : raw.replaceAll("[*#`>]", "").replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || text.length() > MAX_TEXT || SCORE_LIKE.matcher(text).find())
            throw new AiProviderException(AiProviderException.BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                    "AI가 만든 설명이 규칙에 맞지 않아 보여 주지 않았어요. 다시 시도해 주세요.");
        return text;
    }

    private boolean hasConsent(long userId) {
        try { consents.requireConsent(userId, ConsentPolicy.AI_CHAT); return true; }
        catch (ConsentRequiredException e) { return false; }
    }

    private static Map<String,Object> base(String analysisId, String status) {
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("analysisId", analysisId);
        response.put("status", status);
        response.put("source", "AI");
        return response;
    }

    private static Map<String,Object> notEvaluable(String analysisId, String reason) {
        Map<String,Object> response = base(analysisId, STATUS_NOT_EVALUABLE);
        response.put("reason", reason);
        return response;
    }

    private static Map<String,Object> ready(String analysisId, Map<String,Object> saved, Evidence evidence) {
        Map<String,Object> response = base(analysisId, STATUS_READY);
        response.put("text", saved.get("text"));
        response.put("modelName", saved.get("modelName"));
        response.put("promptVersion", saved.get("promptVersion"));
        response.put("generatedAt", saved.get("generatedAt"));
        response.put("basedOn", evidence.sources());
        return response;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
