package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.backend.support.TestPrerequisites.requireSpeech;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Idempotency-Key로 같은 녹음의 중복 분석을 막는지 실제 DB·whisper.cpp로 확인한다.
 * 동시 요청은 서로 다른 스레드에서 실행되므로 테스트 트랜잭션(롤백)을 쓰지 않고, 만든 데이터를 @AfterEach에서 직접 지운다.
 */
@SpringBootTest(properties = {
        "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "app.speech.engine=local",
        "app.audio.storage-path=${java.io.tmpdir}/chatterland-test-audio",
        // 주기 정리 작업이 테스트가 만든 고착 행을 먼저 정리하지 않도록 끈다(정리 동작은 직접 호출해 검증).
        "app.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class SpeechIdempotencyIntegrationTest {
    private static final String MODEL = System.getenv().getOrDefault("WHISPER_MODEL_PATH",
            System.getProperty("user.home") + "/.cache/chatterland/whisper/ggml-large-v3-turbo-q5_0.bin");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.example.backend.chld.service.SpeechAnalysisService speechAnalysis;
    @Autowired com.example.backend.chld.mapper.StudentMapper studentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> createdEmails = new ArrayList<>();

    @BeforeEach
    void requireModel() {
        requireSpeech(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 중복 분석 통합 테스트를 건너뜁니다.");
    }

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            Long userId = jdbc.query("SELECT user_id FROM users WHERE email=?", rs -> rs.next() ? rs.getLong(1) : null, email);
            if (userId == null) continue;
            Long studentId = jdbc.query("SELECT student_id FROM student_profiles WHERE user_id=?", rs -> rs.next() ? rs.getLong(1) : null, userId);
            if (studentId != null) {
                jdbc.update("DELETE FROM practice_attempts WHERE student_id=?", studentId);
                jdbc.update("DELETE FROM speech_analyses WHERE student_id=?", studentId);
                jdbc.update("DELETE FROM student_profiles WHERE student_id=?", studentId);
            }
            jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        }
    }

    @Test
    void retryWithTheSameKeyReturnsTheSameAnalysisAndSavesOneAttempt() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        String key = UUID.randomUUID().toString();
        JsonNode first = json(analyze(student, "word-radio-recorded.wav", itemId, key));
        assertEquals("COMPLETED", first.path("status").asText());
        assertFalse(first.has("reused"));
        // 응답이 유실돼 화면이 같은 녹음을 다시 보낸 경우(네트워크 재시도·다시 분석하기)
        JsonNode retry = json(analyze(student, "word-radio-recorded.wav", itemId, key));
        assertEquals(first.path("analysisId").asText(), retry.path("analysisId").asText());
        assertTrue(retry.path("reused").asBoolean());
        assertEquals(1, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=?", student.studentId));
        String body = "{\"exerciseId\":\"1\",\"itemId\":\"" + itemId + "\",\"audioId\":\"" + first.path("analysisId").asText() + "\"}";
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/api/v1/practice/attempts").header("Authorization", "Bearer " + student.token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertEquals(1, count("SELECT COUNT(*) FROM practice_attempts WHERE student_id=?", student.studentId));
    }

    @Test
    void concurrentRequestsWithTheSameKeyRunOneAnalysis() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        String key = UUID.randomUUID().toString();
        int parallel = 4;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < parallel; i++) {
                Callable<MvcResult> call = () -> { start.await(); return analyze(student, "word-radio-recorded.wav", itemId, key); };
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<String> ids = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get();
                assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
                ids.add(json(result).path("analysisId").asText());
            }
            assertEquals(1, ids.stream().distinct().count(), "모든 동시 요청이 같은 분석을 받아야 한다: " + ids);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=?", student.studentId));
        assertEquals(1, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='COMPLETED'", student.studentId));
    }

    @Test
    void differentRecordingsOrNoKeyAreSeparateAnalyses() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        String a = json(analyze(student, "word-radio-recorded.wav", itemId, UUID.randomUUID().toString())).path("analysisId").asText();
        String b = json(analyze(student, "word-dadio-recorded.wav", itemId, UUID.randomUUID().toString())).path("analysisId").asText();
        String c = json(analyze(student, "word-radio-recorded.wav", itemId, null)).path("analysisId").asText();
        String d = json(analyze(student, "word-radio-recorded.wav", itemId, null)).path("analysisId").asText();
        assertEquals(4, List.of(a, b, c, d).stream().distinct().count(), "키가 다르거나 없으면 별개의 분석이다");
        assertEquals(4, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='COMPLETED'", student.studentId));
    }

    @Test
    void reusingAKeyForAnotherRecordingIsRejected() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        String key = UUID.randomUUID().toString();
        analyze(student, "word-radio-recorded.wav", itemId, key);
        MvcResult conflict = analyze(student, "word-dadio-recorded.wav", itemId, key);
        assertEquals(409, conflict.getResponse().getStatus());
        assertEquals(1, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=?", student.studentId));
        assertEquals(400, analyze(student, "word-radio-recorded.wav", itemId, "bad key!").getResponse().getStatus());
    }

    @Test
    void failedAnalysisReleasesTheKeySoTheSameRecordingCanBeRetried() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        String key = UUID.randomUUID().toString();
        assertEquals(422, analyze(student, "silence.wav", itemId, key).getResponse().getStatus());
        assertEquals(1, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='FAILED' AND request_key IS NULL", student.studentId));
        // 같은 키로 다시 보내면 중복으로 막지 않고 다시 분석한다(무음이라 다시 422).
        assertEquals(422, analyze(student, "silence.wav", itemId, key).getResponse().getStatus());
        assertEquals(2, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='FAILED'", student.studentId));
    }

    private MvcResult analyze(Student student, String file, long itemId, String key) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(getClass().getResource("/speech/" + file).toURI()));
        var request = multipart("/api/v1/speech/analyze").file(new MockMultipartFile("audio", file, "audio/wav", bytes))
                .part(new MockPart("exerciseId", "1".getBytes(StandardCharsets.UTF_8)))
                .part(new MockPart("itemId", String.valueOf(itemId).getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + student.token);
        if (key != null) request.header("Idempotency-Key", key);
        return mvc.perform(request).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int count(String sql, long studentId) {
        return jdbc.queryForObject(sql, Integer.class, studentId);
    }

    private long itemId() {
        return jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 AND text_value='라디오' ORDER BY item_id LIMIT 1", Long.class);
    }

    private Student signupStudent() throws Exception {
        String email = "idem-student-" + UUID.randomUUID() + "@example.test";
        createdEmails.add(email);
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"중복 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Student(studentId, objectMapper.readTree(login).path("accessToken").asText());
    }

    private record Student(long studentId, String token) { }

    // ── 처리 중(PROCESSING) 고착 복구 ──────────────
    private String insertProcessing(long studentId, long itemId, String key, String file, int minutesAgo, String audioPath) throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_path,audio_mime,status,evaluation_mode,target_text,"
                        + "engine_name,request_key,request_hash,created_at) VALUES(?,?,1,?,?,'audio/wav','PROCESSING','SENTENCE_MATCH','라디오','whisper.cpp',?,?,"
                        + "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? MINUTE))",
                id, studentId, String.valueOf(itemId), audioPath, key, key == null ? null : sha256(file, itemId), minutesAgo);
        return id;
    }

    /** SpeechAnalysisServiceImpl.requestHash와 같은 규칙(녹음 바이트 + "|exerciseId|itemId")의 SHA-256 */
    private String sha256(String file, long itemId) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(Path.of(getClass().getResource("/speech/" + file).toURI())));
        digest.update(("|1|" + itemId).getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    @Test
    void retryAfterTheServerDiedMidAnalysisRunsANewAnalysis() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        String key = UUID.randomUUID().toString();
        String stuck = insertProcessing(student.studentId, itemId, key, "word-radio-recorded.wav", 10, null);
        JsonNode response = json(analyze(student, "word-radio-recorded.wav", itemId, key));
        assertEquals("COMPLETED", response.path("status").asText());
        assertNotEquals(stuck, response.path("analysisId").asText());
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM speech_analyses WHERE analysis_id=?", String.class, stuck));
        assertEquals("STALE_PROCESSING", jdbc.queryForObject("SELECT error_code FROM speech_analyses WHERE analysis_id=?", String.class, stuck));
        assertNull(jdbc.queryForObject("SELECT request_key FROM speech_analyses WHERE analysis_id=?", String.class, stuck));
        // 같은 키로 다시 보내면 이제 새로 끝난 분석을 재사용한다.
        JsonNode again = json(analyze(student, "word-radio-recorded.wav", itemId, key));
        assertEquals(response.path("analysisId").asText(), again.path("analysisId").asText());
        assertTrue(again.path("reused").asBoolean());
    }

    @Test
    void analysisStillWithinTheThresholdIsReusedWithoutASecondInference() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        String key = UUID.randomUUID().toString();
        String running = insertProcessing(student.studentId, itemId, key, "word-radio-recorded.wav", 1, null);
        JsonNode response = json(analyze(student, "word-radio-recorded.wav", itemId, key));
        assertEquals(running, response.path("analysisId").asText());
        assertEquals("PROCESSING", response.path("status").asText());
        assertEquals(1, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=?", student.studentId));
        // 같은 키에 다른 녹음은 고착 여부와 무관하게 409(키 재사용 금지 계약 유지)
        assertEquals(409, analyze(student, "word-dadio-recorded.wav", itemId, key).getResponse().getStatus());
    }

    @Test
    void concurrentRetriesOnAStuckAnalysisProduceExactlyOneNewAnalysis() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        String key = UUID.randomUUID().toString();
        String stuck = insertProcessing(student.studentId, itemId, key, "word-radio-recorded.wav", 10, null);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) futures.add(pool.submit(() -> { start.await(); return analyze(student, "word-radio-recorded.wav", itemId, key); }));
            start.countDown();
            List<String> ids = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get();
                assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
                ids.add(json(result).path("analysisId").asText());
            }
            assertEquals(1, ids.stream().distinct().count(), "모든 재시도가 같은 새 분석을 받아야 한다: " + ids);
            assertFalse(ids.contains(stuck));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='COMPLETED'", student.studentId));
        assertEquals(1, count("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='FAILED'", student.studentId));
    }

    @Test
    void periodicRecoveryFailsOnlyStaleRowsAndDeletesTheirAudio() throws Exception {
        Student student = signupStudent();
        long itemId = itemId();
        Path storage = Path.of(System.getProperty("java.io.tmpdir"), "chatterland-test-audio");
        Files.createDirectories(storage);
        Path staleAudio = Files.writeString(storage.resolve(UUID.randomUUID() + ".wav"), "stale");
        Path freshAudio = Files.writeString(storage.resolve(UUID.randomUUID() + ".wav"), "fresh");
        String stale = insertProcessing(student.studentId, itemId, UUID.randomUUID().toString(), "word-radio-recorded.wav", 30, staleAudio.toString());
        String fresh = insertProcessing(student.studentId, itemId, UUID.randomUUID().toString(), "word-radio-recorded.wav", 1, freshAudio.toString());
        assertTrue(speechAnalysis.recoverStaleAnalyses() >= 1);
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM speech_analyses WHERE analysis_id=?", String.class, stale));
        assertNull(jdbc.queryForObject("SELECT audio_path FROM speech_analyses WHERE analysis_id=?", String.class, stale));
        assertFalse(Files.exists(staleAudio), "고착 분석의 녹음은 지운다");
        assertEquals("PROCESSING", jdbc.queryForObject("SELECT status FROM speech_analyses WHERE analysis_id=?", String.class, fresh));
        assertTrue(Files.exists(freshAudio), "기준 시간이 안 된 분석은 건드리지 않는다");
        Files.deleteIfExists(freshAudio);
    }

    @Test
    void aLateFinishOfTheOriginalRequestDoesNotOverwriteTheRecoveredState() throws Exception {
        Student student = signupStudent();
        String stuck = insertProcessing(student.studentId, itemId(), null, "word-radio-recorded.wav", 30, null);
        speechAnalysis.recoverStaleAnalyses();
        // 원래 요청이 뒤늦게 실패·완료 처리를 시도해도(실제 매퍼, 조건부 UPDATE) 정리 결과를 덮어쓰지 않는다.
        assertEquals(0, studentMapper.failAnalysis(stuck, "500 INTERNAL_SERVER_ERROR"));
        assertEquals(0, studentMapper.completeLocalAnalysis(stuck, "늦은 결과", null, null, "[]", "NOT_EVALUATED", "NOT_REQUIRED",
                "whisper.cpp", "model", "WORD", "NO_CANDIDATES", "{}", "jamo-align-v1"));
        assertEquals("STALE_PROCESSING", jdbc.queryForObject("SELECT error_code FROM speech_analyses WHERE analysis_id=?", String.class, stuck));
        assertNull(jdbc.queryForObject("SELECT transcript FROM speech_analyses WHERE analysis_id=?", String.class, stuck));
    }
}
