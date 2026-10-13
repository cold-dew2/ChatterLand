package com.example.backend.chld.controller;

import com.example.backend.chld.dto.request.NotificationSettingsRequest;
import com.example.backend.chld.dto.request.ProfileUpdateRequest;
import com.example.backend.chld.dto.response.NotificationSettingsResponse;
import com.example.backend.chld.dto.response.UserResponse;
import com.example.backend.chld.service.AccountService;
import com.example.backend.global.jwt.TokenPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 로그인한 사용자 자신의 계정 설정 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @PatchMapping("/profile") public UserResponse updateProfile(@AuthenticationPrincipal TokenPrincipal principal, @Valid @RequestBody ProfileUpdateRequest request) {
        return accounts.updateProfile(principal, request);
    }
    @GetMapping("/notification-settings") public NotificationSettingsResponse notificationSettings(@AuthenticationPrincipal TokenPrincipal principal) {
        return accounts.notificationSettings(principal);
    }
    @PutMapping("/notification-settings") public NotificationSettingsResponse updateNotificationSettings(@AuthenticationPrincipal TokenPrincipal principal,
            @Valid @RequestBody NotificationSettingsRequest request) {
        return accounts.updateNotificationSettings(principal, request);
    }
}
