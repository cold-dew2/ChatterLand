package com.example.backend.chld.service;

import java.util.Map;

/**
 * 규칙형 개인화 학습 추천(유료 AI 없이 저장된 연습 기록·콘텐츠 분류만 사용).
 * 추천마다 이유(reason)를 함께 준다. 연습 횟수가 적은 영역을 '부족·취약'으로 진단하지 않는다.
 */
public interface RecommendationService {
    Map<String,Object> recommend(long studentId);
}
