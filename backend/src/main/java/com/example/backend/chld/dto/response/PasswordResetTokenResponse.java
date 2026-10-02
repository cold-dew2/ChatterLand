package com.example.backend.chld.dto.response;

public record PasswordResetTokenResponse(String resetToken, long expiresInSeconds) { }
