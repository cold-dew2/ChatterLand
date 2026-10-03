package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 로그아웃 시 access token 즉시 무효화(로그인 세션 단위), 로그인 상태 비밀번호 변경, access token 기본 수명.
 * 로그아웃한 기기의 토큰은 만료 전이어도 서버가 거절하고, 다른 기기의 세션은 유지되는지 확인한다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
// 실패 시 MockMvc가 요청 본문(비밀번호)을 출력하는 테스트용 덤프를 끈다: 로그 검사는 애플리케이션이 남긴 로그만 대상으로 한다.
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AuthSessionIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    private static final String NEW_PASSWORD = "Changed-Pass!789";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.core.env.Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 정책 5: 로그아웃 ─────────────────────────────

    @Test
    void logoutBlocksThisDevicesAccessTokensImmediatelyButKeepsOtherDevices() throws Exception {
        String email = signupTeacher();
        Tokens phone = login(email, PASSWORD);
        Tokens laptop = login(email, PASSWORD);
        Tokens phoneRotated = refresh(phone.refresh); // 같은 기기(세션)에서 갱신한 토큰
        for (Tokens t : List.of(phone, phoneRotated, laptop)) me(t.access).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + phoneRotated.access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + phoneRotated.refresh + "\"}"))
                .andExpect(status().isNoContent());

        // 이 기기: 갱신 전·후 access token 모두 만료 전이어도 401, refresh도 401
        me(phone.access).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        me(phoneRotated.access).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teachers/me/students").header("Authorization", "Bearer " + phoneRotated.access)).andExpect(status().isUnauthorized());
        refreshStatus(phoneRotated.refresh, 401);
        // 다른 기기: 그대로 유지
        me(laptop.access).andExpect(status().isOk());
        Tokens laptopRotated = refresh(laptop.refresh);
        me(laptopRotated.access).andExpect(status().isOk());
    }

    @Test
    void logoutWithOnlyTheAccessTokenOrOnlyTheRefreshTokenEndsTheSession() throws Exception {
        String email = signupTeacher();
        Tokens a = login(email, PASSWORD);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + a.access)).andExpect(status().isNoContent());
        me(a.access).andExpect(status().isUnauthorized());
        refreshStatus(a.refresh, 401);

        Tokens b = login(email, PASSWORD);
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + b.refresh + "\"}"))
                .andExpect(status().isNoContent());
        me(b.access).andExpect(status().isUnauthorized());
        // 이미 로그아웃한 토큰으로 다시 로그아웃해도 오류 없이 끝난다(멱등).
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + b.access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + b.refresh + "\"}")).andExpect(status().isNoContent());
    }

    /** 세션 클레임이 없는 이전 토큰(이 기능 배포 전 발급)은 로그아웃으로 끊을 수 없고 만료(최대 이전 수명)까지 유효하다. 비밀번호 변경은 막는다. */
    @Test
    void legacyTokensWithoutASessionAreOnlyLimitedByExpiryAndTokenVersion() throws Exception {
        String email = signupTeacher();
        Tokens current = login(email, PASSWORD);
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
        String legacy = io.jsonwebtoken.Jwts.builder().subject(Long.toString(userId)).claim("role", "TEACHER")
                .issuedAt(new java.util.Date()).expiration(new java.util.Date(System.currentTimeMillis() + 3_600_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("test-only-signing-key-must-be-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + current.access)).andExpect(status().isNoContent());
        me(legacy).andExpect(status().isOk());
        Tokens again = login(email, PASSWORD);
        changePassword(again.access, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
        me(legacy).andExpect(status().isUnauthorized());
    }

    // ── 정책 7: 로그인 상태 비밀번호 변경 ─────────────────────

    @Test
    void changingThePasswordValidatesInputAndNeverLogsSecrets(CapturedOutput output) throws Exception {
        String email = signupTeacher();
        Tokens t = login(email, PASSWORD);
        mvc.perform(patch("/api/v1/auth/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(passwordBody(PASSWORD, NEW_PASSWORD, NEW_PASSWORD))).andExpect(status().isUnauthorized());
        changePassword(t.access, "Wrong-Current!1", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않아요. (남은 시도 4회)"));
        changePassword(t.access, PASSWORD, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("현재 비밀번호와 다른 새 비밀번호를 입력해 주세요."));
        changePassword(t.access, PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("새 비밀번호와 확인 값이 일치하지 않아요."));
        changePassword(t.access, PASSWORD, "short", "short").andExpect(status().isBadRequest());
        changePassword(t.access, PASSWORD, "x".repeat(73), "x".repeat(73)).andExpect(status().isBadRequest());
        changePassword(t.access, "", NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        // 실패한 시도는 비밀번호·세션을 바꾸지 않는다(현재 비밀번호가 틀려도 401이 아니라 400: 화면이 로그아웃시키지 않음).
        me(t.access).andExpect(status().isOk());
        login(email, PASSWORD);
        for (String secret : List.of(PASSWORD, NEW_PASSWORD, "Wrong-Current!1", t.access, t.refresh))
            assertFalse(output.getOut().contains(secret), "비밀번호·토큰을 로그에 남기지 않는다");
    }

    @Test
    void changingThePasswordKeepsThisDeviceAndLogsOutEveryOtherDevice(CapturedOutput output) throws Exception {
        String email = signupTeacher();
        Tokens here = login(email, PASSWORD);
        Tokens other = login(email, PASSWORD);

        JsonNode issued = objectMapper.readTree(changePassword(here.access, PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Tokens renewed = new Tokens(issued.path("accessToken").asText(), issued.path("refreshToken").asText());
        assertFalse(renewed.access.isBlank());
        assertFalse(renewed.refresh.isBlank());

        // 이 기기: 새 토큰으로 계속 사용, 이전 토큰은 거절
        me(renewed.access).andExpect(status().isOk());
        me(here.access).andExpect(status().isUnauthorized());
        refreshStatus(here.refresh, 401);
        me(refresh(renewed.refresh).access).andExpect(status().isOk());
        // 다른 기기: access·refresh 모두 거절
        me(other.access).andExpect(status().isUnauthorized());
        refreshStatus(other.refresh, 401);
        // 이전 비밀번호로는 로그인할 수 없고 새 비밀번호로는 된다(BCrypt 해시로 저장).
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email);
        assertTrue(hash.startsWith("$2"), "BCrypt 해시로 저장한다");
        assertFalse(hash.contains(NEW_PASSWORD));
        for (String secret : List.of(PASSWORD, NEW_PASSWORD, renewed.access, renewed.refresh))
            assertFalse(output.getOut().contains(secret), "비밀번호·토큰을 로그에 남기지 않는다");
    }

    // ── 비밀번호 변경 시도 제한 ─────────────────────────

    @Test
    void repeatedWrongCurrentPasswordsAreLimitedWithoutLoggingTheUserOut() throws Exception {
        String email = signupTeacher();
        Tokens t = login(email, PASSWORD);
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
        for (int remaining = 4; remaining >= 1; remaining--)
            changePassword(t.access, "Wrong-Current!1", NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않아요. (남은 시도 " + remaining + "회)"));
        changePassword(t.access, "Wrong-Current!1", NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("15분 뒤에 다시 시도해 주세요")));
        // 막힌 동안에는 맞는 비밀번호도 받지 않는다. 로그인은 유지되고 비밀번호는 바뀌지 않는다.
        changePassword(t.access, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());
        me(t.access).andExpect(status().isOk());
        login(email, PASSWORD);
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM password_change_failures WHERE user_id=?", Integer.class, userId));

        // 제한 기간(15분)이 지나면 다시 시도할 수 있고, 성공하면 실패 기록을 지운다.
        jdbc.update("UPDATE password_change_failures SET created_at = DATE_SUB(created_at, INTERVAL 16 MINUTE) WHERE user_id=?", userId);
        changePassword(t.access, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_change_failures WHERE user_id=?", Integer.class, userId));
    }

    @Test
    void aSuccessfulChangeResetsTheFailureCountAndOtherUsersAreNotAffected() throws Exception {
        String email = signupTeacher();
        String other = signupTeacher();
        Tokens t = login(email, PASSWORD);
        Tokens o = login(other, PASSWORD);
        for (int i = 0; i < 3; i++) changePassword(t.access, "Wrong-Current!1", NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        changePassword(o.access, "Wrong-Current!1", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않아요. (남은 시도 4회)"));
        JsonNode issued = objectMapper.readTree(changePassword(t.access, PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        changePassword(issued.path("accessToken").asText(), "Wrong-Current!1", "Another-Pass!1", "Another-Pass!1")
                .andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않아요. (남은 시도 4회)"));
    }

    // ── 정책 6: access token 기본 수명 ──────────────────────

    /** 발급되는 access token 수명은 설정값(jwt.expiration ← JWT_EXPIRATION_MS, 없으면 기본 30분)을 따른다. */
    @Test
    void accessTokensUseTheConfiguredLifetimeAndCarryTheSession() throws Exception {
        Tokens t = login(signupTeacher(), PASSWORD);
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(t.access.split("\\.")[1]));
        assertEquals(environment.getRequiredProperty("jwt.expiration", Long.class) / 1000, claims.path("exp").asLong() - claims.path("iat").asLong());
        assertTrue(claims.hasNonNull("sid"), "access token은 로그인 세션 ID를 담는다");
    }

    @Test
    void theDefaultAccessTokenLifetimeIsThirtyMinutes() throws Exception {
        String properties = new String(getClass().getResourceAsStream("/application.properties").readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(properties.contains("jwt.expiration=${JWT_EXPIRATION_MS:1800000}"), "환경변수가 없을 때 기본값은 30분(1800000ms)");
    }

    // ── helpers ─────────────────────────────────────────

    private record Tokens(String access, String refresh) { }

    private ResultActions me(String access) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access));
    }

    private ResultActions changePassword(String access, String current, String next, String confirm) throws Exception {
        return mvc.perform(patch("/api/v1/auth/me/password").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody(current, next, confirm)));
    }

    private String passwordBody(String current, String next, String confirm) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\",\"newPasswordConfirm\":\"" + confirm + "\"}";
    }

    private Tokens refresh(String refreshToken) throws Exception {
        JsonNode body = objectMapper.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Tokens(body.path("accessToken").asText(), body.path("refreshToken").asText());
    }

    private void refreshStatus(String refreshToken, int expected) throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}")).andExpect(status().is(expected));
    }

    private Tokens login(String email, String password) throws Exception {
        JsonNode body = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Tokens(body.path("accessToken").asText(), body.path("refreshToken").asText());
    }

    private String signupTeacher() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"세션 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return email;
    }
}
