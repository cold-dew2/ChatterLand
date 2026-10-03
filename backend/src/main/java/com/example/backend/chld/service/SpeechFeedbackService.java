package com.example.backend.chld.service;

import java.util.Map;

/**
 * AI 학습 피드백. 확인된 분석 결과(자동 분석·교사 확정)를 근거로 쉬운 설명만 만든다.
 * 점수를 만들거나 분석 결과·교사 확정 결과를 바꾸지 않으며, 별도 테이블에 보관한다.
 */
public interface SpeechFeedbackService {
    /** 저장된 피드백 상태만 돌려준다(AI를 호출하지 않는다). analysis는 학생 본인의 분석 응답(SpeechAnalysisService.toResponse). */
    Map<String,Object> feedback(long userId, Map<String,Object> analysis);
    /** 근거가 충분하면 피드백을 만든다(같은 근거로 이미 만든 피드백이 있으면 그대로 돌려준다). */
    Map<String,Object> generate(long userId, Map<String,Object> analysis);
}
