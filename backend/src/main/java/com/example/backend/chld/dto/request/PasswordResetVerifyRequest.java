package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetVerifyRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) { }
