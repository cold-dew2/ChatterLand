package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 내 정보 수정: 이름만 바꾼다(이메일은 로그인 아이디, 나이는 보호자 동의 기준이라 센터를 통해 바꾼다). 회원가입과 같은 규칙 */
public record ProfileUpdateRequest(@NotBlank(message="이름을 입력해 주세요.") @Size(max=80, message="이름은 80자 이하로 입력해 주세요.")
        @Pattern(regexp="(?s).*\\S.*", message="이름을 입력해 주세요.") String name) { }
