package com.example.backend.chld.service.impl;

import com.example.backend.chld.mapper.UserMapper;
import com.example.backend.chld.service.CenterService;
import com.example.backend.chld.service.ConsentService;
import com.example.backend.global.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 정리 작업은 한 번에 1000건씩, 남은 행이 없을 때까지 지우고 설정한 보관 기간을 그대로 쓴다. */
class AuthServiceCleanupTest {
    private final UserMapper users = mock(UserMapper.class);
    private final AuthServiceImpl service = new AuthServiceImpl(users, mock(PasswordEncoder.class),
            new JwtUtil("unit-test-secret-with-more-than-thirty-two-characters", 60_000), mock(CenterService.class), mock(ConsentService.class),
            48, 5, Duration.ofMinutes(15), Duration.ofDays(7));

    @Test
    void deletesInBatchesUntilNothingIsLeft() {
        when(users.deleteExpiredRefreshTokens(1000)).thenReturn(1000, 1000, 3);
        when(users.deleteRevokedRefreshTokens(anyLong(), anyInt())).thenReturn(0);
        when(users.deleteOldPasswordChangeFailures(anyLong(), anyInt())).thenReturn(5);
        assertEquals(2008, service.purgeStaleAuthRecords());
        verify(users, times(3)).deleteExpiredRefreshTokens(1000);
        verify(users).deleteRevokedRefreshTokens(eq(7L * 24 * 3600), eq(1000));
        verify(users).deleteOldPasswordChangeFailures(eq(15L * 60), eq(1000));
    }
}
