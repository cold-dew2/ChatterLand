package com.example.backend;

import com.example.backend.chld.exception.AiProviderException;
import com.example.backend.chld.service.impl.AiTextClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI 학습 피드백 API(외부 AI는 mock — 실제 호출은 LiveAiIntegrationTest).
 * 본인 분석만, AI 외부 전송 동의 필요, 피드백은 별도 테이블에 저장하고 분석 결과·점수는 바꾸지 않으며, AI가 실패해도 분석 조회는 그대로다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class SpeechFeedbackIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiTextClient ai;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aStudentGetsAStoredAiExplanationForTheirOwnAnalysisWithoutChangingTheResult() throws Exception {
        Student owner = signupStudent(true);
        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        when(ai.isConfigured()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-flash-latest");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn("라디오를 끝까지 말했어요! 다음에는 첫소리를 천천히 말해 봐요.");

        mvc.perform(get("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_GENERATED"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.consentRequired").value(false));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());

        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY")).andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.text").value("라디오를 끝까지 말했어요! 다음에는 첫소리를 천천히 말해 봐요."))
                .andExpect(jsonPath("$.modelName").value("gemini-flash-latest")).andExpect(jsonPath("$.basedOn[0]").value("AUTO_ANALYSIS"));
        mvc.perform(get("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.status").value("READY"));
        // 분석 결과(일치율·점수·자동 판정)는 그대로이고, 분석 응답에 AI 설명이 섞이지 않는다.
        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.textMatchRate").value(66.67)).andExpect(jsonPath("$.pronunciationScore").doesNotExist())
                .andExpect(jsonPath("$.assessmentStatus").value("ERROR_CANDIDATES")).andExpect(jsonPath("$.aiFeedback").doesNotExist());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM speech_ai_feedback WHERE analysis_id=?", Integer.class, analysisId));
        verify(ai, times(1)).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void otherStudentsTeachersAndUnconsentedStudentsCannotGenerate() throws Exception {
        Student owner = signupStudent(true);
        Student other = signupStudent(true);
        Student noConsent = signupStudent(false);
        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        String unconsentedAnalysis = insertSentenceAnalysis(noConsent.studentId, "ERROR_CANDIDATES");
        when(ai.isConfigured()).thenReturn(true);

        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/speech/analyses/{id}/feedback", unconsentedAnalysis).header("Authorization", noConsent.bearer()))
                .andExpect(jsonPath("$.consentRequired").value(true));
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", unconsentedAnalysis).header("Authorization", noConsent.bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void heldRecordingsAreNotEvaluableAndAiFailuresDoNotBreakTheAnalysis() throws Exception {
        Student owner = signupStudent(true);
        String held = insertSentenceAnalysis(owner.studentId, "HOLD");
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", held).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_EVALUABLE"))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("다시 녹음")));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());

        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt()))
                .thenThrow(new AiProviderException("AI_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, "AI 서비스 응답 시간이 초과되었어요."))
                .thenThrow(new AiProviderException("AI_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE, "설정 필요"))
                .thenReturn("발음 점수는 90점이에요.");
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("AI_TIMEOUT"));
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
        // 점수를 지어낸 응답은 저장하지 않는다.
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_BAD_RESPONSE"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM speech_ai_feedback WHERE analysis_id=?", Integer.class, analysisId));
        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private String insertSentenceAnalysis(long studentId, String assessmentStatus) {
        String id = UUID.randomUUID().toString();
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "comparison_json,pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,1,?,'audio/wav','COMPLETED','SENTENCE_MATCH','라디오','타디오',66.67,?,'NOT_EVALUATED','NOT_REQUIRED','WORD',?,?,'jamo-align-v1',NOW())",
                id, studentId, String.valueOf(itemId), "[{\"type\":\"SUBSTITUTED\",\"expected\":\"라디오\",\"recognized\":\"타디오\"}]", assessmentStatus,
                "{\"holdReasons\":[],\"candidates\":[{\"type\":\"SUBSTITUTION\",\"slot\":\"ONSET\",\"expected\":\"ㄹ\",\"produced\":\"ㅌ\",\"targetSyllable\":\"라\"}]}");
        return id;
    }

    private record Student(long studentId, String token) { String bearer() { return "Bearer " + token; } }

    private Student signupStudent(boolean aiConsent) throws Exception {
        String email = "feedback-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"피드백 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":" + aiConsent + ",\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        String token = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        return new Student(studentId, token);
    }
}
