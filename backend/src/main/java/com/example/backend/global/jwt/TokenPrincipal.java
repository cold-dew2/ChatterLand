package com.example.backend.global.jwt;

/** 인증된 사용자. sessionId는 access token이 속한 로그인 세션(기기)이며, 이 기능 이전에 발급된 토큰은 null이다. */
public record TokenPrincipal(long userId, String role, String sessionId) {
    public TokenPrincipal(long userId, String role) { this(userId, role, null); }
}
