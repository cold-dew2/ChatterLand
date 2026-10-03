package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.AiProviderException;
import com.example.backend.chld.exception.ConsentRequiredException;
import com.example.backend.chld.mapper.SpeechFeedbackMapper;
import com.example.backend.chld.service.ConsentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 학습 피드백 규칙(외부 AI는 mock). 근거가 부족하면 AI를 부르지 않고, AI에는 확인된 분석 근거만 보내며,
 * 점수·진단 표현이 든 응답은 저장하지 않는다. 교사 확정 결과는 그대로 근거로 쓰고 바꾸지 않는다.
 */
class SpeechFeedbackServiceImplTest {
    private final SpeechFeedbackMapper mapper = mock(SpeechFeedbackMapper.class);
    private final AiTextClient ai = mock(AiTextClient.class);
    private final ConsentService consents = mock(ConsentService.class);
    private final SpeechFeedbackServiceImpl service = new SpeechFeedbackServiceImpl(mapper, ai, consents);

    private static Map<String,Object> sentence() {
        Map<String,Object> a = new HashMap<>();
        a.put("analysisId", "a-1"); a.put("studentId", 77); a.put("status", "COMPLETED"); a.put("evaluationMode", "SENTENCE_MATCH");
        a.put("assessmentStatus", "ERROR_CANDIDATES"); a.put("analysisType", "WORD"); a.put("targetText", "라디오"); a.put("transcript", "타디오");
        a.put("textMatchRate", 66.67); a.put("pronunciationScore", null); a.put("recognitionConfidence", 0.9);
        a.put("comparison", Map.of("words", List.of(Map.of("type", "SUBSTITUTED", "expected", "라디오", "recognized", "타디오"))));
        a.put("phonemeCandidates", List.of(Map.of("type", "SUBSTITUTION", "slot", "ONSET", "expected", "ㄹ", "produced", "ㅌ", "targetSyllable", "라")));
        return a;
    }

    private static Map<String,Object> therapy(String reviewStatus) {
        Map<String,Object> a = new HashMap<>(sentence());
        a.put("evaluationMode", "PRONUNCIATION_REVIEW"); a.put("textMatchRate", null); a.put("reviewStatus", reviewStatus);
        a.put("teacherJudgement", "REVIEWED".equals(reviewStatus) ? "NEEDS_PRACTICE" : null);
        a.put("teacherNote", "보호자 상담 내용: 비공개 메모");
        a.put("teacherConfirmedErrors", List.of(Map.of("phoneme", "ㄹ", "errorType", "DISTORTION", "position", "어두 초성")));
        return a;
    }

    @Test
    void sentenceEvidenceUsesOnlyVerifiedFactsAndMarksUnratedMeasures() {
        SpeechFeedbackServiceImpl.Evidence evidence = SpeechFeedbackServiceImpl.evidence(sentence());
        assertNull(evidence.notEvaluable());
        String text = evidence.text();
        assertTrue(text.contains("\"라디오\"") && text.contains("\"타디오\""));
        assertTrue(text.contains("음성 인식 결과이며 발음 판정이 아님"));
        assertTrue(text.contains("자동 후보(확정 아님): '라'의 첫소리 ㄹ → ㅌ"));
        assertTrue(text.contains("발음 정확도: 미평가"));
        assertTrue(text.contains("말하기 속도·유창성: 평가 지표 없음"));
        assertFalse(text.contains("66"), "텍스트 일치율 숫자를 발음 점수처럼 보내지 않는다");
        assertFalse(text.contains("77") || text.contains("a-1"), "학생·분석 ID를 보내지 않는다");
        assertEquals(List.of("AUTO_ANALYSIS"), evidence.sources());
    }

    @Test
    void notEvaluableCasesNeverCallTheAi() {
        Map<String,Object> held = sentence(); held.put("assessmentStatus", "HOLD");
        Map<String,Object> failed = sentence(); failed.put("status", "FAILED");
        Map<String,Object> external = sentence(); external.put("evaluationMode", "EXTERNAL_PROVIDER");
        Map<String,Object> silent = sentence(); silent.put("transcript", " ");
        for (Map<String,Object> analysis : List.of(held, failed, external, silent, therapy("PENDING"))) {
            Map<String,Object> response = service.generate(1, analysis);
            assertEquals("NOT_EVALUABLE", response.get("status"), analysis.toString());
            assertFalse(String.valueOf(response.get("reason")).isBlank());
        }
        assertEquals("선생님이 녹음을 확인한 뒤에 설명을 볼 수 있어요.", service.generate(1, therapy("PENDING")).get("reason"));
        verifyNoInteractions(ai);
        verify(mapper, never()).upsertFeedback(any(), any(), any(), any(), any());
    }

