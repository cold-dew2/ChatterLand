package com.example.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호 변경 시도 제한의 동시성과 오래된 인증 기록 정리를 실제 DB로 확인한다.
 * 동시 요청은 서로 다른 스레드·커넥션에서 실행되므로 테스트 트랜잭션(롤백)을 쓰지 않고, 만든 데이터를 @AfterEach에서 직접 지운다.
 */
@SpringBootTest(properties = {"jwt.secret=test-only-signing-key-must-be-at-least-32-bytes", "app.scheduling.enabled=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuthMaintenanceIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.example.backend.chld.service.AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) jdbc.update("DELETE FROM users WHERE email=?", email); // refresh_tokens·실패 기록·동의는 CASCADE
    }

    @Test
    void concurrentWrongAttemptsCannotExceedTheLimit() throws Exception {
        String email = signupTeacher();
        String access = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> codes = new ArrayList<>();
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                start.await();
                return mvc.perform(patch("/api/v1/auth/me/password").header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong-Current!1\",\"newPassword\":\"Changed-Pass!789\",\"newPasswordConfirm\":\"Changed-Pass!789\"}"))
                        .andReturn().getResponse().getStatus();
            }));
            start.countDown();
            for (Future<Integer> future : futures) codes.add(future.get());
        } finally {
            pool.shutdownNow();
        }
        // 사용자 행 잠금으로 차례로 처리된다: 4번은 남은 횟수 안내(400), 5번째부터는 모두 429. 실패 기록은 허용 횟수(5)를 넘지 않는다.
        assertEquals(4, codes.stream().filter(code -> code == 400).count(), codes.toString());
        assertEquals(4, codes.stream().filter(code -> code == 429).count(), codes.toString());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM password_change_failures WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Integer.class, email));
    }

    @Test
    void cleanupRemovesOnlyExpiredOrLongRevokedTokensAndOldFailures() throws Exception {
        String email = signupTeacher();
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
        String session = UUID.randomUUID().toString();
        insertToken(userId, "active", "DATE_ADD(NOW(), INTERVAL 10 DAY)", null, session);
        insertToken(userId, "revoked-recent", "DATE_ADD(NOW(), INTERVAL 10 DAY)", "DATE_SUB(NOW(), INTERVAL 1 DAY)", session);
        insertToken(userId, "revoked-old", "DATE_ADD(NOW(), INTERVAL 10 DAY)", "DATE_SUB(NOW(), INTERVAL 8 DAY)", session);
        insertToken(userId, "expired", "DATE_SUB(NOW(), INTERVAL 1 MINUTE)", null, session);
        // 한 번에 지우는 건수(1000)보다 많은 만료 행도 모두 지워져야 한다.
        jdbc.batchUpdate("INSERT INTO refresh_tokens(user_id, token_hash, expires_at) VALUES(?, ?, DATE_SUB(NOW(), INTERVAL 1 DAY))",
                java.util.stream.IntStream.range(0, 1205).mapToObj(i -> new Object[]{userId, hash("bulk-" + userId + "-" + i)}).toList());
        jdbc.update("INSERT INTO password_change_failures(user_id, created_at) VALUES(?, DATE_SUB(NOW(), INTERVAL 20 MINUTE))", userId);
        jdbc.update("INSERT INTO password_change_failures(user_id) VALUES(?)", userId);

        assertTrue(authService.purgeStaleAuthRecords() >= 1205 + 2 + 1);

        List<String> left = jdbc.queryForList("SELECT token_hash FROM refresh_tokens WHERE user_id=?", String.class, userId);
        assertEquals(java.util.Set.of(hash("active"), hash("revoked-recent")), java.util.Set.copyOf(left),
                "현재 세션(폐기·만료 전)과 보관 기간(7일) 안의 폐기 행은 남긴다");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM password_change_failures WHERE user_id=?", Integer.class, userId),
                "제한 기간(15분) 안의 실패 기록은 남긴다");
    }

    private void insertToken(long userId, String name, String expiresSql, String revokedSql, String session) {
        jdbc.update("INSERT INTO refresh_tokens(user_id, token_hash, expires_at, revoked_at, session_id) VALUES(?, ?, " + expiresSql + ", "
                + (revokedSql == null ? "NULL" : revokedSql) + ", ?)", userId, hash(name), session);
    }

    private static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String signupTeacher() throws Exception {
        String email = "maint-" + UUID.randomUUID() + "@example.test";
        createdEmails.add(email);
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"정리 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return email;
    }
}
