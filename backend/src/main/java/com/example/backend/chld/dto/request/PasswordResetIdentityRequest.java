package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 메일 없이 가입 정보로 본인 확인(임시 방식). 학생은 나이까지 확인한다. */
public record PasswordResetIdentityRequest(@NotBlank @Email @Size(max = 254) String email,
                                           @NotBlank @Size(max = 80) String name,
                                           @NotNull Long centerId,
                                           @NotBlank @Pattern(regexp = "STUDENT|TEACHER|GUARDIAN") String role,
                                           @Min(1) @Max(18) Integer age) { }
