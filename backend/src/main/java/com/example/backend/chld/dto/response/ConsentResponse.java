package com.example.backend.chld.dto.response;

public record ConsentResponse(
        String type,
        boolean agreed,
        boolean required,
        String policyVersion,
        String agreedAt,
        String withdrawnAt,
        String guardianName,
        String guardianRelation,
        boolean currentVersion
) { }