    @Test
    void therapyFeedbackFollowsTheTeachersConfirmedResultAndKeepsTheNotePrivate() {
        SpeechFeedbackServiceImpl.Evidence evidence = SpeechFeedbackServiceImpl.evidence(therapy("REVIEWED"));
        String text = evidence.text();
        assertTrue(text.contains("선생님 확인 결과(그대로 따를 것): 더 연습이 필요함"));
        assertTrue(text.contains("어두 초성의 ㄹ 소리가 소리가 정확하지 않음") || text.contains("어두 초성의 ㄹ 소리가 정확하지 않음"));
        assertFalse(text.contains("비공개 메모"), "교사 메모는 외부 AI로 보내지 않는다");
        assertFalse(text.contains("자동 후보"), "교사가 확정했으면 자동 후보는 근거로 쓰지 않는다");
        assertEquals(List.of("TEACHER_CONFIRMED"), evidence.sources());
        Map<String,Object> revised = therapy("REVIEWED");
        revised.put("teacherJudgement", "ACCEPTABLE");
        assertNotEquals(evidence.hash(), SpeechFeedbackServiceImpl.evidence(revised).hash(), "교사가 다시 판정하면 이전 설명은 쓰지 않는다");
    }

    @Test
    void generationRequiresConsentStoresTheTextSeparatelyAndReusesItForTheSameEvidence() {
        doThrow(new ConsentRequiredException("AI_CHAT", "AI 외부 전송 동의가 필요해요.")).when(consents).requireConsent(1, "AI_CHAT");
        assertThrows(ConsentRequiredException.class, () -> service.generate(1, sentence()));
        verifyNoInteractions(ai);

        reset(consents);
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn("**라디오**를 끝까지 또박또박 말했어요! 다음에는 '라' 소리를 천천히 연습해 봐요.");
        when(ai.modelName()).thenReturn("gemini-flash-latest");
        String hash = SpeechFeedbackServiceImpl.evidence(sentence()).hash();
        when(mapper.findFeedback("a-1")).thenReturn(null,
                Map.of("text", "라디오를 끝까지 또박또박 말했어요! 다음에는 '라' 소리를 천천히 연습해 봐요.", "evidenceHash", hash, "modelName", "gemini-flash-latest", "promptVersion", "feedback-v1", "generatedAt", "2026-10-03T09:00:00"));
        Map<String,Object> first = service.generate(1, sentence());
        assertEquals("READY", first.get("status"));
        assertEquals("AI", first.get("source"));
        assertFalse(first.containsKey("pronunciationScore"), "AI 응답에 점수 필드를 만들지 않는다");
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertFeedback(eq("a-1"), stored.capture(), eq(hash), eq("gemini-flash-latest"), eq("feedback-v1"));
        assertFalse(stored.getValue().contains("*"), "마크다운 기호를 지운다");

        service.generate(1, sentence()); // 같은 근거: 저장된 설명을 돌려준다
        verify(ai, times(1)).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void responsesWithScoresDiagnosesOrExcessLengthAreRejected() {
        for (String bad : List.of("발음이 85점이에요.", "정확도 90%예요.", "발음 점수가 좋아요.", "언어 장애가 의심돼요.", "", "가".repeat(401))) {
            AiProviderException error = assertThrows(AiProviderException.class, () -> SpeechFeedbackServiceImpl.sanitize(bad), bad);
            assertEquals("AI_BAD_RESPONSE", error.getCode());
        }
        assertEquals("잘했어요! 다시 해 봐요.", SpeechFeedbackServiceImpl.sanitize("  **잘했어요!**\n 다시 해 봐요. "));
    }

    @Test
    void readingTheStateNeverCallsTheAiAndIgnoresAnOutdatedExplanation() {
        when(ai.isConfigured()).thenReturn(true);
        doThrow(new ConsentRequiredException("AI_CHAT", "동의 필요")).when(consents).requireConsent(1, "AI_CHAT");
        when(mapper.findFeedback("a-1")).thenReturn(Map.of("text", "예전 설명", "evidenceHash", "other-hash"));
        Map<String,Object> state = service.feedback(1, sentence());
        assertEquals("NOT_GENERATED", state.get("status"));
        assertEquals(true, state.get("consentRequired"));
        assertEquals(true, state.get("available"));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());
    }
}
