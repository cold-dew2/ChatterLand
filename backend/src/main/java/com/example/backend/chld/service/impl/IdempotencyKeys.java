package com.example.backend.chld.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Idempotency-Key 헤더 공통 처리(음성 분석·숙제 등록).
 * 화면이 요청 의도마다 만든 키를 검사하고, 같은 키로 다른 내용이 왔는지 가리기 위한 요청 지문을 만든다.
 */
final class IdempotencyKeys {
    private static final Pattern REQUEST_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private IdempotencyKeys() { }

    /** 키가 없으면 null(멱등 처리 없이 기존처럼 동작), 형식이 틀리면 400. */
    static String normalize(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) return null;
        String key = requestKey.trim();
        if (!REQUEST_KEY.matcher(key).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key는 영문·숫자·-·_ 8~64자여야 합니다.");
        return key;
    }

    /** 요청 내용 지문. 각 값 앞에 길이를 붙여 값 경계가 섞이지 않게 한다(null은 "-"). */
    static String fingerprint(Object... values) {
        StringBuilder text = new StringBuilder();
        for (Object value : values) {
            String part = value == null ? "-" : String.valueOf(value);
            text.append(part.length()).append(':').append(part).append('|');
        }
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
