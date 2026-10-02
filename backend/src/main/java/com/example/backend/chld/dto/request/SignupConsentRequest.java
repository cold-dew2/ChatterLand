package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.Size;

/** 회원가입 시 동의 항목. 만 14세 미만 학생은 법정대리인 확인 정보가 필요하다. */
public record SignupConsentRequest(
        Boolean privacy,
        Boolean voice,
        Boolean aiChat,
        String policyVersion,
        Boolean guardianConfirmed,
        @Size(max = 80) String guardianName,
        @Size(max = 20) String guardianRelation
) { }
