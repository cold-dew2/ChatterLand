package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 선생님의 음성 분석 검토 결과. AI 결과와 별도로 저장한다. */
public record SpeechReviewRequest(
        @NotBlank @Pattern(regexp = "ACCEPTABLE|NEEDS_PRACTICE|UNCLEAR") String judgement,
        @Size(max = 1000) String note
) { }
