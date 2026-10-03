package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.AfterEach;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SMTP 서버에 연결할 수 없을 때: 503 MAIL_DELIVERY_FAILED, 인증 요청은 서비스 트랜잭션 롤백으로 남지 않아 바로 다시 시도할 수 있다.
 * 롤백을 실제로 확인하려면 테스트 트랜잭션으로 감싸면 안 되므로(@Transactional 없음) 만든 계정을 직접 지운다.
 */
@SpringBootTest(properties = {"jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "spring.mail.host=127.0.0.1", "spring.mail.port=1", "app.mail.from=no-reply@chatterland.test",
        "spring.mail.properties.mail.smtp.connectiontimeout=2000", "spring.mail.properties.mail.smtp.timeout=2000"})
@AutoConfigureMockMvc
class MailDeliveryFailureIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private String createdEmail;

    @AfterEach
    void cleanUp() {
        if (createdEmail == null) return;
        jdbc.update("DELETE FROM password_reset_requests WHERE user_id IN (SELECT user_id FROM users WHERE email=?)", createdEmail);
        jdbc.update("DELETE FROM users WHERE email=?", createdEmail);
    }

    @Test
    void unreachableSmtpReturnsDeliveryFailedAndLeavesNoPendingCode() throws Exception {
        String email = "smtpdown-" + UUID.randomUUID() + "@example.test";
        createdEmail = email;
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TEACHER\",\"name\":\"메일실패\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
        for (int attempt = 0; attempt < 2; attempt++) { // 두 번째도 재발송 제한(1분)에 걸리지 않고 실제로 다시 시도한다
            mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("MAIL_DELIVERY_FAILED"))
                    .andExpect(jsonPath("$.message").value("인증 메일을 보내지 못했어요. 잠시 뒤 다시 시도해 주세요."));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_requests WHERE user_id=?", Integer.class, userId));
    }
}
