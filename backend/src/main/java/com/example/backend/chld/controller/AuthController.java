package com.example.backend.chld.controller;

import com.example.backend.chld.dto.request.FindIdRequest;
import com.example.backend.chld.dto.request.LoginRequest;
import com.example.backend.chld.dto.request.PasswordResetConfirmRequest;
import com.example.backend.chld.dto.request.PasswordResetRequest;
import com.example.backend.chld.dto.request.PasswordResetVerifyRequest;
import com.example.backend.chld.dto.request.RefreshRequest;
import com.example.backend.chld.dto.request.SignupRequest;
import com.example.backend.chld.dto.response.AuthResponse;
import com.example.backend.chld.dto.response.FindIdResponse;
import com.example.backend.chld.dto.response.PasswordResetTokenResponse;
import com.example.backend.chld.dto.response.TokenResponse;
import com.example.backend.chld.dto.response.UserResponse;
import com.example.backend.chld.service.AccountRecoveryService;
import com.example.backend.chld.service.AuthService;
import com.example.backend.global.jwt.TokenPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AccountRecoveryService recovery;
    public AuthController(AuthService authService, AccountRecoveryService recovery) { this.authService = authService; this.recovery = recovery; }

    @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) { return authService.signup(request); }
    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }
    @PostMapping("/refresh") public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) { return authService.refresh(request); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) Map<String, String> body) { authService.logout(body == null ? null : body.get("refreshToken")); }
    @PostMapping("/find-id") public FindIdResponse findId(@Valid @RequestBody FindIdRequest request) { return recovery.findId(request); }
    @PostMapping("/password-reset/request") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        recovery.requestPasswordReset(request);
        return Map.of("message", "입력한 이메일로 가입된 계정이 있으면 인증 코드를 보냈어요. 메일함(스팸함 포함)을 확인해 주세요.");
    }
    @PostMapping("/password-reset/verify") public PasswordResetTokenResponse verifyResetCode(@Valid @RequestBody PasswordResetVerifyRequest request) { return recovery.verifyResetCode(request); }
    @PostMapping("/password-reset/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) { recovery.confirmPasswordReset(request); }
    @GetMapping("/me") public UserResponse me(@AuthenticationPrincipal TokenPrincipal principal) { return authService.currentUser(principal.userId()); }
}
