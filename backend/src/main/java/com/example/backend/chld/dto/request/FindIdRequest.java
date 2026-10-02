package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FindIdRequest(@NotBlank @Size(max = 80) String name, @NotNull Long centerId,
                            @NotBlank @Pattern(regexp = "STUDENT|TEACHER") String role) { }
