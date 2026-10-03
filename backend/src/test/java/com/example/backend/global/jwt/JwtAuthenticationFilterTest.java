package com.example.backend.global.jwt;

import com.example.backend.chld.service.AuthService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** access token 버전 확인: 일치하면 인증, 다르면 인증하지 않음(401은 Security가 응답), DB 조회 실패면 로그아웃시키지 않도록 503. */
class JwtAuthenticationFilterTest {
    private final JwtUtil jwt = new JwtUtil("test-only-signing-key-must-be-at-least-32-bytes", 60_000);
    private final AuthService auth = mock(AuthService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, auth);

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void anActiveTokenAuthenticatesWithItsSession() throws Exception {
        when(auth.isAccessTokenActive(7L, 2, "session-a")).thenReturn(true);
        AtomicReference<Authentication> seen = new AtomicReference<>();
        MockHttpServletResponse response = run(jwt.createAccessToken(7, "TEACHER", 2, "session-a"), seen);
        assertEquals(200, response.getStatus());
        assertNotNull(seen.get());
        TokenPrincipal principal = (TokenPrincipal) seen.get().getPrincipal();
        assertEquals(7L, principal.userId());
        assertEquals("session-a", principal.sessionId(), "로그아웃 API가 이 기기 세션을 알 수 있어야 한다");
    }

    @Test
    void aRevokedSessionOutdatedVersionOrInactiveAccountIsNotAuthenticated() throws Exception {
        when(auth.isAccessTokenActive(anyLong(), anyInt(), any())).thenReturn(false);
        AtomicReference<Authentication> seen = new AtomicReference<>();
        run(jwt.createAccessToken(7, "TEACHER", 2, "logged-out"), seen);
        assertNull(seen.get(), "로그아웃·비밀번호 변경 등으로 무효화된 토큰은 인증하지 않는다");
        verify(auth).isAccessTokenActive(7L, 2, "logged-out");
        // 세션 클레임이 없는 이전 토큰은 세션 ID 없이(버전만) 확인한다.
        run(jwt.createAccessToken(7, "TEACHER", 0), seen);
        verify(auth).isAccessTokenActive(7L, 0, null);
    }

    @Test
    void databaseFailureAnswers503InsteadOfLoggingTheUserOut() throws Exception {
        when(auth.isAccessTokenActive(anyLong(), anyInt(), any())).thenThrow(new DataAccessResourceFailureException("db down"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer " + jwt.createAccessToken(7, "TEACHER", 0));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH_CHECK_UNAVAILABLE"));
        assertFalse(response.getContentAsString().contains("db down"), "내부 오류 메시지를 응답에 노출하지 않는다");
        verifyNoInteractions(chain);
    }

    @Test
    void invalidOrMissingTokensSkipTheDatabase() throws Exception {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        run("not-a-token", seen);
        assertNull(seen.get());
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/centers"), new MockHttpServletResponse(), mock(FilterChain.class));
        verifyNoInteractions(auth);
    }

    private MockHttpServletResponse run(String token, AtomicReference<Authentication> seen) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        seen.set(null);
        filter.doFilter(request, response, (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        return response;
    }
}
