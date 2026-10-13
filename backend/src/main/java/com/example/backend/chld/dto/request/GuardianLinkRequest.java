package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 자녀 계정에서 받은 연결 코드(하이픈·공백·대소문자는 서버에서 정리한다). 학생 ID는 받지 않는다. */
public record GuardianLinkRequest(@NotBlank @Size(max = 20) String code) { }
