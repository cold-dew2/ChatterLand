package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인 상태 비밀번호 변경. 새 비밀번호 규칙은 가입·재설정과 같다(8~72자). */
public record PasswordChangeRequest(@NotBlank @Size(max = 72) String currentPassword,
                                    @NotBlank @Size(min = 8, max = 72) String newPassword,
                                    @NotBlank String newPasswordConfirm) { }
