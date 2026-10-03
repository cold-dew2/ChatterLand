package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** access token 수명은 JWT_EXPIRATION_MS(jwt.expiration)로 설정한다. 만료되면 401이고, refresh token으로 새 토큰을 받아 계속 쓴다. */
@SpringBootTest(properties = {"jwt.secret=test-only-signing-key-must-be-at-least-32-bytes", "jwt.expiration=1500"})
@AutoConfigureMockMvc
@Transactional
class AccessTokenLifetimeIntegrationTest {
    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void anExpiredAccessTokenIsRejectedAndTheRefreshTokenIssuesANewOne() throws Exception {
        String email = "ttl-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TEACHER\",\"name\":\"수명 선생님\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,"
                        + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}")).andExpect(status().isCreated());
        JsonNode login = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}")).andReturn().getResponse().getContentAsString());
        String access = login.path("accessToken").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());

        Thread.sleep(2100); // JWT exp는 초 단위
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        JsonNode renewed = objectMapper.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + login.path("refreshToken").asText() + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + renewed.path("accessToken").asText())).andExpect(status().isOk());
    }
}
