package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 외부 AI(설정된 AI_API_URL·AI_API_KEY) 호출 검증. LIVE_AI_TEST=1 일 때만 실행된다(build.gradle).
 * 키는 출력하지 않고, 생성된 문장(합성 테스트 데이터 기반)과 오류 코드만 출력한다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class LiveAiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiTextClient ai;
    @Autowired ExternalProviderService providers;
    @Value("${app.ai.endpoint:}") String url;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void requireLiveConfiguration() {
        assumeTrue("1".equals(System.getenv("LIVE_AI_TEST")), "LIVE_AI_TEST=1 일 때만 실행");
        assertTrue(ai.isConfigured(), "AI_API_URL·AI_API_KEY 설정이 필요합니다.");
        System.out.println("[live-ai] provider=" + (ai.isGemini() ? "gemini-native" : "openai-compatible") + " model=" + ai.modelName());
    }

    @Test
    void aiChatGetsARealReply() {
        long started = System.nanoTime();
        String reply = providers.generateReply("오늘의 기분", List.of(Map.of("speaker", "STUDENT", "content", "오늘 학교에서 친구랑 놀아서 기분이 좋아요.")));
        System.out.println("[live-ai] chat reply (" + Duration.ofNanos(System.nanoTime() - started).toMillis() + "ms): " + reply);
        assertFalse(reply.isBlank());
        assertTrue(reply.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3), "한국어로 답해야 한다");
    }

    @Test
    void learningFeedbackIsGeneratedThroughTheApiAndFollowsTheRules() throws Exception {
        String email = "live-ai-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STUDENT\",\"name\":\"실연동 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                        + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        String token = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        String analysisId = UUID.randomUUID().toString();
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "comparison_json,pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,1,?,'audio/wav','COMPLETED','SENTENCE_MATCH','라디오','타디오',66.67,?,'NOT_EVALUATED','NOT_REQUIRED','WORD','ERROR_CANDIDATES',?,'jamo-align-v1',NOW())",
                analysisId, studentId, String.valueOf(itemId), "[{\"type\":\"SUBSTITUTED\",\"expected\":\"라디오\",\"recognized\":\"타디오\"}]",
                "{\"holdReasons\":[],\"candidates\":[{\"type\":\"SUBSTITUTION\",\"slot\":\"ONSET\",\"expected\":\"ㄹ\",\"produced\":\"ㅌ\",\"targetSyllable\":\"라\"}]}");

        long started = System.nanoTime();
        String body = mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        JsonNode feedback = objectMapper.readTree(body);
        System.out.println("[live-ai] feedback (" + Duration.ofNanos(System.nanoTime() - started).toMillis() + "ms): " + feedback.path("status").asText()
                + " / " + feedback.path("code").asText("") + " / " + feedback.path("text").asText(feedback.path("message").asText()));
        assertEquals("READY", feedback.path("status").asText(), body);
        assertEquals(ai.modelName(), feedback.path("modelName").asText());
        String text = feedback.path("text").asText();
        assertFalse(text.matches("(?s).*\\d+\\s*(점|%).*"), "점수를 만들지 않는다");
    }

    @Test
    void aRejectedKeyAndATimeoutAreReportedWithSafeCodes() {
        AiTextClient wrongKey = new AiTextClient(RestClient.builder(), url, "invalid-key-for-live-test", "unused", Duration.ofSeconds(20));
        AiProviderException auth = assertThrows(AiProviderException.class, () -> wrongKey.generate("짧게 답해.", List.of(new AiTextClient.Turn("user", "안녕")), 0.2, 64));
        System.out.println("[live-ai] wrong key -> " + auth.getCode() + " " + auth.getStatus().value());
        assertEquals("AI_AUTH_FAILED", auth.getCode());
        assertFalse(auth.getMessage().contains("invalid-key-for-live-test"));

        AiTextClient slow = new AiTextClient(RestClient.builder(), url, "invalid-key-for-live-test", "unused", Duration.ofMillis(1));
        AiProviderException timeout = assertThrows(AiProviderException.class, () -> slow.generate("짧게 답해.", List.of(new AiTextClient.Turn("user", "안녕")), 0.2, 64));
        System.out.println("[live-ai] 1ms timeout -> " + timeout.getCode());
        assertEquals("AI_TIMEOUT", timeout.getCode());
    }
}
