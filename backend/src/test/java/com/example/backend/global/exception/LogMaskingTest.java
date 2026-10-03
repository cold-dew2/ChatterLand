package com.example.backend.global.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogMaskingTest {
    @Test
    void masksEmailsTokensSecretsAndPhones() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiJ9.c2lnbmF0dXJl";
        String masked = LogMasking.mask("Duplicate entry 'parent@example.com' for key 'uq_users_email'; Authorization: Bearer " + jwt
                + " {\"password\":\"Chatterland!234\",\"refreshToken\":\"abc123\"} code=482913 phone 010-1234-5678 token=" + jwt);
        assertFalse(masked.contains("parent@example.com"), masked);
        assertFalse(masked.contains("Chatterland!234"), masked);
        assertFalse(masked.contains("abc123"), masked);
        assertFalse(masked.contains("482913"), masked);
        assertFalse(masked.contains("010-1234-5678"), masked);
        assertFalse(masked.contains(jwt), masked);
        assertTrue(masked.contains("[EMAIL]") && masked.contains("[MASKED]") && masked.contains("[PHONE]"), masked);
        assertTrue(masked.contains("uq_users_email"), "원인 파악에 필요한 정보(제약 이름)는 남긴다");
    }

    @Test
    void leavesOrdinaryMessagesAndNullsAlone() {
        assertEquals("음성 분석 결과를 저장하지 못했습니다.", LogMasking.mask("음성 분석 결과를 저장하지 못했습니다."));
        assertNull(LogMasking.mask(null));
    }

    @Test
    void describeMasksTheWholeCauseChainAndKeepsAppFrames() {
        Exception error = new IllegalStateException("save failed for kid@example.test",
                new RuntimeException("password=Secret1234 Bearer abc.def.ghi"));
        String described = LogMasking.describe(error);
        assertTrue(described.startsWith("java.lang.IllegalStateException: save failed for [EMAIL]"), described);
        assertTrue(described.contains("caused by java.lang.RuntimeException"), described);
        assertFalse(described.contains("kid@example.test") || described.contains("Secret1234") || described.contains("abc.def.ghi"), described);
        assertTrue(described.contains("at com.example.backend."), "앱 코드 위치는 남긴다: " + described);
    }
}
