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

    public String createAccessToken(long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(Long.toString(userId)).claim("role", role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key).compact();
    }

    public TokenPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new TokenPrincipal(Long.parseLong(claims.getSubject()), claims.get("role", String.class));
    }
}
