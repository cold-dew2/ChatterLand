package com.example.backend.chld.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 음성 인식(ASR) 결과. 모델이 인식한 텍스트이며 발음 정확도와 동일하지 않다.
 * confidence는 모델 토큰 확률 평균(0~1)이고, 발음 평가 신뢰도가 아니다.
 * speechSegments는 VAD가 찾은 말소리 구간(원본 녹음 기준 ms)이며, VAD를 쓰지 못한 경우 null이다.
 * peakAmplitude·clippedRatio는 녹음 품질 확인용 측정값이다.
 */
public record SpeechRecognitionResult(
        String transcript,
        BigDecimal confidence,
        String engineName,
        String modelName,
        long audioDurationMs,
        long processingMs,
        List<SpeechSegment> speechSegments,
        int peakAmplitude,
        double clippedRatio
) {
    public record SpeechSegment(long startMs, long endMs) { }
}
