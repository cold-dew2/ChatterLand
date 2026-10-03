package com.example.backend.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expirationMs) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("JWT_SECRET 환경변수 설정이 필요합니다.");
        if (expirationMs <= 0) throw new IllegalStateException("JWT_EXPIRATION_MS는 양수여야 합니다.");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** access token의 사용자별 토큰 버전 클레임. 비밀번호를 재설정하면 DB 값이 올라가 이전 토큰이 거절된다. */
    static final String TOKEN_VERSION_CLAIM = "tv";

    /** access token이 속한 로그인 세션(refresh token 묶음) ID. 로그아웃하면 이 세션의 access token이 거절된다. */
    static final String SESSION_CLAIM = "sid";

    /**
     * 서명·만료를 확인한 access token. tokenVersion 클레임이 없는 이전 토큰은 0, 세션 클레임이 없으면 sessionId는 null이다
     * (배포 직후 기존 로그인 유지).
     */
    public record VerifiedToken(TokenPrincipal principal, int tokenVersion) {
        public String sessionId() { return principal.sessionId(); }
    }

    public String createAccessToken(long userId, String role, int tokenVersion) {
        return createAccessToken(userId, role, tokenVersion, null);
    }

    public String createAccessToken(long userId, String role, int tokenVersion, String sessionId) {
        Instant now = Instant.now();
        var builder = Jwts.builder().subject(Long.toString(userId)).claim("role", role).claim(TOKEN_VERSION_CLAIM, tokenVersion);
        if (sessionId != null) builder.claim(SESSION_CLAIM, sessionId);
        return builder.issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key).compact();
    }

    public long expirationMs() { return expirationMs; }

    public VerifiedToken verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        return new VerifiedToken(new TokenPrincipal(Long.parseLong(claims.getSubject()), claims.get("role", String.class),
                claims.get(SESSION_CLAIM, String.class)), version == null ? 0 : version);
    }

    public TokenPrincipal parse(String token) {
        return verify(token).principal();
    }
}
