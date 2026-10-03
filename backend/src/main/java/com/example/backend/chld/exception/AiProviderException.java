package com.example.backend.chld.exception;

import org.springframework.http.HttpStatus;

/**
 * 외부 AI 호출 실패. code로 원인을 구분해 화면이 대응할 수 있게 한다(응답 메시지에 키·요청 내용을 넣지 않는다).
 * - AI_NOT_CONFIGURED(503): AI_API_URL·AI_API_KEY 없음
 * - AI_AUTH_FAILED(503): 제공자가 키를 거부(401·403)
 * - AI_RATE_LIMITED(429): 제공자 사용량 제한
 * - AI_TIMEOUT(504): 연결·응답 시간 초과
 * - AI_REQUEST_REJECTED(502): 제공자가 요청을 거부(400·404: 모델 이름·주소 확인 필요)
 * - AI_PROVIDER_ERROR(502): 제공자 서버 오류(5xx)
 * - AI_BLOCKED(502): 안전 정책으로 응답 차단
 * - AI_BAD_RESPONSE(502): 응답이 비었거나 형식·내용 규칙에 맞지 않음
 */
public class AiProviderException extends RuntimeException {
    public static final String NOT_CONFIGURED = "AI_NOT_CONFIGURED";
    public static final String AUTH_FAILED = "AI_AUTH_FAILED";
    public static final String RATE_LIMITED = "AI_RATE_LIMITED";
    public static final String TIMEOUT = "AI_TIMEOUT";
    public static final String REQUEST_REJECTED = "AI_REQUEST_REJECTED";
    public static final String PROVIDER_ERROR = "AI_PROVIDER_ERROR";
    public static final String BLOCKED = "AI_BLOCKED";
    public static final String BAD_RESPONSE = "AI_BAD_RESPONSE";

    private final String code;
    private final HttpStatus status;

    public AiProviderException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
