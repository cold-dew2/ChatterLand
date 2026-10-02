package com.example.backend.chld.exception;

/** 동의가 필요한 기능을 동의 없이 호출했을 때 (403 CONSENT_REQUIRED) */
public class ConsentRequiredException extends RuntimeException {
    private final String consentType;

    public ConsentRequiredException(String consentType, String message) {
        super(message);
        this.consentType = consentType;
    }

    public String getConsentType() { return consentType; }
}
