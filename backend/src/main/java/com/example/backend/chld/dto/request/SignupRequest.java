package com.example.backend.chld.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank String role,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Long centerId,
        @NotNull Boolean termsAgreed,
        Integer age,
        @Valid SignupConsentRequest consents
) { }
