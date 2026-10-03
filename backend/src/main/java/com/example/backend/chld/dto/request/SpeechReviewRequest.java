package com.example.backend.chld.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 선생님의 음성 분석 검토 결과. 자동 분석 결과(오류 후보)와 별도 필드로 저장한다.
 * confirmedErrors는 선생님이 직접 듣고 확정한 오류 음소이며 생략할 수 있다.
 */
public record SpeechReviewRequest(
        @NotBlank @Pattern(regexp = "ACCEPTABLE|NEEDS_PRACTICE|UNCLEAR") String judgement,
        @Size(max = 1000) String note,
        @Valid @Size(max = 20) List<ConfirmedError> confirmedErrors
) {
    /** errorType: SUBSTITUTION(대치)·OMISSION(생략)·DISTORTION(왜곡)·ADDITION(첨가) */
    public record ConfirmedError(
            @NotBlank @Size(max = 4) String phoneme,
            @NotBlank @Pattern(regexp = "SUBSTITUTION|OMISSION|DISTORTION|ADDITION") String errorType,
            @Size(max = 4) String produced,
            @Size(max = 40) String position
    ) { }
}
