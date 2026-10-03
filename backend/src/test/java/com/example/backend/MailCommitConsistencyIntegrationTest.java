package com.example.backend;

import com.example.backend.chld.exception.MailUnavailableException;
import com.example.backend.chld.service.MailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호 재설정 요청에서 메일 발송과 DB 커밋이 어긋나는 경우의 데이터 일관성.
 * 메일 서비스는 모의 객체다(실제 SMTP 검증 아님 — 실제 발송은 MailpitDeliveryIntegrationTest).
 * 서비스 트랜잭션의 실제 커밋·롤백을 보려고 테스트 트랜잭션 없이 실행하고 만든 계정을 지운다.
 */
@SpringBootTest(properties = {"jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "spring.mail.host=127.0.0.1", "app.mail.from=no-reply@chatterland.test"})
@AutoConfigureMockMvc
class MailCommitConsistencyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MailService mail;
    private final List<String> sentCodes = new ArrayList<>();
    private String email;

    @AfterEach
    void cleanUp() {
        if (email == null) return;
        jdbc.update("DELETE FROM password_reset_requests WHERE user_id IN (SELECT user_id FROM users WHERE email=?)", email);
        jdbc.update("DELETE FROM users WHERE email=?", email);
    }

    @Test
    void mailFailureAndCommitFailureAfterSendingNeverInvalidateTheLastWorkingCode() throws Exception {
        email = "mail-commit-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TEACHER\",\"name\":\"커밋검증\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);

        // 1) 정상 발송: 첫 번째 코드
        doAnswer(call -> { sentCodes.add(call.getArgument(1)); return null; }).when(mail).sendPasswordResetCode(eq(email), anyString(), anyInt());
        request().andExpect(status().isAccepted());
        String firstCode = sentCodes.get(0);
        allowNextRequest(userId);

        // 2) 발송 실패: 새 코드 저장·기존 코드 무효화가 모두 롤백되어야 한다
        doThrow(new MailUnavailableException(MailUnavailableException.DELIVERY_FAILED, "인증 메일을 보내지 못했어요. 잠시 뒤 다시 시도해 주세요."))
                .when(mail).sendPasswordResetCode(eq(email), anyString(), anyInt());
        request().andExpect(status().isServiceUnavailable());
        assertEquals(1, requests(userId));

        // 3) 발송 성공 직후 커밋 실패(트랜잭션 커밋 직전 오류로 재현): 메일로 나간 코드는 DB에 없고, 기존 코드는 그대로 유효
        doAnswer(call -> {
            sentCodes.add(call.getArgument(1));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("simulated commit failure"); }
            });
            return null;
        }).when(mail).sendPasswordResetCode(eq(email), anyString(), anyInt());
        request().andExpect(status().isInternalServerError());
        String orphanCode = sentCodes.get(1);
        assertEquals(1, requests(userId), "커밋 실패한 요청은 저장되지 않는다");
        // 메일로 나간(커밋되지 않은) 코드는 쓸 수 없고, 실패들 이전에 받은 첫 코드는 여전히 쓸 수 있다
        if (!orphanCode.equals(firstCode)) verify(orphanCode).andExpect(status().isBadRequest());
        verify(firstCode).andExpect(status().isOk());

        // 커밋 실패한 요청은 재발송 제한에 포함되지 않아 바로 다시 요청할 수 있다
        doAnswer(call -> { sentCodes.add(call.getArgument(1)); return null; }).when(mail).sendPasswordResetCode(eq(email), anyString(), anyInt());
        int before = sentCodes.size();
        request().andExpect(status().isAccepted());
        assertEquals(before + 1, sentCodes.size(), "바로 다시 요청하면 실제로 새 코드를 보낸다");
        String latestCode = sentCodes.get(sentCodes.size() - 1);

        verify(latestCode).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions request() throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions verify(String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    /** 재발송 제한(1분)을 넘긴 것처럼 기존 요청 시각을 앞당긴다. */
    private void allowNextRequest(long userId) {
        jdbc.update("UPDATE password_reset_requests SET created_at=DATE_SUB(created_at, INTERVAL 2 MINUTE) WHERE user_id=?", userId);
    }

    private int requests(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_requests WHERE user_id=?", Integer.class, userId);
    }
}
