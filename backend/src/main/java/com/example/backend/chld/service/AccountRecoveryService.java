package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.FindIdRequest;
import com.example.backend.chld.dto.request.PasswordResetConfirmRequest;
import com.example.backend.chld.dto.request.PasswordResetRequest;
import com.example.backend.chld.dto.request.PasswordResetVerifyRequest;
import com.example.backend.chld.dto.response.FindIdResponse;
import com.example.backend.chld.dto.response.PasswordResetTokenResponse;

/** 아이디(이메일) 찾기와 이메일 인증 코드 기반 비밀번호 재설정 */
public interface AccountRecoveryService {
    FindIdResponse findId(FindIdRequest request);

    /** 가입 여부를 드러내지 않도록 항상 같은 응답을 준다. */
    void requestPasswordReset(PasswordResetRequest request);

    PasswordResetTokenResponse verifyResetCode(PasswordResetVerifyRequest request);

    void confirmPasswordReset(PasswordResetConfirmRequest request);
}
