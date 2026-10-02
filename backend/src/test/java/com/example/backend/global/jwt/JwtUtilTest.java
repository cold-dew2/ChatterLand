package com.example.backend.global.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private final JwtUtil jwt = new JwtUtil("unit-test-secret-with-more-than-thirty-two-characters", 60_000);

    @Test
    void parsesUserAndRoleFromSignedToken() {
        TokenPrincipal principal = jwt.parse(jwt.createAccessToken(42, "TEACHER"));
        assertEquals(42L, principal.userId());
        assertEquals("TEACHER", principal.role());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = jwt.createAccessToken(42, "STUDENT");
        JwtUtil otherKey = new JwtUtil("different-unit-test-key-with-more-than-thirty-two-characters", 60_000);
        assertThrows(JwtException.class, () -> otherKey.parse(token));
    }
}
