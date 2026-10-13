package com.example.backend;

import com.example.backend.chld.exception.AiProviderException;
import com.example.backend.chld.service.impl.AiTextClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 대화 학습 흐름: 후속 질문 규칙이 담긴 요청, 대화 낱말과 연결된 실제 활성 콘텐츠, 실제 대화로 만든 종료 요약.
 * AI가 실패하면 요약·메시지·학습 기록을 저장하지 않고, 동의가 없거나 철회되면 막는다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class ConversationLearningIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiTextClient ai;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void configuredAi() {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.modelName()).thenReturn("test-model");
    }

    @Test
    void followUpRulesRelatedContentsAndSummaryComeFromTheRealConversation() throws Exception {
        Student student = signupStudent("하늘이", true);
        long attemptsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempts", Long.class);
        String id = start(student, "좋아하는 음식");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn("사과 맛있지! 사과는 어떤 맛이 나서 좋아?");
        JsonNode reply = json(say(student, id, "저는 사과를 좋아해요").andExpect(status().isOk()));
        assertEquals("사과 맛있지! 사과는 어떤 맛이 나서 좋아?", reply.path("response").asText());

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<AiTextClient.Turn>> turns = ArgumentCaptor.captor();
        verify(ai).generate(instruction.capture(), turns.capture(), anyDouble(), anyInt());
        assertTrue(instruction.getValue().contains("exactly ONE follow-up question"));
        assertTrue(instruction.getValue().contains("re-explain more simply"));
        assertTrue(instruction.getValue().contains("Never comment on, score or praise pronunciation"));
        assertFalse(instruction.getValue().contains("하늘이"), "학생 이름 등 개인정보는 외부 AI에 보내지 않는다");
        assertEquals("저는 사과를 좋아해요", turns.getValue().get(turns.getValue().size() - 1).text());

        // 대화 낱말 → 실제 활성 콘텐츠
        JsonNode contents = json(mvc.perform(get("/api/v1/ai/conversations/" + id + "/contents").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertFalse(contents.path("items").isEmpty());
        JsonNode first = contents.path("items").get(0);
        assertEquals("사과", first.path("keyword").asText());
        assertEquals("대화에서 나온 '사과' 낱말이 들어 있는 연습이에요", first.path("reason").asText());
        assertTrue(jdbc.queryForObject("SELECT active FROM exercises WHERE exercise_id=?", Boolean.class, first.path("exerciseId").asLong()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM exercises e WHERE e.exercise_id=? AND (e.title LIKE '%사과%' OR EXISTS "
                + "(SELECT 1 FROM exercise_items i WHERE i.exercise_id=e.exercise_id AND i.text_value LIKE '%사과%'))", Integer.class, first.path("exerciseId").asLong()));

        // 끝내기: 실제 대화를 AI에 보내 요약, 성공한 뒤에만 종료·저장
        reset(ai); configuredAi();
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn("오늘은 좋아하는 사과 이야기를 나눴어요. 이야기해 줘서 고마워요.");
        JsonNode summary = json(mvc.perform(post("/api/v1/ai/conversations/" + id + "/end").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals("CLOSED", summary.path("status").asText());
        assertEquals("오늘은 좋아하는 사과 이야기를 나눴어요. 이야기해 줘서 고마워요.", summary.path("summary").asText());
        assertEquals(1, summary.path("studentTurns").asLong());
        assertFalse(summary.path("relatedContents").isEmpty());
        assertEquals("PRACTICE", summary.path("nextActivities").get(0).path("type").asText());
        verify(ai).generate(argThat(text -> text.contains("Use ONLY what appears in the conversation") && text.contains("Do not evaluate, score or praise pronunciation")),
                argThat(list -> list.stream().anyMatch(turn -> turn.text().equals("저는 사과를 좋아해요"))), anyDouble(), anyInt());
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM conversations WHERE conversation_id=?", String.class, id));

        // 끝난 대화에는 더 말할 수 없고, 다시 끝내면 저장된 요약을 그대로(AI 재호출 없음)
        say(student, id, "또 할래요").andExpect(status().isConflict());
        JsonNode again = json(mvc.perform(post("/api/v1/ai/conversations/" + id + "/end").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals(summary.path("summary").asText(), again.path("summary").asText());
        verify(ai, times(1)).generate(anyString(), anyList(), anyDouble(), anyInt());
        // 대화 이력에 요약이 보인다
        JsonNode list = json(mvc.perform(get("/api/v1/ai/conversations").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals(summary.path("summary").asText(), list.path("content").get(0).path("summary").asText());
        assertEquals(attemptsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempts", Long.class), "대화는 연습 기록을 만들지 않는다");
    }

    @Test
    void aiFailureSavesNothingAndEmptyOrUnconsentedConversationsAreRefused() throws Exception {
        Student student = signupStudent("바다", true);
        String id = start(student, "동물 이야기");
        // 빈 대화는 AI를 부르지 않고 거절
        mvc.perform(post("/api/v1/ai/conversations/" + id + "/end").header("Authorization", bearer(student.token))).andExpect(status().isBadRequest());
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());

        // 응답 실패: 메시지를 저장하지 않는다(가짜 답 없음)
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt()))
                .thenThrow(new AiProviderException(AiProviderException.TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "AI 서비스 응답 시간이 초과되었어요."));
        say(student, id, "강아지가 좋아요").andExpect(status().isGatewayTimeout());
        assertEquals(0, messages(id));

        // 대화 후 요약 실패: 종료·요약을 저장하지 않고 다시 시도할 수 있다
        reset(ai); configuredAi();
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn("강아지 귀엽지! 강아지랑 무엇을 하고 놀고 싶어?");
        say(student, id, "강아지가 좋아요").andExpect(status().isOk());
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt()))
                .thenThrow(new AiProviderException(AiProviderException.TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "AI 서비스 응답 시간이 초과되었어요."));
        mvc.perform(post("/api/v1/ai/conversations/" + id + "/end").header("Authorization", bearer(student.token))).andExpect(status().isGatewayTimeout());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM conversations WHERE conversation_id=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT summary FROM conversations WHERE conversation_id=?", String.class, id));
        assertEquals(2, messages(id));

        // 동의 철회: 말하기·끝내기 모두 막는다
        mvc.perform(put("/api/v1/consents/me/AI_CHAT").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"agreed\":false,\"policyVersion\":\"2026-10-01\"}")).andExpect(status().isOk());
        say(student, id, "고양이도 좋아요").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ai/conversations/" + id + "/end").header("Authorization", bearer(student.token))).andExpect(status().isForbidden());
        assertEquals(2, messages(id));

        // 처음부터 동의하지 않은 학생은 대화를 시작할 수 없다
        Student noConsent = signupStudent("구름", false);
        mvc.perform(post("/api/v1/ai/conversations").header("Authorization", bearer(noConsent.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\"동물 이야기\"}")).andExpect(status().isForbidden());
    }

    private long messages(String conversationId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages WHERE conversation_id=?", Long.class, conversationId);
    }

    private String start(Student student, String topic) throws Exception {
        return json(mvc.perform(post("/api/v1/ai/conversations").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\"" + topic + "\"}")).andExpect(status().isOk())).path("conversationId").asText();
    }

    private ResultActions say(Student student, String id, String text) throws Exception {
        return mvc.perform(post("/api/v1/ai/conversations/" + id + "/messages").header("Authorization", bearer(student.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Student(long studentId, String token) { }

    private Student signupStudent(String name, boolean aiChat) throws Exception {
        String email = "conv-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":" + aiChat + ",\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        String token = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        return new Student(studentId, token);
    }
}
