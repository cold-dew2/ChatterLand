package com.example.backend.global.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private static final String SECRET = "unit-test-secret-with-more-than-thirty-two-characters";
    private final JwtUtil jwt = new JwtUtil(SECRET, 60_000);

    @Test
    void parsesUserAndRoleFromSignedToken() {
        TokenPrincipal principal = jwt.parse(jwt.createAccessToken(42, "TEACHER", 0));
        assertEquals(42L, principal.userId());
        assertEquals("TEACHER", principal.role());
    }

    @Test
    void carriesTheTokenVersionAndTreatsTokensWithoutItAsVersionZero() {
        assertEquals(5, jwt.verify(jwt.createAccessToken(42, "TEACHER", 5)).tokenVersion());
        String legacy = Jwts.builder().subject("42").claim("role", "TEACHER").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertEquals(0, jwt.verify(legacy).tokenVersion());
        // 버전 클레임을 고쳐 쓰면 서명이 깨진다.
        String[] parts = jwt.createAccessToken(42, "TEACHER", 0).split("\\.");
        String bumped = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).replace("\"tv\":0", "\"tv\":1").getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> jwt.verify(parts[0] + "." + bumped + "." + parts[2]));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = jwt.createAccessToken(42, "STUDENT", 0);
        JwtUtil otherKey = new JwtUtil("different-unit-test-key-with-more-than-thirty-two-characters", 60_000);
        assertThrows(JwtException.class, () -> otherKey.parse(token));
    }

    @Test
    void rejectsExpiredToken() {
        Instant past = Instant.now().minusSeconds(3600);
        String expired = Jwts.builder().subject("42").claim("role", "STUDENT").issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(60))).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThrows(ExpiredJwtException.class, () -> jwt.parse(expired));
    }

    @Test
    void tokenIssuedByThisUtilExpiresAfterTheConfiguredTime() throws Exception {
        JwtUtil shortLived = new JwtUtil(SECRET, 1);
        String token = shortLived.createAccessToken(42, "STUDENT", 0);
        Thread.sleep(1100); // JWT exp는 초 단위이므로 1초 이상 기다린다.
        assertThrows(ExpiredJwtException.class, () -> shortLived.parse(token));
    }

    @Test
    void rejectsTamperedPayloadAndUnsignedToken() {
        String[] parts = jwt.createAccessToken(42, "STUDENT", 0).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).replace("STUDENT", "TEACHER").getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> jwt.parse(parts[0] + "." + forgedPayload + "." + parts[2]));
        String none = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> jwt.parse(none + "." + parts[1] + "."));
        assertThrows(RuntimeException.class, () -> jwt.parse("not.a.jwt"));
        assertThrows(RuntimeException.class, () -> jwt.parse(""));
    }

    @Test
    void refusesToStartWithoutSecretOrWithNonPositiveExpiration() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil(" ", 60_000));
        assertThrows(IllegalStateException.class, () -> new JwtUtil(SECRET, 0));
    }
}
