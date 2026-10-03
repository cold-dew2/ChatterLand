package com.example.backend.global.exception;

import java.util.regex.Pattern;

/**
 * 로그에 남기기 전에 민감 정보를 가린다. 예외 메시지에는 DB 드라이버가 넣은 입력값(예: Duplicate entry 'parent@example.com')이나
 * 요청 조각이 들어갈 수 있으므로, 이메일·JWT·Bearer 토큰·비밀번호/토큰/코드 값·전화번호를 마스킹한다.
 */
public final class LogMasking {
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)(\"?(?:password|passwd|pwd|newPassword|currentPassword|token|accessToken|refreshToken|resetToken|code|secret|apiKey|api_key|authorization|cookie)\"?\\s*[:=]\\s*\"?)([^\"\\s,;&}]+)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b");

    private LogMasking() { }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) return value;
        String masked = JWT.matcher(value).replaceAll("[JWT]");
        masked = BEARER.matcher(masked).replaceAll("$1[TOKEN]");
        masked = SECRET_FIELD.matcher(masked).replaceAll("$1[MASKED]");
        masked = EMAIL.matcher(masked).replaceAll("[EMAIL]");
        return PHONE.matcher(masked).replaceAll("[PHONE]");
    }

    /** 예외 체인을 "유형: 가린 메시지" 형식으로, 앱 코드 위치 몇 줄과 함께 요약한다(원본 메시지를 그대로 출력하지 않는다). */
    public static String describe(Throwable error) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (Throwable current = error; current != null && depth < 5; current = current.getCause(), depth++) {
            if (depth > 0) out.append(" <- caused by ");
            out.append(current.getClass().getName()).append(": ").append(mask(String.valueOf(current.getMessage())));
        }
        int frames = 0;
        for (StackTraceElement frame : error.getStackTrace()) {
            if (!frame.getClassName().startsWith("com.example.backend")) continue;
            out.append("\n    at ").append(frame);
            if (++frames == 8) break;
        }
        return out.toString();
    }
}
