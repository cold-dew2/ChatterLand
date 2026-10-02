// 예외를 잡아서 프론트가 보기 좋은 JSON으로 바꿔주는 곳
package com.example.backend.global.exception;

import com.example.backend.chld.exception.ConsentRequiredException;
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

    @ExceptionHandler(ConsentRequiredException.class)
    public ResponseEntity<ErrorResponse> handleConsentRequired(ConsentRequiredException e) {
        return ResponseEntity.status(403).body(ErrorResponse.builder().success(false)
                .code("CONSENT_REQUIRED").message(e.getMessage()).build());
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

        // 로그 출력
        log.warn(
                "Member Exception : {}",
                e.getMessage()
        );

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

        // 에러 로그 출력
        log.error(
                "Unexpected Exception",
                e
        );

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
