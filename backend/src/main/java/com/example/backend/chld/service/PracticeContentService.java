package com.example.backend.chld.service;

import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.global.jwt.TokenPrincipal;

import java.util.Map;

/** 연습 콘텐츠 검색·조회(학생 자율 연습, 교사 숙제 만들기 공통). 콘텐츠는 학생 개인정보가 아니므로 로그인한 학생·교사 모두 볼 수 있다. */
public interface PracticeContentService {
    PageResponse<Map<String,Object>> search(TokenPrincipal principal, String keyword, String categoryId, String difficulty,
                                            String rule, String contentType, int page, int size);
    Map<String,Object> detail(TokenPrincipal principal, long exerciseId);
}
