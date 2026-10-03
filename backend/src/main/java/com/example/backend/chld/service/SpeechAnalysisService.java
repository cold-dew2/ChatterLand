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

    /**
     * targetPhonemes: 연습 문제의 목표 음소 설정(예: "ㄹ", "ㅂ,ㅍ"). 없으면 null.
     * requestKey: 화면이 녹음마다 만든 Idempotency-Key(선택). 같은 키는 학생별로 한 번만 분석하고, 이후 요청에는 같은 분석을 돌려준다.
     */
    Map<String,Object> analyze(long studentId, String learnerType, long exerciseId, long itemId, String targetText,
                               String targetPhonemes, MultipartFile audio, String requestKey);

    Map<String,Object> findAnalysis(long studentId, String analysisId);

    /**
     * 처리 중(PROCESSING)인 채로 기준 시간을 넘긴 분석을 실패(STALE_PROCESSING)로 바꾸고 요청 키·녹음을 정리한다.
     * 서버 종료 등으로 끝나지 못한 분석 때문에 같은 녹음을 다시 분석할 수 없게 되는 것을 막는다. 정리한 건수를 돌려준다.
     */
    int recoverStaleAnalyses();

    /** DB에 저장된 비교 결과 JSON을 응답용 객체로 바꾼다. */
    Map<String,Object> toResponse(Map<String,Object> row);

    /** 검토 결과 등 구조화된 값을 DB 저장용 JSON으로 바꾼다. */
    String toJson(Object value);
}
