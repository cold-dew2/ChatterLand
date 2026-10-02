package com.example.backend.chld.service;

public interface MailService {
    /** 메일 발송 설정이 없으면 503 예외를 던진다. */
    void requireAvailable();

    void sendPasswordResetCode(String to, String code, int expiresMinutes);
}
