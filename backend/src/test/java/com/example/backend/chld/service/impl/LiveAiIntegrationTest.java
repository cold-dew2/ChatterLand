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
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
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

    /**
     * RAG 학습 피드백 실측: 표준 발음법 조항이 적용되는 문장 10개(합성 분석 결과)로 실제 Gemini를 호출한다.
     * - 각 단계 시간(검색·임베딩·생성·전체)의 평균·중앙값·p95, 근거 검증 통과율을 출력한다.
     * - 응답 원문은 교사 검수용으로 build/live-ai/feedback-samples.json에 저장한다(품질 판정은 사람이 한다).
     * 검수 전(DRAFT) 원문 자료를 이 테스트 안에서만 승인 상태로 바꾼다(트랜잭션 롤백).
     */
    @Test
    void ragFeedbackLatencyAndGroundingOnTenSentences(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        // 출처·권한이 확인된(VERIFIED) 원문 조항 자료를 이 테스트 안에서만 승인한다(개발자 요약 자료는 PENDING이라 쓰지 않는다).
        jdbc.update("UPDATE knowledge_documents SET review_status='APPROVED', reviewed_at=NOW() WHERE document_id IN ('nikl-pron-assimilation','nikl-pron-coda')");
        String token = consentedStudentToken();
        long studentId = jdbc.queryForObject("SELECT sp.student_id FROM student_profiles sp JOIN users u ON u.user_id=sp.user_id WHERE u.email=?", Long.class, lastEmail);
        String[][] cases = {{"신라","실라","ㄴ","ㄹ","신"},{"국물","국물","",""," "},{"국밥","국밥","","",""},{"같이","가치","ㅌ","ㅊ","같"},{"강릉","강릉","","",""},
                {"옷을 입어요","오슬 이버요","","",""},{"부엌","부억","ㅋ","ㄱ","엌"},{"난로","날로","ㄴ","ㄹ","난"},{"칼날","칼랄","ㄴ","ㄹ","날"},{"밥물","밤물","ㅂ","ㅁ","밥"}};
        List<Map<String,Object>> samples = new java.util.ArrayList<>();
        int ready = 0;
        for (String[] c : cases) {
            String analysisId = insertAnalysis(studentId, c[0], c[1], c[2], c[3], c[4]);
            String body = mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            JsonNode feedback = objectMapper.readTree(body);
            if ("READY".equals(feedback.path("status").asText())) ready++;
            samples.add(Map.of("target", c[0], "recognized", c[1], "status", feedback.path("status").asText(feedback.path("code").asText()),
                    "text", feedback.path("text").asText(feedback.path("message").asText("")), "sources", feedback.path("sources").toString()));
            System.out.println("[live-ai] " + c[0] + " -> " + feedback.path("status").asText(feedback.path("code").asText()) + " / " + feedback.path("text").asText(feedback.path("message").asText()));
        }
        java.nio.file.Path out = java.nio.file.Path.of("build/live-ai/feedback-samples.json");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(samples));
        // 처리 시간(ai.timing 로그) 요약
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("ai\\.timing stage=feedback outcome=(\\S+) retrievalMs=(\\d+) embeddingMs=(\\d+) generationMs=(\\d+) totalMs=(\\d+)").matcher(output.getOut());
        List<long[]> timings = new java.util.ArrayList<>();
        while (m.find()) timings.add(new long[]{Long.parseLong(m.group(2)), Long.parseLong(m.group(3)), Long.parseLong(m.group(4)), Long.parseLong(m.group(5))});
        String[] names = {"retrieval", "embedding", "generation", "total"};
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            long[] values = timings.stream().mapToLong(t -> t[k]).sorted().toArray();
            if (values.length == 0) continue;
            System.out.printf("[live-ai] %s n=%d mean=%.0fms median=%dms p95=%dms%n", names[i], values.length,
                    java.util.Arrays.stream(values).average().orElse(0), values[values.length / 2], values[Math.max(0, (int) Math.ceil(values.length * 0.95) - 1)]);
        }
        System.out.println("[live-ai] READY(근거 검증 통과) " + ready + "/" + cases.length + " · 표본은 " + out);
        assertTrue(ready > 0, "실제 응답이 하나도 근거 검증을 통과하지 못함");

        // 관련 자료가 없는 결과(받침 없는 낱말)는 외부 호출 없이 근거 부족
        String none = insertAnalysis(studentId, "라디오", "타디오", "ㄹ", "ㅌ", "라");
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", none).header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("INSUFFICIENT_SOURCES"));
    }

    private String lastEmail;

    private String consentedStudentToken() throws Exception {
        String email = "live-ai-" + UUID.randomUUID() + "@example.test";
        lastEmail = email;
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STUDENT\",\"name\":\"실연동 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                        + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        String token = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/consents/me/AI_FEEDBACK").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"agreed\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}"))
                .andExpect(status().isOk());
        return token;
    }

    private String insertAnalysis(long studentId, String target, String recognized, String expected, String produced, String syllable) {
        String id = UUID.randomUUID().toString();
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        String candidates = expected.isBlank() ? "[]" : "[{\"type\":\"SUBSTITUTION\",\"slot\":\"CODA\",\"expected\":\"" + expected + "\",\"produced\":\"" + produced + "\",\"targetSyllable\":\"" + syllable + "\"}]";
        String words = target.equals(recognized) ? "[{\"type\":\"MATCH\",\"expected\":\"" + target + "\",\"recognized\":\"" + recognized + "\"}]"
                : "[{\"type\":\"SUBSTITUTED\",\"expected\":\"" + target + "\",\"recognized\":\"" + recognized + "\"}]";
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "comparison_json,pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,1,?,'audio/wav','COMPLETED','SENTENCE_MATCH',?,?,NULL,?,'NOT_EVALUATED','NOT_REQUIRED','WORD',?,?,'jamo-align-v1',NOW())",
                id, studentId, String.valueOf(itemId), target, recognized, words, expected.isBlank() ? "NO_CANDIDATES" : "ERROR_CANDIDATES",
                "{\"holdReasons\":[],\"candidates\":" + candidates + "}");
        return id;
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
