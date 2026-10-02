package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 비밀번호 정책은 회원가입(SignupRequest)과 같은 8~72자다. */
public record PasswordResetConfirmRequest(@NotBlank String resetToken,
                                          @NotBlank @Size(min = 8, max = 72) String newPassword,
                                          @NotBlank String newPasswordConfirm) { }
