package com.example.backend.chld.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private int status;
    private String code;
    private String message;
    private String path;
    private String token;
}