package com.example.backend.global.jwt;

import com.example.backend.chld.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer access token을 확인한다. 서명·만료 외에 다음을 DB로 확인한다(한 번의 조회).
 * - 사용자의 현재 토큰 버전(users.token_version)과 토큰의 버전이 같다: 비밀번호 재설정·변경 뒤 이전 토큰은 즉시 거절된다.
 * - 토큰의 로그인 세션(sid)에 아직 폐기되지 않은 refresh token이 있다: 로그아웃한 기기의 토큰은 즉시 거절된다.
 * 확인용 DB 조회가 실패하면 로그아웃시키지 않도록 401이 아니라 503으로 응답한다.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, AuthService authService) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            JwtUtil.VerifiedToken token;
            try {
                token = jwtUtil.verify(header.substring(7));
            } catch (RuntimeException invalidToken) {
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }
            boolean active;
            try {
                active = authService.isAccessTokenActive(token.principal().userId(), token.tokenVersion(), token.sessionId());
            } catch (DataAccessException unavailable) {
                log.warn("Access token version check failed: {}", unavailable.getClass().getSimpleName());
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"code\":\"AUTH_CHECK_UNAVAILABLE\",\"message\":\"잠시 후 다시 시도해 주세요.\"}");
                return;
            }
            if (active) {
                TokenPrincipal principal = token.principal();
                var auth = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // 로그아웃·비밀번호 변경으로 무효화된 토큰이거나 비활성 계정: 인증하지 않는다(보호 API는 401).
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
