package com.example.backend.chld.exception;

/**
 * 요청이 현재 데이터 상태와 충돌할 때 (409). code로 원인을 구분해 화면이 대응할 수 있게 한다.
 * - VERSION_CONFLICT: 조회한 뒤 다른 곳에서 먼저 수정됨(낙관적 잠금). 최신 데이터를 다시 불러와야 한다.
 * - IDEMPOTENCY_KEY_REUSED: 같은 Idempotency-Key로 다른 내용을 요청함.
 */
public class ConflictException extends RuntimeException {
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
