package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 연습 기록 저장 요청. score는 실제로 측정된 평가 점수가 있을 때만 보낸다.
 * 로컬 음성 인식은 발음 점수를 만들지 않으므로 score 없이 저장할 수 있다.
 * homeworkId: 숙제로 수행한 연습이면 숙제 ID(선택). 있으면 HOMEWORK로 저장한다.
 * practiceType: 숙제가 아닐 때 SELF(학생이 고른 자율 연습) 또는 LESSON(수업 연습, sessionId 필요). 보내지 않으면 기존처럼 PRACTICE(구분 없음).
 */
public record AttemptRequest(@NotBlank String exerciseId, @NotBlank String itemId,
                             @Min(0) @Max(100) Double score, @NotBlank String audioId, Long homeworkId,
                             String practiceType, Long sessionId) {
    /** 기존 호출(숙제 없음) 호환 */
    public AttemptRequest(String exerciseId, String itemId, Double score, String audioId) { this(exerciseId, itemId, score, audioId, null, null, null); }
    public AttemptRequest(String exerciseId, String itemId, Double score, String audioId, Long homeworkId) { this(exerciseId, itemId, score, audioId, homeworkId, null, null); }
}
