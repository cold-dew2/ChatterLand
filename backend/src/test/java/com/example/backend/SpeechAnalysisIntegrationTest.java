package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import java.nio.charset.StandardCharsets;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 녹음 업로드 → 로컬 whisper.cpp 추론 → 목표 문장 비교 → DB 저장 → 조회/연습 기록/선생님 검토까지 실제로 실행한다.
 * 일반 아동(문장 일치도)과 언어재활 아동(발음 미평가 + 선생님 검토)을 분리해 검증한다.
 * 입력 음성은 macOS TTS 합성음이며 아동 음성 성능을 대표하지 않는다.
 */
@SpringBootTest(properties = {
        "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "app.speech.engine=local",
        "app.audio.storage-path=${java.io.tmpdir}/chatterland-test-audio"
})
@AutoConfigureMockMvc
@Transactional
class SpeechAnalysisIntegrationTest {
    private static final String MODEL = System.getenv().getOrDefault("WHISPER_MODEL_PATH",
            System.getProperty("user.home") + "/.cache/chatterland/whisper/ggml-large-v3-turbo-q5_0.bin");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void requireLocalModel() {
        assumeTrue(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 음성 통합 테스트를 건너뜁니다.");
    }

    @Test
    void generalLearnerGetsSentenceMatchWithoutPronunciationScore() throws Exception {
        Student student = signupStudent();
        long exerciseId = 4; // 천천히 말하기 (fluency)
        long itemId = itemId(exerciseId, "오늘은 날씨가 좋아요.");

        String analyze = mvc.perform(multipart("/api/v1/speech/analyze").file(audio("sentence-weather.wav"))
                        .part(part("exerciseId", String.valueOf(exerciseId))).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        String analysisId = objectMapper.readTree(analyze).path("analysisId").asText();

        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationMode").value("SENTENCE_MATCH"))
                .andExpect(jsonPath("$.targetText").value("오늘은 날씨가 좋아요."))
                .andExpect(jsonPath("$.matchRate").value(100.0))
                .andExpect(jsonPath("$.comparison.words[0].type").value("MATCH"))
                .andExpect(jsonPath("$.pronunciationScore").doesNotExist())
                .andExpect(jsonPath("$.speechRateScore").doesNotExist())
                .andExpect(jsonPath("$.fluencyScore").doesNotExist())
                .andExpect(jsonPath("$.overallScore").doesNotExist())
                .andExpect(jsonPath("$.pronunciationStatus").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.reviewStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.modelName").value(Path.of(MODEL).getFileName().toString()));
        // 일반 연습 녹음은 인식 직후 삭제된다.
        assertNull(jdbc.queryForObject("SELECT audio_path FROM speech_analyses WHERE analysis_id=?", String.class, analysisId));

        String attemptBody = "{\"exerciseId\":\"" + exerciseId + "\",\"itemId\":\"" + itemId + "\",\"audioId\":\"" + analysisId + "\"}";
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token))
                        .contentType(MediaType.APPLICATION_JSON).content(attemptBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").doesNotExist()).andExpect(jsonPath("$.matchRate").value(100.0));
        // 측정하지 않은 점수를 끼워 넣으면 거부한다.
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token))
                        .contentType(MediaType.APPLICATION_JSON).content(attemptBody.replace("{", "{\"score\":100,")))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAttempts").value(1))
                .andExpect(jsonPath("$.averageMatchRate").value(100)).andExpect(jsonPath("$.overallScore").doesNotExist());
        mvc.perform(get("/api/v1/students/me/history").param("type", "word").header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].matchRate").value(100.0));
    }

    @Test
    void therapyLearnerIsNotScoredAndRequiresTeacherReview() throws Exception {
        Student student = signupStudent();
        String teacherEmail = "speech-teacher-" + UUID.randomUUID() + "@example.test";
        signupTeacher(teacherEmail);
        String teacherToken = login(teacherEmail);
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + student.studentId + ",\"name\":\"음성 학생\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.learnerType").value("GENERAL"));
        mvc.perform(put("/api/v1/teachers/me/students/{id}", student.studentId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"음성 학생\",\"age\":8,\"learnerType\":\"THERAPY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.learnerType").value("THERAPY"));

        long exerciseId = 1; // ㄹ 발음
        long itemId = itemId(exerciseId, "라디오");
        String analyze = mvc.perform(multipart("/api/v1/speech/analyze").file(audio("word-radio.wav"))
                        .part(part("exerciseId", String.valueOf(exerciseId))).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String analysisId = objectMapper.readTree(analyze).path("analysisId").asText();

        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationMode").value("PRONUNCIATION_REVIEW"))
                .andExpect(jsonPath("$.transcript").isNotEmpty())
                .andExpect(jsonPath("$.matchRate").doesNotExist())
                .andExpect(jsonPath("$.pronunciationScore").doesNotExist())
                .andExpect(jsonPath("$.pronunciationStatus").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"));

        mvc.perform(get("/api/v1/teachers/me/students/{id}/speech-analyses", student.studentId)
                        .param("reviewStatus", "PENDING").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].analysisId").value(analysisId))
                .andExpect(jsonPath("$.content[0].hasAudio").value(true))
                .andExpect(jsonPath("$.content[0].audioPath").doesNotExist());
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/audio", analysisId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(content().contentType("audio/wav"));
        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", student.studentId)
                        .param("startDate", java.time.LocalDate.now().minusDays(1).toString()).param("endDate", java.time.LocalDate.now().toString())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingReviews").value(1));

        // 다른 선생님은 녹음과 분석 결과에 접근할 수 없다.
        String otherEmail = "speech-other-" + UUID.randomUUID() + "@example.test";
        signupTeacher(otherEmail);
        String otherToken = login(otherEmail);
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/audio", analysisId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"ACCEPTABLE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/teachers/me/students/{id}/speech-analyses", student.studentId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"NEEDS_PRACTICE\",\"note\":\"ㄹ 받침 연습 필요\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.teacherJudgement").value("NEEDS_PRACTICE"))
                .andExpect(jsonPath("$.pronunciationStatus").value("NOT_EVALUATED"));

        // PDF 리포트에 한글 이름·AI 인식 결과·선생님 판단과 메모, '미평가'가 실제 데이터로 들어간다.
        byte[] pdf = mvc.perform(get("/api/v1/teachers/me/students/{id}/report/download", student.studentId)
                        .param("startDate", java.time.LocalDate.now().minusDays(1).toString()).param("endDate", java.time.LocalDate.now().toString())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("음성 학생"), text);
            assertTrue(text.contains("언어재활 아동"));
            assertTrue(text.replaceAll("\\s+", "").contains("연습필요/ㄹ받침연습필요"), text);
            assertTrue(text.contains("미평가"));
            assertTrue(text.contains("라디오"));
        }
    }

    @Test
    void silentOrInvalidRecordingFailsWithoutStoringResult() throws Exception {
        Student student = signupStudent();
        long itemId = itemId(1, "라디오");
        mvc.perform(multipart("/api/v1/speech/analyze").file(audio("silence.wav"))
                        .part(part("exerciseId", "1")).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isUnprocessableContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='FAILED' AND audio_path IS NULL", Integer.class, student.studentId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='COMPLETED'", Integer.class, student.studentId));

        MockMultipartFile empty = new MockMultipartFile("audio", "speech.wav", "audio/wav", new byte[0]);
        mvc.perform(multipart("/api/v1/speech/analyze").file(empty).part(part("exerciseId", "1")).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isBadRequest());
        MockMultipartFile fake = new MockMultipartFile("audio", "speech.wav", "audio/wav", "not audio".getBytes());
        mvc.perform(multipart("/api/v1/speech/analyze").file(fake).part(part("exerciseId", "1")).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isUnsupportedMediaType());
    }

    private MockMultipartFile audio(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(getClass().getResource("/speech/" + name).toURI()));
        return new MockMultipartFile("audio", name, "audio/wav", bytes);
    }

    private MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private long itemId(long exerciseId, String text) {
        return jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=? AND text_value=? ORDER BY item_id LIMIT 1", Long.class, exerciseId, text);
    }

    private Student signupStudent() throws Exception {
        String email = "speech-student-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"음성 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"},\"age\":8}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Student(studentId, login(email));
    }

    private void signupTeacher(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"검토 치료사\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode payload = objectMapper.readTree(response);
        return payload.path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record Student(long studentId, String token) { }
}

