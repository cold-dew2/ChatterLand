package com.example.backend.chld.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor

public class SignupRequest {

    private String userId;
    private String userPw;
    private String userNm;
    private String email;
    private String stateCd;
    private String roleCd;
    private LocalDate birthDt;
    private String genderCd;
    private String phoneNum;
    private LocalDateTime createdDt;
    private LocalDateTime updatedDt;
    private String centerId;
}