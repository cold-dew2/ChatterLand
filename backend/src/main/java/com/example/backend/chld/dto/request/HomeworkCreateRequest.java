package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HomeworkCreateRequest(@NotNull Long studentId, @NotBlank @Size(max=160) String title,
        @NotBlank @Size(max=60) String type, @NotNull @FutureOrPresent LocalDate dueDate,
        @NotNull @Min(1) Integer targetMinutes, @Size(max=2000) String description) { }
