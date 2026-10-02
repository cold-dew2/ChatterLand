package com.example.backend.chld.dto.response;

import java.math.BigDecimal;

/**
 * 음성 인식(ASR) 결과. 모델이 인식한 텍스트이며 발음 정확도와 동일하지 않다.
 * confidence는 모델 토큰 확률 평균(0~1)이고, 발음 평가 신뢰도가 아니다.
 */
public record SpeechRecognitionResult(
        String transcript,
        BigDecimal confidence,
        String engineName,
        String modelName,
        long audioDurationMs,
        long processingMs
) { }
