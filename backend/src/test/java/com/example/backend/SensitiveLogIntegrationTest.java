package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 로그 출력을 캡처해 비밀번호·JWT·refresh 토큰·이메일·인증 코드가 남지 않는지 확인한다.
 * 예상하지 못한 서버 오류 경로는 민감 정보를 담은 예외를 던지는 테스트 전용 컨트롤러로 재현한다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
@Import(SensitiveLogIntegrationTest.LeakyController.class)
class SensitiveLogIntegrationTest {
    private static final String PASSWORD = "Leak-Check-Pw!789";
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestConfiguration
    @RestController
    static class LeakyController {
        @GetMapping("/api/v1/test-only/boom")
        public String boom() {
            throw new IllegalStateException("Duplicate entry 'leaky.parent@example.test' for key 'uq_users_email' password=" + PASSWORD
                    + " Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5OSJ9.bGVha3k code=135790 phone 010-9876-5432");
        }
    }

    @Test
    void authenticationFlowsAndUnexpectedErrorsDoNotLeakSecretsToLogs(CapturedOutput output) throws Exception {
        String email = "log-check-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"로그 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":false,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"김보호\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        JsonNode login = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String access = login.path("accessToken").asText();
        String refresh = login.path("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Wrong-" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());
        // 검증 실패(필드 이름만 로그) — 비밀번호 값은 남기지 않아야 한다.
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STUDENT\",\"name\":\"\",\"email\":\"bad\",\"password\":\"" + PASSWORD + "-but-invalid-request\",\"centerId\":1,\"termsAgreed\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer " + access + "tampered")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/test-only/boom").header("Authorization", "Bearer " + access))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));

        String logs = output.getAll();
        assertTrue(logs.contains("Unexpected Exception java.lang.IllegalStateException"), "오류 원인(예외 유형)은 로그에 남아야 한다");
        assertTrue(logs.contains("uq_users_email"), "원인 파악에 필요한 제약 이름은 남아야 한다");
        assertTrue(logs.contains("Request validation failed for fields"), "검증 실패는 필드 이름으로 기록된다");
        for (String secret : new String[]{PASSWORD, access, refresh, email, "leaky.parent@example.test", "135790", "010-9876-5432",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5OSJ9.bGVha3k", "김보호"}) {
            assertFalse(logs.contains(secret), "로그에 민감 정보가 남음: " + secret.substring(0, Math.min(6, secret.length())) + "…");
        }
    }

    @Test
    void noGeneratedSecurityPasswordUserExists() {
        // Spring Boot 기본 사용자(시작 로그에 "Using generated security password" 출력)를 만들지 않는다.
        assertTrue(context.getBeansOfType(InMemoryUserDetailsManager.class).isEmpty());
        assertEquals(1, context.getBeansOfType(UserDetailsService.class).size());
    }
}
