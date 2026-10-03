package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalProviderServiceTest {
    @Test
    void doesNotPretendSpeechProviderIsAvailableWhenUnconfigured() {
        ExternalProviderService service = new ExternalProviderService(mock(RestClient.Builder.class), new AiTextClient(mock(RestClient.class), "", "", "model"), "", "");
        ResponseStatusException error = assertThrows(ResponseStatusException.class, service::requireSpeechProvider);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void doesNotPretendAiProviderIsAvailableWhenUnconfigured() {
        ExternalProviderService service = new ExternalProviderService(mock(RestClient.Builder.class), new AiTextClient(mock(RestClient.class), "", "", "model"), "", "");
        AiProviderException error = assertThrows(AiProviderException.class, service::requireAiProvider);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("AI_NOT_CONFIGURED", error.getCode());
    }

    @Test
    void sendsOpenAiCompatibleChatRequestWithConfiguredUrlKeyAndModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("안녕"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"반가워!\"}}]}", MediaType.APPLICATION_JSON));
        ExternalProviderService service = new ExternalProviderService(builder, new AiTextClient(builder.build(), "http://ai.test/v1/chat/completions", "test-key", "test-model"), "", "");

        String reply = service.generateReply("오늘의 기분", List.of(Map.of("speaker", "STUDENT", "content", "안녕")));

        assertEquals("반가워!", reply);
        server.verify();
    }

    /** 제공자가 키를 거부하면(401) 원인을 구분할 수 있는 코드(AI_AUTH_FAILED, 503)로 알린다. 키 값은 메시지에 넣지 않는다. */
    @Test
    void reportsRejectedKeyWithASafeSpecificCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai.test/v1/chat/completions")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        ExternalProviderService service = new ExternalProviderService(builder, new AiTextClient(builder.build(), "http://ai.test/v1/chat/completions", "wrong-key", "test-model"), "", "");

        AiProviderException error = assertThrows(AiProviderException.class,
                () -> service.generateReply("오늘의 기분", List.of(Map.of("speaker", "STUDENT", "content", "안녕"))));
        assertEquals("AI_AUTH_FAILED", error.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertFalse(error.getMessage().contains("wrong-key"));
    }
}
