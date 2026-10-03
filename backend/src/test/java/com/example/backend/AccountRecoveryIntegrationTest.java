package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 아이디 찾기와 이메일 인증 코드 기반 비밀번호 재설정. 메일은 테스트용 인메모리 SMTP(GreenMail)로 실제 발송·수신한다. */
@SpringBootTest(properties = {
        "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "spring.mail.host=localhost", "spring.mail.port=3025", "app.mail.from=no-reply@chatterland.test"
})
@AutoConfigureMockMvc
@Transactional
class AccountRecoveryIntegrationTest {
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP).withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    private static final String CONSENTS = "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String signupTeacher(String name) throws Exception {
        String email = "recovery-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":2,\"termsAgreed\":true," + CONSENTS + "}"))
                .andExpect(status().isCreated());
        return email;
    }

    private String json(Object... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) out.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        return out.append('}').toString();
    }

    private String latestCode() throws Exception {
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertTrue(messages.length > 0, "인증 메일이 발송되어야 합니다.");
        Matcher matcher = Pattern.compile("인증 코드: (\\d{6})").matcher(GreenMailUtil.getBody(messages[messages.length - 1]));
        if (!matcher.find()) {
            String body = new String(jakarta.mail.internet.MimeUtility.decode(messages[messages.length - 1].getInputStream(), "quoted-printable").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            matcher = Pattern.compile("인증 코드: (\\d{6})").matcher(body);
            assertTrue(matcher.find(), "메일 본문에 6자리 인증 코드가 있어야 합니다.");
        }
        return matcher.group(1);
    }

    @Test
    void findIdReturnsOnlyMaskedEmailsAndRejectsMismatchOrInvalidCenter() throws Exception {
        String name = "아이디찾기" + UUID.randomUUID().toString().substring(0, 6);
        String email = signupTeacher(name);
        String body = mvc.perform(post("/api/v1/auth/find-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"centerId\":2,\"role\":\"TEACHER\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String masked = objectMapper.readTree(body).path("maskedEmails").get(0).asText();
        assertNotEquals(email, masked);
        assertTrue(masked.startsWith(email.substring(0, 2)) && masked.contains("*") && masked.endsWith(".test"));

        mvc.perform(post("/api/v1/auth/find-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"centerId\":1,\"role\":\"TEACHER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/find-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"centerId\":999999,\"role\":\"TEACHER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/find-id").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"centerId\":2,\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetFullFlowWithSingleUseCodeAndTokenRevokesSessions() throws Exception {
        String email = signupTeacher("재설정 치료사");
        String loginBody = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "password", "Chatterland!234")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oldRefresh = objectMapper.readTree(loginBody).path("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json("email", email)))
                .andExpect(status().isAccepted());
        String code = latestCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "code", wrong)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("남은 시도 4회")));
        String verify = mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "code", code)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(900)).andReturn().getResponse().getContentAsString();
        String resetToken = objectMapper.readTree(verify).path("resetToken").asText();
        // 같은 코드는 다시 쓸 수 없다.
        mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "code", code)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json("resetToken", resetToken, "newPassword", "NewPassword!9", "newPasswordConfirm", "Different!9")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json("resetToken", resetToken, "newPassword", "short", "newPasswordConfirm", "short")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json("resetToken", resetToken, "newPassword", "NewPassword!9", "newPasswordConfirm", "NewPassword!9")))
                .andExpect(status().isNoContent());
        // 재설정 토큰도 한 번만 쓸 수 있다.
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json("resetToken", resetToken, "newPassword", "Another!999", "newPasswordConfirm", "Another!999")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "password", "Chatterland!234")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "password", "NewPassword!9")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json("refreshToken", oldRefresh)))
                .andExpect(status().isUnauthorized());
    }

    /** 비밀번호 재설정 뒤에는 이미 발급된 access token(모든 기기)도 만료 시각과 관계없이 즉시 거절된다. */
    @Test
    void passwordResetImmediatelyInvalidatesAccessTokensOnEveryDevice() throws Exception {
        String email = signupTeacher("토큰 무효화 치료사");
        JsonNode phone = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", "Chatterland!234"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode laptop = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", "Chatterland!234"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // 노트북은 재설정 전에 한 번 갱신해 둔다(갱신으로 받은 토큰도 함께 무효화되어야 한다).
        JsonNode refreshed = objectMapper.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", laptop.path("refreshToken").asText()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
        // 이 기능 배포 전에 발급된 토큰(버전 클레임 없음)은 버전 0으로 보고 계속 받아들인다.
        String legacy = io.jsonwebtoken.Jwts.builder().subject(Long.toString(userId)).claim("role", "TEACHER")
                .issuedAt(new java.util.Date()).expiration(new java.util.Date(System.currentTimeMillis() + 3_600_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("test-only-signing-key-must-be-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
        for (String token : java.util.List.of(phone.path("accessToken").asText(), refreshed.path("accessToken").asText(), legacy))
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json("email", email)))
                .andExpect(status().isAccepted());
        String verify = mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "code", latestCode())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json("resetToken", objectMapper.readTree(verify).path("resetToken").asText(), "newPassword", "NewPassword!9", "newPasswordConfirm", "NewPassword!9")))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("SELECT token_version FROM users WHERE user_id=?", Integer.class, userId));

        // 기존 access token은 모두 401(아직 만료 전이어도), 기존 refresh token으로 새 access token을 받을 수도 없다.
        for (String token : java.util.List.of(phone.path("accessToken").asText(), refreshed.path("accessToken").asText(), legacy)) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mvc.perform(get("/api/v1/teachers/me/students").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        for (String refresh : java.util.List.of(phone.path("refreshToken").asText(), refreshed.path("refreshToken").asText()))
            mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json("refreshToken", refresh)))
                    .andExpect(status().isUnauthorized());
        // 무효화된 토큰이 붙어 있어도 공개 API(로그인)는 막히지 않는다. 새 비밀번호로 다시 로그인한 세션은 정상 동작한다.
        JsonNode again = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer " + legacy)
                .contentType(MediaType.APPLICATION_JSON).content(json("email", email, "password", "NewPassword!9")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + again.path("accessToken").asText())).andExpect(status().isOk());
        JsonNode rotated = objectMapper.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", again.path("refreshToken").asText()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rotated.path("accessToken").asText())).andExpect(status().isOk());
    }

    @Test
    void expiredCodeAndTooManyAttemptsAreRejected() throws Exception {
        String email = signupTeacher("만료 치료사");
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json("email", email)))
                .andExpect(status().isAccepted());
        String code = latestCode();
        jdbc.update("UPDATE password_reset_requests SET code_expires_at=DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) WHERE user_id=(SELECT user_id FROM users WHERE email=?)", email);
        mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "code", code)))
                .andExpect(status().isBadRequest());

        // 재발급 제한(1분)을 우회해 새 요청을 만들고 시도 횟수 초과를 확인한다.
        jdbc.update("UPDATE password_reset_requests SET created_at=DATE_SUB(created_at, INTERVAL 2 MINUTE) WHERE user_id=(SELECT user_id FROM users WHERE email=?)", email);
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json("email", email)))
                .andExpect(status().isAccepted());
        String fresh = latestCode();
        String wrong = fresh.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++)
            mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "code", wrong)))
                    .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "code", wrong)))
                .andExpect(status().isTooManyRequests());
        // 시도 초과 후에는 올바른 코드도 거부된다.
        mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON).content(json("email", email, "code", fresh)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEmailAndResendCooldownGiveSameResponseWithoutExtraMail() throws Exception {
        int before = greenMail.getReceivedMessages().length;
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json("email", "nobody-" + UUID.randomUUID() + "@example.test")))
                .andExpect(status().isAccepted());
        assertEquals(before, greenMail.getReceivedMessages().length);

        String email = signupTeacher("재발급 치료사");
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json("email", email))).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json("email", email))).andExpect(status().isAccepted());
        assertEquals(before + 1, greenMail.getReceivedMessages().length);
        JsonNode count = objectMapper.valueToTree(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_requests WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Integer.class, email));
        assertEquals(1, count.asInt());
    }
}
