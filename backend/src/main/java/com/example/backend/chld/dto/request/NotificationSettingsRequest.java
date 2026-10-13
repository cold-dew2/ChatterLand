package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotNull;

/** 알림 설정(모두 필수). 실제로 일어나는 숙제 이벤트만 다룬다. */
public record NotificationSettingsRequest(@NotNull Boolean homeworkAssigned, @NotNull Boolean homeworkChanged,
        @NotNull Boolean homeworkDueSoon, @NotNull Boolean homeworkOverdue) { }
