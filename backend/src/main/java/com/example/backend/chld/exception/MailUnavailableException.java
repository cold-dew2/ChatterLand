package com.example.backend.chld.exception;

/**
 * 메일을 보낼 수 없을 때 (503). code로 원인을 구분한다.
 * - MAIL_NOT_CONFIGURED: MAIL_HOST/MAIL_FROM 설정 누락
 * - MAIL_DELIVERY_FAILED: SMTP 서버 연결·발송 실패
 */
public class MailUnavailableException extends RuntimeException {
    public static final String NOT_CONFIGURED = "MAIL_NOT_CONFIGURED";
    public static final String DELIVERY_FAILED = "MAIL_DELIVERY_FAILED";
    private final String code;

    public MailUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
