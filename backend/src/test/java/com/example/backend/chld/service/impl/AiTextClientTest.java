package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** AI 호출: Gemini 네이티브 형식, 오류·시간 초과·사용량 제한·차단 처리, 키가 URL·메시지·로그에 남지 않음. */
@ExtendWith(OutputCaptureExtension.class)
class AiTextClientTest {
    private static final String GEMINI = "https://ai.test/v1beta/models/gemini-flash-latest:generateContent";
    private static final String KEY = "secret-test-key-123";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AiTextClient client = new AiTextClient(builder.build(), GEMINI, KEY, "gpt-4o-mini", new long[0]);

    private String generate() { return client.generate("시스템 지시", List.of(new AiTextClient.Turn("user", "안녕"), new AiTextClient.Turn("assistant", "반가워"), new AiTextClient.Turn("user", "또 만나")), 0.3, 512); }

    @Test
    void callsGeminiWithTheConfiguredUrlAndModelAndKeyInAHeader() {
        server.expect(requestTo(GEMINI)) // URL 그대로(쿼리에 키 없음)
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", KEY))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("시스템 지시"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[2].parts[0].text").value("또 만나"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(512))
                .andExpect(jsonPath("$.model").doesNotExist())
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"생각 중\",\"thought\":true},{\"text\":\" 잘했어요! \"}]},\"finishReason\":\"STOP\"}]}", MediaType.APPLICATION_JSON));
        assertEquals("잘했어요!", generate());
        assertEquals("gemini-flash-latest", client.modelName(), "모델 이름은 URL에서 읽는다(AI_MODEL 기본값을 쓰지 않음)");
        server.verify();
    }

    @Test
    void mapsProviderErrorsToSafeCodes(CapturedOutput output) {
        assertCode(HttpStatus.UNAUTHORIZED, "AI_AUTH_FAILED", HttpStatus.SERVICE_UNAVAILABLE);
        assertCode(HttpStatus.FORBIDDEN, "AI_AUTH_FAILED", HttpStatus.SERVICE_UNAVAILABLE);
        assertCode(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS);
        assertCode(HttpStatus.BAD_REQUEST, "AI_REQUEST_REJECTED", HttpStatus.BAD_GATEWAY);
        assertCode(HttpStatus.NOT_FOUND, "AI_REQUEST_REJECTED", HttpStatus.BAD_GATEWAY);
        assertCode(HttpStatus.INTERNAL_SERVER_ERROR, "AI_PROVIDER_ERROR", HttpStatus.BAD_GATEWAY);
        assertFalse(output.getOut().contains(KEY), "키를 로그에 남기지 않는다");
        assertFalse(output.getOut().contains("provider-detail-with-prompt"), "제공자 응답 본문(요청 내용 포함 가능)을 로그에 남기지 않는다");
    }

    @Test
    void temporaryProviderErrorsAreRetriedButQuotaAndAuthErrorsAreNot() {
        AiTextClient retrying = new AiTextClient(builder.build(), GEMINI, KEY, "m", new long[]{0, 0});
        String ok = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"좋아요\"}]}}]}";
        server.reset();
        server.expect(requestTo(GEMINI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(GEMINI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(GEMINI)).andRespond(withSuccess(ok, MediaType.APPLICATION_JSON));
        assertEquals("좋아요", retrying.generate("s", List.of(new AiTextClient.Turn("user", "안녕")), 0.3, 64));
        server.verify();

        server.reset();
        for (int i = 0; i < 3; i++) server.expect(requestTo(GEMINI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals("AI_PROVIDER_ERROR", assertThrows(AiProviderException.class, () -> retrying.generate("s", List.of(new AiTextClient.Turn("user", "안녕")), 0.3, 64)).getCode());
        server.verify(); // 처음 1회 + 재시도 2회

        for (HttpStatus noRetry : List.of(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.UNAUTHORIZED, HttpStatus.BAD_REQUEST)) {
            server.reset();
            server.expect(org.springframework.test.web.client.ExpectedCount.once(), requestTo(GEMINI)).andRespond(withStatus(noRetry));
            assertThrows(AiProviderException.class, () -> retrying.generate("s", List.of(new AiTextClient.Turn("user", "안녕")), 0.3, 64));
            server.verify();
        }
    }

    @Test
    void aUsedUpDailyQuotaSaysTomorrowNotInAMoment() {
        server.expect(requestTo(GEMINI)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}"));
        AiProviderException error = assertThrows(AiProviderException.class, this::generate);
        assertEquals("AI_RATE_LIMITED", error.getCode());
        assertTrue(error.getMessage().contains("내일 다시"));
    }

    @Test
    void geminisInvalidKeyResponseIsReportedAsAKeyProblem() {
        server.expect(requestTo(GEMINI)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}"));
        assertEquals("AI_AUTH_FAILED", assertThrows(AiProviderException.class, this::generate).getCode());
    }

    @Test
    void timeoutBlockedAndEmptyResponsesAreDistinguished() {
        server.expect(requestTo(GEMINI)).andRespond(request -> { throw new SocketTimeoutException("read timed out"); });
        assertEquals("AI_TIMEOUT", assertThrows(AiProviderException.class, this::generate).getCode());
        server.reset();
        server.expect(requestTo(GEMINI)).andRespond(withSuccess("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", MediaType.APPLICATION_JSON));
        assertEquals("AI_BLOCKED", assertThrows(AiProviderException.class, this::generate).getCode());
        server.reset();
        server.expect(requestTo(GEMINI)).andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"SAFETY\"}]}", MediaType.APPLICATION_JSON));
        assertEquals("AI_BLOCKED", assertThrows(AiProviderException.class, this::generate).getCode());
        server.reset();
        server.expect(requestTo(GEMINI)).andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"  \"}]},\"finishReason\":\"MAX_TOKENS\"}]}", MediaType.APPLICATION_JSON));
        assertEquals("AI_BAD_RESPONSE", assertThrows(AiProviderException.class, this::generate).getCode());
    }

    @Test
    void aMissingUrlOrKeyIsReportedWithoutCallingTheProvider() {
        for (AiTextClient unconfigured : List.of(new AiTextClient(builder.build(), "", KEY, "m"), new AiTextClient(builder.build(), GEMINI, " ", "m"))) {
            assertFalse(unconfigured.isConfigured());
            AiProviderException error = assertThrows(AiProviderException.class, () -> unconfigured.generate("s", List.of(), 0.3, 10));
            assertEquals("AI_NOT_CONFIGURED", error.getCode());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        }
        server.verify(); // 호출 없음
    }

    private void assertCode(HttpStatus provider, String code, HttpStatus ours) {
        server.reset();
        server.expect(requestTo(GEMINI)).andRespond(withStatus(provider).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"provider-detail-with-prompt\"}}"));
        AiProviderException error = assertThrows(AiProviderException.class, this::generate);
        assertEquals(code, error.getCode(), provider.toString());
        assertEquals(ours, error.getStatus());
        assertFalse(error.getMessage().contains(KEY));
        assertFalse(error.getMessage().contains("provider-detail-with-prompt"));
    }
}
