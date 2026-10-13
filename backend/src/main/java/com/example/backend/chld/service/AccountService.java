package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.NotificationSettingsRequest;
import com.example.backend.chld.dto.request.ProfileUpdateRequest;
import com.example.backend.chld.dto.response.NotificationSettingsResponse;
import com.example.backend.chld.dto.response.UserResponse;
import com.example.backend.global.jwt.TokenPrincipal;

/** 로그인한 사용자의 계정 설정(내 정보 수정·알림 설정). 비밀번호 변경은 기존 AuthService를 쓴다. */
public interface AccountService {
    UserResponse updateProfile(TokenPrincipal principal, ProfileUpdateRequest request);
    NotificationSettingsResponse notificationSettings(TokenPrincipal principal);
    NotificationSettingsResponse updateNotificationSettings(TokenPrincipal principal, NotificationSettingsRequest request);
}
