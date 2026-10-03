package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.LoginRequest;
import com.example.backend.chld.dto.request.PasswordChangeRequest;
import com.example.backend.chld.dto.request.RefreshRequest;
import com.example.backend.chld.dto.request.SignupRequest;
import com.example.backend.chld.dto.response.AuthResponse;
import com.example.backend.chld.dto.response.TokenResponse;
import com.example.backend.chld.dto.response.UserResponse;
import com.example.backend.global.jwt.TokenPrincipal;

public interface AuthService {
    UserResponse signup(SignupRequest request);
    AuthResponse login(LoginRequest request);
    TokenResponse refresh(RefreshRequest request);
    UserResponse currentUser(long userId);
    /**
     * access token이 아직 유효한지(서명·만료 외 서버 상태): 활성 계정이고, 토큰 버전이 현재 값과 같고,
     * 세션 ID가 있으면 그 세션에 폐기되지 않은 refresh token이 남아 있어야 한다. DB 오류는 DataAccessException으로 전달한다.
     */
    boolean isAccessTokenActive(long userId, int tokenVersion, String sessionId);
    /** 로그아웃: refresh token이 속한 로그인 세션과 access token의 세션(principalSessionId)을 폐기한다. */
    void logout(String refreshToken, String principalSessionId);
    /** 로그인 상태 비밀번호 변경. 다른 기기의 세션은 모두 끊고, 이 기기에는 새 토큰을 발급한다. */
    TokenResponse changePassword(TokenPrincipal principal, PasswordChangeRequest request);
    /** 만료·폐기된 오래된 refresh token과 지난 비밀번호 변경 실패 기록을 지운다. 지운 행 수를 돌려준다. */
    int purgeStaleAuthRecords();
}
