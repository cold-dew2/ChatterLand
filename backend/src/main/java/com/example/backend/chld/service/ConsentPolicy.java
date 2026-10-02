package com.example.backend.chld.service;

import java.util.List;

/**
 * 동의 항목과 현재 안내문 버전. 안내 문구를 바꾸면 버전을 올리고 프런트엔드 상수(consentPolicy.ts)도 함께 바꾼다.
 * 법정대리인 동의 연령 기준(만 14세 미만)은 개인정보 보호법 제22조의2를 따른 운영 기준이며, 확정 법률 자문이 아니다.
 */
public final class ConsentPolicy {
    public static final String CURRENT_VERSION = "2026-10-01";
    public static final int GUARDIAN_REQUIRED_UNDER_AGE = 14;

    /** 개인정보 수집·이용 (필수) */
    public static final String PRIVACY = "PRIVACY";
    /** 만 14세 미만 아동의 법정대리인 동의 (해당 학생 필수) */
    public static final String GUARDIAN = "GUARDIAN";
    /** 아동 음성 녹음 수집·이용 및 서버 로컬 음성 인식 (선택, 말하기 연습·음성 대화에 필요) */
    public static final String VOICE = "VOICE";
    /** AI 대화 내용(텍스트)을 외부 AI 서비스로 전송 (선택, AI 대화에 필요) */
    public static final String AI_CHAT = "AI_CHAT";

    public static final List<String> TYPES = List.of(PRIVACY, GUARDIAN, VOICE, AI_CHAT);

    private ConsentPolicy() { }

    public static boolean guardianRequired(String role, Integer age) {
        return "STUDENT".equals(role) && (age == null || age < GUARDIAN_REQUIRED_UNDER_AGE);
    }
}
