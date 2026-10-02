package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.LoginRequest;
import com.example.backend.chld.dto.request.RefreshRequest;
import com.example.backend.chld.dto.request.SignupRequest;
import com.example.backend.chld.dto.response.AuthResponse;
import com.example.backend.chld.dto.response.TokenResponse;
import com.example.backend.chld.dto.response.UserResponse;

public interface AuthService {
    UserResponse signup(SignupRequest request);
    AuthResponse login(LoginRequest request);
    TokenResponse refresh(RefreshRequest request);
    void logout(String refreshToken);
    UserResponse currentUser(long userId);
}
