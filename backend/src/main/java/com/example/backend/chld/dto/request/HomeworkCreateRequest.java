package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HomeworkCreateRequest(@NotNull Long studentId, @NotBlank @Size(max=160) String title,
        @NotBlank @Size(max=60) String type, @NotNull @FutureOrPresent LocalDate dueDate,
        @NotNull @Min(1) Integer targetMinutes, @Size(max=2000) String description, Long exerciseId) {
    /** exerciseId: 숙제로 낼 연습 세트(선택). 없으면 기존처럼 자유 숙제. 기존 호출 호환 생성자 */
    public HomeworkCreateRequest(Long studentId, String title, String type, LocalDate dueDate, Integer targetMinutes, String description) {
        this(studentId, title, type, dueDate, targetMinutes, description, null);
    }
}
