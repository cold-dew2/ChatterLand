package com.example.backend.chld.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ExternalProviderServiceTest {
    @Test
    void doesNotPretendSpeechProviderIsAvailableWhenUnconfigured() {
        ExternalProviderService service = new ExternalProviderService(mock(RestClient.Builder.class), "", "", "model", "", "");
        ResponseStatusException error = assertThrows(ResponseStatusException.class, service::requireSpeechProvider);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void doesNotPretendAiProviderIsAvailableWhenUnconfigured() {
        ExternalProviderService service = new ExternalProviderService(mock(RestClient.Builder.class), "", "", "model", "", "");
        ResponseStatusException error = assertThrows(ResponseStatusException.class, service::requireAiProvider);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }
}
