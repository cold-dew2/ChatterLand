package com.example.backend.chld.dto.response;

/** 알림 설정. 저장한 적이 없으면 모두 켜짐(기본값) */
public record NotificationSettingsResponse(boolean homeworkAssigned, boolean homeworkChanged, boolean homeworkDueSoon, boolean homeworkOverdue) {
    public static NotificationSettingsResponse defaults() { return new NotificationSettingsResponse(true, true, true, true); }
}
