package com.example.backend.chld.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor

public class LoginRequest {
    private String userId;
    private String userPw;
}