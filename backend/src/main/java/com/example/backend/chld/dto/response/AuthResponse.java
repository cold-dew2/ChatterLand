package com.example.backend.chld.dto.response;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) { }
