package com.example.backend.chld.dto.response;

public record UserResponse(long userId, String role, String name, String email, long centerId, Long studentId, String centerName) { }
