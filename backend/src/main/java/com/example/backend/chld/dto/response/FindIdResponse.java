package com.example.backend.chld.dto.response;

import java.util.List;

/** 일부를 가린 이메일(로그인 아이디) 목록 */
public record FindIdResponse(List<String> maskedEmails) { }
