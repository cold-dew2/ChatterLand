package com.example.backend.chld.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentCreateRequest(Long studentId, @NotBlank @Size(max=80) String name,
        @Min(1) @Max(18) Integer age, @Size(max=30) String parentPhone,
        List<@Size(max=40) String> tags, @Size(max=1000) String memo,
        @Min(1) @Max(100) Integer sessionsTotal,String status,
        @Pattern(regexp="GENERAL|THERAPY") String learnerType) { }
