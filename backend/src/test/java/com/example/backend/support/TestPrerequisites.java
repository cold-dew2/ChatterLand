package com.example.backend.support;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 외부 준비물(whisper 모델·VAD·Mailpit)이 필요한 테스트의 사전 조건.
 * 기본은 준비물이 없으면 SKIPPED(성공으로 집계하지 않음)이고, CI처럼 반드시 실행해야 하는 환경에서는
 * REQUIRE_SPEECH_MODEL=true / REQUIRE_MAILPIT=true 로 설정해 준비물 누락을 FAIL로 드러낸다.
 */
public final class TestPrerequisites {
    private TestPrerequisites() { }

    public static void requireSpeech(boolean available, String reason) { require("REQUIRE_SPEECH_MODEL", available, reason); }

    public static void requireMailpit(boolean available, String reason) { require("REQUIRE_MAILPIT", available, reason); }

    private static void require(String flag, boolean available, String reason) {
        if (available) return;
        if ("true".equalsIgnoreCase(System.getenv(flag)) || Boolean.getBoolean(flag))
            fail(reason + " (" + flag + "=true 환경이라 건너뛰지 않고 실패로 처리합니다)");
        assumeTrue(false, reason);
    }
}
