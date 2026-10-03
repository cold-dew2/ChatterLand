package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MAIL_HOST가 빈 환경(.env에 MAIL_FROM만 있는 경우 포함): 서버는 정상 기동하고, 재설정 요청은 503 MAIL_NOT_CONFIGURED(가입 여부와 무관하게 동일).
 * Spring Boot는 빈 MAIL_HOST로도 JavaMailSender를 만들기 때문에, 발송기 존재가 아니라 설정값으로 판단해야 한다(수정 전에는 localhost:587로 발송을 시도했다).
 */
@SpringBootTest(properties = {"jwt.secret=test-only-signing-key-must-be-at-least-32-bytes", "spring.mail.host=", "app.mail.from=no-reply@chatterland.test"})
@AutoConfigureMockMvc
@Transactional
class MailNotConfiguredIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectProvider<JavaMailSender> mailSender;

    @Test
    void serverStartsWithoutMailAndResetReturnsMailNotConfigured() throws Exception {
        assertNotNull(mailSender.getIfAvailable(), "Spring Boot는 빈 MAIL_HOST로도 발송기를 만든다(그래서 설정값으로 판단해야 한다)");
        String email = "nomail-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TEACHER\",\"name\":\"메일없음\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_requests", Integer.class);
        for (String target : new String[]{email, "unknown-" + UUID.randomUUID() + "@example.test"}) {
            mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + target + "\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"))
                    .andExpect(jsonPath("$.message").value("메일 발송 설정(MAIL_HOST, MAIL_FROM)이 필요합니다. 센터 관리자에게 문의해 주세요."));
        }
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_requests", Integer.class));
    }
}
