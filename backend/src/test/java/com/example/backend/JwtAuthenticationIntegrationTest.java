package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 보호 API의 JWT 인증: 만료·변조·다른 키·누락 토큰은 기존 규약(401 UNAUTHORIZED)으로 거부하고, 유효 토큰과 재발급 흐름은 유지된다. */
@SpringBootTest(properties = "jwt.secret=" + JwtAuthenticationIntegrationTest.SECRET)
@AutoConfigureMockMvc
@Transactional
class JwtAuthenticationIntegrationTest {
    static final String SECRET = "test-only-signing-key-must-be-at-least-32-bytes";
    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String token(long userId, String role, Instant issuedAt, Instant expiresAt, String secret) {
        return Jwts.builder().subject(Long.toString(userId)).claim("role", role).issuedAt(Date.from(issuedAt)).expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void expiredTamperedForeignAndMissingTokensGet401WhileValidTokenWorks() throws Exception {
        JsonNode login = signupAndLogin();
        long userId = login.path("user").path("userId").asLong();
        String valid = login.path("accessToken").asText();
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer " + valid)).andExpect(status().isOk());

        Instant past = Instant.now().minusSeconds(7200);
        String expired = token(userId, "STUDENT", past, past.plusSeconds(60), SECRET);
        String foreign = token(userId, "STUDENT", Instant.now(), Instant.now().plusSeconds(600), "another-signing-key-that-is-at-least-32-bytes");
        String[] parts = valid.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).replace("STUDENT", "TEACHER");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        for (String bad : new String[]{expired, foreign, tampered, "garbage", ""}) {
            mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer " + bad))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
        }
        // 변조로 역할을 바꿔도 선생님 API에 들어갈 수 없다.
        mvc.perform(get("/api/v1/teachers/me/students").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/students/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        // 만료된 토큰이 붙어 있어도 공개 API(센터 목록)는 그대로 쓸 수 있다.
        mvc.perform(get("/api/v1/centers").header("Authorization", "Bearer " + expired)).andExpect(status().isOk());
    }

    @Test
    void expiredAccessTokenCanBeReplacedThroughRefresh() throws Exception {
        JsonNode login = signupAndLogin();
        long userId = login.path("user").path("userId").asLong();
        Instant past = Instant.now().minusSeconds(7200);
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer " + token(userId, "STUDENT", past, past.plusSeconds(60), SECRET)))
                .andExpect(status().isUnauthorized());
        String refreshed = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.path("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newAccess = objectMapper.readTree(refreshed).path("accessToken").asText();
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer " + newAccess)).andExpect(status().isOk());
    }

    private JsonNode signupAndLogin() throws Exception {
        String email = "jwt-student-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"토큰 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":false,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        return objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
