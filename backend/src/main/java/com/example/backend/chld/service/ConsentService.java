package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.ConsentUpdateRequest;
import com.example.backend.chld.dto.request.SignupConsentRequest;
import com.example.backend.chld.dto.response.ConsentResponse;
import com.example.backend.global.jwt.TokenPrincipal;

import java.util.List;

public interface ConsentService {
    /** 회원가입 요청의 동의 항목을 검증한다. 필수 동의가 없으면 400 예외. (계정 생성 전에 호출) */
    void validateSignupConsents(String role, Integer age, SignupConsentRequest consents);

    void recordSignupConsents(long userId, String role, Integer age, SignupConsentRequest consents);

    List<ConsentResponse> myConsents(TokenPrincipal principal);

    ConsentResponse updateConsent(TokenPrincipal principal, String type, ConsentUpdateRequest request);

    /** 동의가 없으면 ConsentRequiredException(403 CONSENT_REQUIRED)을 던진다. */
    void requireConsent(long userId, String type);
}
