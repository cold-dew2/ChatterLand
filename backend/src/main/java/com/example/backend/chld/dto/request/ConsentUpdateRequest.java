package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsentUpdateRequest(
        @NotNull Boolean agreed,
        @NotBlank String policyVersion,
        Boolean guardianConfirmed,
        @Size(max = 80) String guardianName,
        @Size(max = 20) String guardianRelation
) { }
