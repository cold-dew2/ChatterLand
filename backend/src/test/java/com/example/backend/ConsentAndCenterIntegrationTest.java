package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 센터 목록 API·서버 검증, 회원가입 동의와 동의 기반 기능 차단·철회 */
@SpringBootTest(properties = {
        "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "app.audio.storage-path=${java.io.tmpdir}/chatterland-test-audio",
        "app.ai.endpoint=", "app.ai.api-key="
})
@AutoConfigureMockMvc
@Transactional
class ConsentAndCenterIntegrationTest {
    private static final String VERSION = "2026-10-01";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String studentBody(String email, long centerId, int age, String consents) {
        return "{\"role\":\"STUDENT\",\"name\":\"동의 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":" + centerId
                + ",\"termsAgreed\":true,\"age\":" + age + (consents == null ? "" : ",\"consents\":" + consents) + "}";
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).path("accessToken").asText();
    }

    private String email() { return "consent-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test"; }

    @Test
    void centersApiListsOnlyActiveCentersAndSignupValidatesCenter() throws Exception {
        // 테스트 전용 센터는 트랜잭션 안에서만 만들고 롤백되므로 운영 데이터에 남지 않는다.
        jdbc.update("INSERT INTO centers(name,active) VALUES('테스트 비활성 센터',FALSE)");
        long inactiveId = jdbc.queryForObject("SELECT MAX(center_id) FROM centers", Long.class);
        String body = mvc.perform(get("/api/v1/centers")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode centers = objectMapper.readTree(body);
        assertTrue(centers.size() >= 1);
        centers.forEach(center -> assertNotEquals(inactiveId, center.path("centerId").asLong()));
        assertTrue(centers.get(0).has("name"));

        String consents = "{\"privacy\":true,\"policyVersion\":\"" + VERSION + "\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(studentBody(email(), inactiveId, 8, consents)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(studentBody(email(), 999999, 8, consents)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupRequiresPrivacyConsentCurrentVersionAndGuardianForChildren() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(studentBody(email(), 1, 8, null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(email(), 1, 8, "{\"privacy\":true,\"policyVersion\":\"" + VERSION + "\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("법정대리인")));
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(email(), 1, 8, "{\"privacy\":true,\"policyVersion\":\"2000-01-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}")))
                .andExpect(status().isBadRequest());
        // 만 14세 이상 학생은 법정대리인 확인 없이 가입할 수 있다.
        String teenEmail = email();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(teenEmail, 1, 15, "{\"privacy\":true,\"voice\":false,\"policyVersion\":\"" + VERSION + "\"}")))
                .andExpect(status().isCreated());

        String childEmail = email();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(childEmail, 1, 8, "{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"" + VERSION + "\",\"guardianConfirmed\":true,\"guardianName\":\"김보호\",\"guardianRelation\":\"부모\"}")))
                .andExpect(status().isCreated());
        String token = login(childEmail);
        String consents = mvc.perform(get("/api/v1/consents/me").header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(consents);
        assertEquals(5, list.size());
        for (JsonNode consent : list) {
            assertEquals(VERSION, consent.path("policyVersion").asText());
            switch (consent.path("type").asText()) {
                case "PRIVACY", "GUARDIAN", "VOICE" -> { assertTrue(consent.path("agreed").asBoolean()); assertFalse(consent.path("agreedAt").isNull()); }
                case "AI_CHAT", "AI_FEEDBACK" -> assertFalse(consent.path("agreed").asBoolean());
                default -> fail("알 수 없는 동의 항목");
            }
        }
        assertEquals("김보호", list.get(1).path("guardianName").asText());
    }

    @Test
    void speechAndAiChatAreBlockedOnServerWithoutConsentAndWithdrawalDeletesAudio() throws Exception {
        String studentEmail = email();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(studentEmail, 1, 8, "{\"privacy\":true,\"voice\":false,\"aiChat\":false,\"policyVersion\":\"" + VERSION + "\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}")))
                .andExpect(status().isCreated());
        String token = login(studentEmail);
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, studentEmail);

        MockMultipartFile audio = new MockMultipartFile("audio", "speech.wav", "audio/wav", Files.readAllBytes(Path.of(getClass().getResource("/speech/word-radio.wav").toURI())));
        mvc.perform(multipart("/api/v1/speech/analyze").file(audio).part(new MockPart("exerciseId", "1".getBytes(StandardCharsets.UTF_8)))
                        .part(new MockPart("itemId", "1".getBytes(StandardCharsets.UTF_8))).header("Authorization", token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(post("/api/v1/ai/conversations").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"topic\":\"학교 이야기\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        // 법정대리인 확인 없이 음성 동의를 켤 수 없다.
        mvc.perform(put("/api/v1/consents/me/VOICE").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true,\"policyVersion\":\"" + VERSION + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/consents/me/AI_CHAT").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true,\"policyVersion\":\"" + VERSION + "\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.agreed").value(true));
        // AI 동의 후에도 외부 AI 설정이 없으면 가짜 응답 없이 503을 준다.
        mvc.perform(post("/api/v1/ai/conversations").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"topic\":\"학교 이야기\"}"))
                .andExpect(status().isServiceUnavailable());

        mvc.perform(put("/api/v1/consents/me/VOICE").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true,\"policyVersion\":\"" + VERSION + "\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}"))
                .andExpect(status().isOk());

        // 보관 중인 녹음(선생님 검토용)을 만든 뒤 음성 동의를 철회하면 파일이 즉시 삭제된다.
        Path storage = Path.of(System.getProperty("java.io.tmpdir"), "chatterland-test-audio");
        Files.createDirectories(storage);
        Path file = Files.writeString(storage.resolve(UUID.randomUUID() + ".wav"), "audio");
        String analysisId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_path,audio_mime,status,audio_expires_at) VALUES(?,?,1,'1',?,'audio/wav','COMPLETED',DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 6 MONTH))",
                analysisId, studentId, file.toAbsolutePath().toString());
        mvc.perform(put("/api/v1/consents/me/VOICE").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":false,\"policyVersion\":\"" + VERSION + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.agreed").value(false)).andExpect(jsonPath("$.withdrawnAt").isNotEmpty());
        assertFalse(Files.exists(file));
        assertNull(jdbc.queryForObject("SELECT audio_path FROM speech_analyses WHERE analysis_id=?", String.class, analysisId));
        mvc.perform(multipart("/api/v1/speech/analyze").file(audio).part(new MockPart("exerciseId", "1".getBytes(StandardCharsets.UTF_8)))
                        .part(new MockPart("itemId", "1".getBytes(StandardCharsets.UTF_8))).header("Authorization", token))
                .andExpect(status().isForbidden());

        // 필수 동의는 화면에서 철회할 수 없다.
        mvc.perform(put("/api/v1/consents/me/PRIVACY").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":false,\"policyVersion\":\"" + VERSION + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consentRecordsAreOnlyVisibleToTheirOwner() throws Exception {
        String teacherEmail = email();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"동의 치료사\",\"email\":\"" + teacherEmail + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"" + VERSION + "\"}}"))
                .andExpect(status().isCreated());
        String body = mvc.perform(get("/api/v1/consents/me").header("Authorization", login(teacherEmail)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(body);
        // 선생님은 본인 동의(개인정보)만 보고, 학생용 항목이나 다른 사용자의 기록은 볼 수 없다.
        assertEquals(1, list.size());
        assertEquals("PRIVACY", list.get(0).path("type").asText());
        mvc.perform(put("/api/v1/consents/me/VOICE").header("Authorization", login(teacherEmail)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true,\"policyVersion\":\"" + VERSION + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/consents/me")).andExpect(status().isUnauthorized());
    }
}
