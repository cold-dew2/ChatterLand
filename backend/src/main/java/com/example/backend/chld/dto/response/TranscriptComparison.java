package com.example.backend.chld.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 목표 문장과 인식 문장의 비교 결과.
 * matchRate는 음절 기준 일치도(1 - 음절 오류율, 0~100)이며 발음 정확도 점수가 아니다.
 */
public record TranscriptComparison(
        String targetText,
        String recognizedText,
        BigDecimal matchRate,
        List<WordDiff> words
) {
    public enum DiffType { MATCH, MISSING, INSERTED, SUBSTITUTED }

    public record WordDiff(DiffType type, String expected, String recognized) { }
}
