package com.example.backend.chld.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 연습 녹음 분석 흐름(저장 → 인식 → 목표 문장 비교 → 결과 저장)을 담당한다.
 * 일반 아동 문장 연습과 언어재활 아동 발음 평가를 구분해 처리한다.
 */
public interface SpeechAnalysisService {
    String LEARNER_GENERAL = "GENERAL";
    String LEARNER_THERAPY = "THERAPY";

    Map<String,Object> analyze(long studentId, String learnerType, long exerciseId, long itemId, String targetText, MultipartFile audio);

    Map<String,Object> findAnalysis(long studentId, String analysisId);

    /** DB에 저장된 비교 결과 JSON을 응답용 객체로 바꾼다. */
    Map<String,Object> toResponse(Map<String,Object> row);
}
