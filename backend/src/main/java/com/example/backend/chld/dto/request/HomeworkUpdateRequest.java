package com.example.backend.chld.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HomeworkUpdateRequest(@Size(max=160) String title,@Size(max=60) String type,
        @Size(max=2000) String description,@Min(1) Integer targetMinutes,
        @FutureOrPresent LocalDate dueDate,String status,Boolean done) { }
