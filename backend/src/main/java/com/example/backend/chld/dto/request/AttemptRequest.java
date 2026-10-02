package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 연습 기록 저장 요청. score는 실제로 측정된 평가 점수가 있을 때만 보낸다.
 * 로컬 음성 인식은 발음 점수를 만들지 않으므로 score 없이 저장할 수 있다.
 */
public record AttemptRequest(@NotBlank String exerciseId, @NotBlank String itemId,
                             @Min(0) @Max(100) Double score, @NotBlank String audioId) { }
