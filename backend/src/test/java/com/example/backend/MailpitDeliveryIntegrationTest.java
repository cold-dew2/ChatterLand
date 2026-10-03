package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.backend.support.TestPrerequisites.requireMailpit;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mailpit(로컬 테스트용 SMTP, 127.0.0.1:1025 / API 8025)으로 실제 SMTP 발송을 확인한다. 실제 메일 서버는 쓰지 않는다.
 * Mailpit이 실행 중이 아니면 SKIPPED로 남는다(성공으로 처리하지 않음). CI 스크립트는 Mailpit을 먼저 띄운다.
 */
@SpringBootTest(properties = {"jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "spring.mail.host=127.0.0.1", "spring.mail.port=1025", "app.mail.from=no-reply@chatterland.test"})
@AutoConfigureMockMvc
@Transactional
class MailpitDeliveryIntegrationTest {
    @Autowired MockMvc mvc;

    @BeforeAll
    static void checkMailpit() {
        boolean reachable;
        try (Socket smtp = new Socket("127.0.0.1", 1025); Socket api = new Socket("127.0.0.1", 8025)) { reachable = smtp.isConnected() && api.isConnected(); }
        catch (Exception e) { reachable = false; }
        requireMailpit(reachable, "Mailpit(127.0.0.1:1025/8025)이 실행 중이 아니어서 건너뜁니다.");
    }

    @Test
    void resetCodeIsDeliveredThroughSmtp() throws Exception {
        String email = "mailpit-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TEACHER\",\"name\":\"메일성공\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        HttpClient http = HttpClient.newHttpClient();
        ObjectMapper json = new ObjectMapper();
        String search = "http://127.0.0.1:8025/api/v1/search?query=" + URLEncoder.encode("to:" + email, StandardCharsets.UTF_8);
        JsonNode found = null;
        for (int i = 0; i < 20 && (found == null || found.path("messages").isEmpty()); i++) {
            found = json.readTree(http.send(HttpRequest.newBuilder(URI.create(search)).build(), HttpResponse.BodyHandlers.ofString()).body());
            if (found.path("messages").isEmpty()) Thread.sleep(250);
        }
        assertNotNull(found);
        assertEquals(1, found.path("messages").size(), "재설정 메일이 정확히 한 통 도착해야 한다");
        String id = found.path("messages").get(0).path("ID").asText();
        JsonNode message = json.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8025/api/v1/message/" + id)).build(), HttpResponse.BodyHandlers.ofString()).body());
        assertEquals("[채터랜드] 비밀번호 재설정 인증 코드", message.path("Subject").asText());
        Matcher code = Pattern.compile("인증 코드: (\\d{6})").matcher(message.path("Text").asText());
        assertTrue(code.find(), "메일 본문에 6자리 인증 코드가 있어야 한다");
    }
}
