package com.example.backend.chld.service.impl;

import com.example.backend.chld.service.AuthService;
import com.example.backend.global.exception.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 인증 기록을 주기적으로 지운다(기본: 시작 2분 후, 이후 6시간마다). 지운 건수만 기록하고 토큰 값은 남기지 않는다.
 * 쌓이기만 하던 refresh_tokens 행(만료·폐기)이 대상이며, 현재 로그인 세션은 지우지 않는다.
 */
@Slf4j
@Component
public class AuthTokenCleanupScheduler {
    private final AuthService authService;

    public AuthTokenCleanupScheduler(AuthService authService) { this.authService = authService; }

    @Scheduled(initialDelayString = "${app.auth.token-cleanup-initial-delay:PT2M}", fixedDelayString = "${app.auth.token-cleanup-interval:PT6H}")
    public void purgeStaleAuthRecords() {
        try {
            int deleted = authService.purgeStaleAuthRecords();
            if (deleted > 0) log.info("Purged {} stale auth records (expired/revoked refresh tokens, old password change failures)", deleted);
        } catch (RuntimeException e) {
            log.error("Auth record cleanup failed: {}", LogMasking.describe(e));
        }
    }
}
