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
    /**
     * AI 학습 피드백: 음성 분석 결과(목표 문장·인식 문장·자동 오류 후보 또는 선생님 확정 결과, 글자)를 외부 AI 서비스로 전송 (선택).
     * AI_CHAT 안내문은 '대화 내용'만 명시하므로 목적·전송 항목이 다른 학습 피드백은 별도 항목으로 받는다. 회원가입 때가 아니라 마이페이지에서 동의한다.
     */
    public static final String AI_FEEDBACK = "AI_FEEDBACK";

    public static final List<String> TYPES = List.of(PRIVACY, GUARDIAN, VOICE, AI_CHAT, AI_FEEDBACK);

    private ConsentPolicy() { }

    public static boolean guardianRequired(String role, Integer age) {
        return "STUDENT".equals(role) && (age == null || age < GUARDIAN_REQUIRED_UNDER_AGE);
    }
}
