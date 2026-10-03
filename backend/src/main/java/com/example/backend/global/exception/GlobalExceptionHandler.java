// 예외를 잡아서 프론트가 보기 좋은 JSON으로 바꿔주는 곳
package com.example.backend.global.exception;

import com.example.backend.chld.exception.AiProviderException;
import com.example.backend.chld.exception.ConflictException;
import com.example.backend.chld.exception.ConsentRequiredException;
import com.example.backend.chld.exception.MailUnavailableException;
import com.example.backend.chld.exception.MemberException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        if (e instanceof MethodArgumentNotValidException invalid) {
            log.warn("Request validation failed for fields {}", invalid.getBindingResult().getFieldErrors().stream().map(error -> error.getField()).distinct().toList());
        }
        return ResponseEntity.badRequest().body(ErrorResponse.builder().success(false)
                .code("VALIDATION_ERROR").message("요청값을 확인해 주세요.").build());
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMissingRequestPart(Exception e) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder().success(false)
                .code("VALIDATION_ERROR").message("필수 요청값이 누락되었습니다.").build());
    }

    /** 경로 변수·쿼리 값의 형식 오류(예: 숫자 ID 자리에 문자)는 서버 오류가 아니라 잘못된 요청이다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder().success(false)
                .code("VALIDATION_ERROR").message("요청값의 형식이 올바르지 않습니다.").build());
    }

    /**
     * 없는 경로(404), 지원하지 않는 메서드(405), 지원하지 않는 Content-Type(415) 등 Spring 표준 요청 오류는
     * 아래 Exception 처리기로 넘어가 500이 되지 않도록 원래 상태 코드를 유지한다.
     */
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleSpringRequestError(Exception e) {
        org.springframework.http.HttpStatusCode status = ((org.springframework.web.ErrorResponse) e).getStatusCode();
        String message = status.value() == 404 ? "요청한 주소를 찾을 수 없습니다."
                : status.value() == 405 ? "지원하지 않는 요청 방식입니다."
                : status.value() == 415 ? "지원하지 않는 요청 형식입니다." : "요청을 처리하지 못했습니다.";
        return ResponseEntity.status(status).body(ErrorResponse.builder().success(false)
                .code(status.toString()).message(message).build());
    }

    @ExceptionHandler(ConsentRequiredException.class)
    public ResponseEntity<ErrorResponse> handleConsentRequired(ConsentRequiredException e) {
        return ResponseEntity.status(403).body(ErrorResponse.builder().success(false)
                .code("CONSENT_REQUIRED").message(e.getMessage()).build());
    }

    @ExceptionHandler(MailUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMailUnavailable(MailUnavailableException e) {
        return ResponseEntity.status(503).body(ErrorResponse.builder().success(false)
                .code(e.getCode()).message(e.getMessage()).build());
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ErrorResponse> handleAiProvider(AiProviderException e) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.builder().success(false)
                .code(e.getCode()).message(e.getMessage()).build());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(409).body(ErrorResponse.builder().success(false)
                .code(e.getCode()).message(e.getMessage()).build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(ErrorResponse.builder().success(false)
                .code("PAYLOAD_TOO_LARGE").message("업로드 파일은 10MB 이하여야 합니다.").build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(ErrorResponse.builder().success(false)
                .code("DATA_CONFLICT").message("이미 등록된 데이터이거나 연결된 정보가 유효하지 않습니다.").build());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatusException(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(
                ErrorResponse.builder()
                        .success(false)
                        .code(e.getStatusCode().toString())
                        .message(e.getReason() == null ? "요청을 처리하지 못했습니다." : e.getReason())
                        .build()
        );
    }

    /**
     * 회원 관련 예외 처리
     *
     * 예)
     * throw new MemberException(...)
     *
     * 발생 시 여기서 잡는다.
     */
    @ExceptionHandler(MemberException.class)
    public ResponseEntity<ErrorResponse> handleMemberException(
            MemberException e
    ) {

        // 로그 출력(메시지는 마스킹)
        log.warn("Member Exception : {}", LogMasking.mask(e.getMessage()));

        // 에러 응답 생성 후 반환
        return ResponseEntity
                .status(
                        e.getErrorCode().getStatus()
                )
                .body(
                        ErrorResponse.builder()
                                .success(false)
                                .message(
                                        e.getErrorCode().getMessage()
                                )
                                .build()
                );
    }

    /**
     * 예상하지 못한 서버 오류 처리
     *
     * NullPointerException
     * DB 오류
     * 기타 RuntimeException
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e
    ) {

        // 원인 파악용 로그. 예외 메시지에 입력값(이메일 등)이 섞일 수 있어 마스킹한 요약과 앱 코드 위치만 남긴다.
        log.error("Unexpected Exception {}", LogMasking.describe(e));

        // 사용자에게는 공통 메시지 반환
        return ResponseEntity
                        .internalServerError()
                        .body(
                                ErrorResponse.builder()
                                        .success(false)
                                        .code("INTERNAL_SERVER_ERROR")
                                        .message(
                                        "서버 내부 오류가 발생했습니다."
                                )
                                .build()
                );
    }
}
