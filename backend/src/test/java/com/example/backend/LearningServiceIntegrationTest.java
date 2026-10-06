package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 학습 서비스 확장: 연습 콘텐츠 검색, 숙제 없는 자율 연습 저장, 연습 세트를 지정한 숙제와 숙제 연습 저장,
 * 담당 선생님의 자율·숙제 연습 기록 조회와 권한(다른 학생·다른 선생님·학생 토큰) 차단.
 * 음성 분석 행은 Whisper 없이 DB에 직접 만든다(실제 녹음→Whisper→분석 흐름은 SpeechAnalysisIntegrationTest·E2E).
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class LearningServiceIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 연습 콘텐츠 ─────────────────────────────

    @Test
    void studentsAndTeachersCanSearchAndFilterPracticeContentWithPaging() throws Exception {
        String student = signupStudent().token, teacher = signupTeacher();
        long seeded = jdbc.queryForObject("SELECT COUNT(*) FROM exercises WHERE active=TRUE AND pronunciation_rule IS NOT NULL", Long.class);
        assertTrue(seeded >= 5, "seed 콘텐츠(data.sql)가 들어 있어야 한다");

        JsonNode all = json(mvc.perform(get("/api/v1/practice-contents").param("size", "3").header("Authorization", bearer(student))).andExpect(status().isOk()));
        assertEquals(3, all.path("content").size());
        assertTrue(all.path("totalElements").asLong() >= seeded);
        JsonNode first = all.path("content").get(0);
        assertTrue(first.path("items").isArray() && first.path("itemCount").asInt() == first.path("items").size());

        // 필터: 발음 유형·난이도·콘텐츠 유형, 검색어(세트 이름 또는 문항 글자)
        JsonNode consonant = json(mvc.perform(get("/api/v1/practice-contents").param("rule", "BASIC_CONSONANT").param("difficulty", "BEGINNER")
                .param("contentType", "WORD").param("size", "50").header("Authorization", bearer(teacher))).andExpect(status().isOk()));
        assertTrue(consonant.path("content").size() > 0);
        for (JsonNode row : consonant.path("content")) {
            assertEquals("BASIC_CONSONANT", row.path("pronunciationRule").asText());
            assertEquals("BEGINNER", row.path("difficulty").asText());
            assertEquals("WORD", row.path("contentType").asText());
        }
        JsonNode keyword = json(mvc.perform(get("/api/v1/practice-contents").param("keyword", "고구마").header("Authorization", bearer(student))).andExpect(status().isOk()));
        assertTrue(keyword.path("totalElements").asLong() >= 1);
        assertTrue(keyword.path("content").get(0).path("items").toString().contains("고구마"));

        // 페이지: 두 번째 페이지는 첫 페이지와 겹치지 않는다
        JsonNode page2 = json(mvc.perform(get("/api/v1/practice-contents").param("size", "3").param("page", "1").header("Authorization", bearer(student))));
        assertNotEquals(first.path("exerciseId").asLong(), page2.path("content").get(0).path("exerciseId").asLong());

        mvc.perform(get("/api/v1/practice-contents").param("rule", "DROP TABLE").header("Authorization", bearer(student))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/practice-contents").param("keyword", "가".repeat(51)).header("Authorization", bearer(student))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/practice-contents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/practice-contents/{id}", first.path("exerciseId").asLong()).header("Authorization", bearer(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/v1/practice-contents/{id}", 99_999_999).header("Authorization", bearer(student))).andExpect(status().isNotFound());
    }

    // ── 자율 연습 · 숙제 연습 ─────────────────────

    @Test
    void selfPracticeIsSavedWithoutHomeworkAndHomeworkPracticeIsLinkedToTheAssignedContent() throws Exception {
        Student student = signupStudent();
        String teacher = signupTeacher();
        link(teacher, student.studentId);
        long[] content = seededContent(0), other = seededContent(1);

        // 자율 연습: 숙제 없이 콘텐츠를 골라 분석 → 저장
        String selfAnalysis = completedAnalysis(student.studentId, content[0], content[1]);
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                        .content(attempt(content, selfAnalysis, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptType").value("PRACTICE")).andExpect(jsonPath("$.homeworkId").doesNotExist());

        // 선생님이 연습 세트를 지정한 숙제를 만든다(담당 학생에게만)
        JsonNode homework = json(mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content(homeworkBody(student.studentId, content[0]))).andExpect(status().isCreated()));
        long homeworkId = homework.path("homeworkId").asLong();
        assertEquals(content[0], homework.path("exerciseId").asLong());
        assertFalse(homework.path("exerciseTitle").asText().isBlank());
        JsonNode mine = json(mvc.perform(get("/api/v1/students/me/homeworks").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals(content[0], mine.path("content").get(0).path("exerciseId").asLong());
        assertEquals(0, mine.path("content").get(0).path("attemptCount").asInt());

        // 숙제 연습: 같은 세트면 HOMEWORK로 연결, 다른 세트면 409, 다른 학생의 숙제 ID면 404
        String homeworkAnalysis = completedAnalysis(student.studentId, content[0], content[1]);
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                        .content(attempt(content, homeworkAnalysis, homeworkId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptType").value("HOMEWORK")).andExpect(jsonPath("$.homeworkId").value(homeworkId));
        String wrongSet = completedAnalysis(student.studentId, other[0], other[1]);
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content(attempt(other, wrongSet, homeworkId))).andExpect(status().isConflict());
        Student stranger = signupStudent();
        String strangerAnalysis = completedAnalysis(stranger.studentId, content[0], content[1]);
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(stranger.token)).contentType(MediaType.APPLICATION_JSON)
                .content(attempt(content, strangerAnalysis, homeworkId))).andExpect(status().isNotFound());

        mine = json(mvc.perform(get("/api/v1/students/me/homeworks").header("Authorization", bearer(student.token))));
        assertEquals(1, mine.path("content").get(0).path("attemptCount").asInt());
        JsonNode history = json(mvc.perform(get("/api/v1/students/me/history").param("type", "word").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals(java.util.Set.of("PRACTICE", "HOMEWORK"), java.util.Set.of(history.path("content").get(0).path("attemptType").asText(), history.path("content").get(1).path("attemptType").asText()));
    }

    @Test
    void homeworkWithContentIsOnlyForAssignedStudentsAndExistingContent() throws Exception {
        Student assigned = signupStudent(), notMine = signupStudent();
        String teacher = signupTeacher();
        link(teacher, assigned.studentId);
        long[] content = seededContent(0);
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(homeworkBody(notMine.studentId, content[0]))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(homeworkBody(assigned.studentId, 99_999_999L))).andExpect(status().isBadRequest());
        // 연습 세트 없는 기존 자유 숙제도 그대로 만들 수 있다
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content(homeworkBody(assigned.studentId, null))).andExpect(status().isCreated()).andExpect(jsonPath("$.exerciseId").doesNotExist());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM homeworks WHERE student_id=?", Integer.class, notMine.studentId));
    }

    // ── 연습 기록 유형: SELF·LESSON·HOMEWORK, 기존 PRACTICE 유지 ─────────

    @Test
    void newAttemptsAreTypedAsSelfLessonOrHomeworkAndLegacyRecordsStayUnclassified() throws Exception {
        Student student = signupStudent(), other = signupStudent();
        String teacher = signupTeacher();
        link(teacher, student.studentId);
        long[] content = seededContent(0), notInLesson = seededContent(1);
        // 수업(세션)과 그 수업에 든 연습 세트
        jdbc.update("INSERT INTO therapy_sessions(student_id,title,scheduled_at,status) VALUES(?,'수업 1',NOW(),'IN_PROGRESS')", student.studentId);
        long sessionId = jdbc.queryForObject("SELECT MAX(session_id) FROM therapy_sessions WHERE student_id=?", Long.class, student.studentId);
        jdbc.update("INSERT INTO session_exercises(session_id,exercise_id,sort_order) VALUES(?,?,1)", sessionId, content[0]);
        jdbc.update("INSERT INTO therapy_sessions(student_id,title,scheduled_at,status) VALUES(?,'남의 수업',NOW(),'IN_PROGRESS')", other.studentId);
        long othersSession = jdbc.queryForObject("SELECT MAX(session_id) FROM therapy_sessions WHERE student_id=?", Long.class, other.studentId);
        jdbc.update("INSERT INTO session_exercises(session_id,exercise_id,sort_order) VALUES(?,?,1)", othersSession, content[0]);

        // 기존(유형 구분 전) 기록: ENUM 확장 뒤에도 PRACTICE 그대로
        String legacy = completedAnalysis(student.studentId, content[0], content[1]);
        jdbc.update("INSERT INTO practice_attempts(student_id,exercise_id,item_id,analysis_id,attempt_type,created_at) VALUES(?,?,?,?,'PRACTICE',DATE_SUB(NOW(),INTERVAL 1 DAY))",
                student.studentId, content[0], String.valueOf(content[1]), legacy);

        saveTyped(student, content, "\"practiceType\":\"SELF\"").andExpect(status().isOk()).andExpect(jsonPath("$.attemptType").value("SELF"));
        saveTyped(student, content, "\"practiceType\":\"LESSON\",\"sessionId\":" + sessionId).andExpect(status().isOk()).andExpect(jsonPath("$.attemptType").value("LESSON"));
        // 수업 연습 검증: 수업 ID 없음 400, 남의 수업 404, 수업에 없는 세트 409, 알 수 없는 유형 400
        saveTyped(student, content, "\"practiceType\":\"LESSON\"").andExpect(status().isBadRequest());
        saveTyped(student, content, "\"practiceType\":\"LESSON\",\"sessionId\":" + othersSession).andExpect(status().isNotFound());
        saveTyped(student, notInLesson, "\"practiceType\":\"LESSON\",\"sessionId\":" + sessionId).andExpect(status().isConflict());
        saveTyped(student, content, "\"practiceType\":\"AI_CHAT\"").andExpect(status().isBadRequest());
        // 숙제 연습은 기존 homeworkId 연결 그대로 HOMEWORK
        long homeworkId = json(mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(homeworkBody(student.studentId, content[0])))).path("homeworkId").asLong();
        saveTyped(student, content, "\"homeworkId\":" + homeworkId).andExpect(status().isOk()).andExpect(jsonPath("$.attemptType").value("HOMEWORK"));

        assertEquals("PRACTICE", jdbc.queryForObject("SELECT attempt_type FROM practice_attempts WHERE analysis_id=?", String.class, legacy), "기존 기록은 재분류하지 않는다");
        mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-summary", student.studentId).header("Authorization", bearer(teacher)))
                .andExpect(jsonPath("$.totalCount").value(4)).andExpect(jsonPath("$.selfCount").value(1)).andExpect(jsonPath("$.lessonCount").value(1))
                .andExpect(jsonPath("$.homeworkCount").value(1)).andExpect(jsonPath("$.unclassifiedCount").value(1)).andExpect(jsonPath("$.practiceCount").value(3));
        for (String type : java.util.List.of("SELF", "LESSON", "HOMEWORK", "PRACTICE")) {
            JsonNode page = json(mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-attempts", student.studentId).param("type", type).header("Authorization", bearer(teacher))).andExpect(status().isOk()));
            assertEquals(1, page.path("totalElements").asInt(), type);
            assertEquals(type, page.path("content").get(0).path("attemptType").asText());
        }
        JsonNode history = json(mvc.perform(get("/api/v1/students/me/history").param("type", "word").header("Authorization", bearer(student.token))));
        assertEquals(java.util.Set.of("SELF", "LESSON", "HOMEWORK", "PRACTICE"), java.util.Set.copyOf(
                java.util.stream.StreamSupport.stream(history.path("content").spliterator(), false).map(n -> n.path("attemptType").asText()).toList()));
    }

    private org.springframework.test.web.servlet.ResultActions saveTyped(Student student, long[] content, String extra) throws Exception {
        String analysisId = completedAnalysis(student.studentId, content[0], content[1]);
        return mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"exerciseId\":\"" + content[0] + "\",\"itemId\":\"" + content[1] + "\",\"audioId\":\"" + analysisId + "\"," + extra + "}"));
    }

    // ── 숙제 자동 완료 ─────────────────────────

    @Test
    void homeworkIsCompletedAutomaticallyOnlyAfterANormalAnalysisOfTheAssignedSet() throws Exception {
        Student student = signupStudent();
        String teacher = signupTeacher();
        link(teacher, student.studentId);
        long[] content = seededContent(0), other = seededContent(1);
        long homeworkId = json(mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(homeworkBody(student.studentId, content[0])))).path("homeworkId").asLong();

        // 분석 중·실패: 기록 저장 자체가 거절되고(409) 숙제는 그대로
        for (String status : java.util.List.of("PROCESSING", "FAILED")) {
            String id = analysis(student.studentId, content[0], content[1], status, "NO_CANDIDATES");
            mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                    .content(attempt(content, id, homeworkId))).andExpect(status().isConflict());
        }
        // 분석 결과 없음(없는 분석 ID): 저장 거절
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content(attempt(content, UUID.randomUUID().toString(), homeworkId))).andExpect(status().isForbidden());
        // 판정 보류(HOLD): 숙제 연습으로 저장은 되지만 완료하지 않는다
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                        .content(attempt(content, analysis(student.studentId, content[0], content[1], "COMPLETED", "HOLD"), homeworkId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptType").value("HOMEWORK")).andExpect(jsonPath("$.homeworkCompleted").value(false));
        // 지정한 세트와 다른 세트: 409, 완료 안 됨
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content(attempt(other, analysis(student.studentId, other[0], other[1], "COMPLETED", "NO_CANDIDATES"), homeworkId))).andExpect(status().isConflict());
        assertEquals("PENDING", homeworkStatus(homeworkId));
        int versionBefore = homeworkVersion(homeworkId);

        // 정상 분석 + 같은 세트: 자동 완료(버전 1 증가)
        String done = analysis(student.studentId, content[0], content[1], "COMPLETED", "ERROR_CANDIDATES");
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content(attempt(content, done, homeworkId))).andExpect(status().isOk()).andExpect(jsonPath("$.homeworkCompleted").value(true));
        assertEquals("COMPLETED", homeworkStatus(homeworkId));
        assertEquals(versionBefore + 1, homeworkVersion(homeworkId));

        // 이미 완료된 숙제: 같은 기록 재저장·새 연습 모두 오류 없이 처리, 상태·버전 다시 바뀌지 않음
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content(attempt(content, done, homeworkId))).andExpect(status().isOk()).andExpect(jsonPath("$.homeworkCompleted").value(true));
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                        .content(attempt(content, analysis(student.studentId, content[0], content[1], "COMPLETED", "NO_CANDIDATES"), homeworkId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.homeworkCompleted").value(true));
        mvc.perform(patch("/api/v1/students/me/homeworks/{id}/complete", homeworkId).header("Authorization", bearer(student.token))).andExpect(status().isOk());
        assertEquals("COMPLETED", homeworkStatus(homeworkId));
        assertEquals(versionBefore + 1, homeworkVersion(homeworkId));
    }

    @Test
    void aFreeHomeworkWithoutAPracticeSetIsNotCompletedAutomatically() throws Exception {
        Student student = signupStudent();
        String teacher = signupTeacher();
        link(teacher, student.studentId);
        long[] content = seededContent(0);
        long homeworkId = json(mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(homeworkBody(student.studentId, null)))).path("homeworkId").asLong();
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                        .content(attempt(content, analysis(student.studentId, content[0], content[1], "COMPLETED", "NO_CANDIDATES"), homeworkId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.homeworkCompleted").value(false));
        assertEquals("PENDING", homeworkStatus(homeworkId), "연습 세트를 지정하지 않은 숙제는 무엇을 해야 완료인지 알 수 없어 자동 완료하지 않는다");
    }

    private String analysis(long studentId, long exerciseId, long itemId, String status, String assessmentStatus) {
        String id = UUID.randomUUID().toString();
        String text = jdbc.queryForObject("SELECT text_value FROM exercise_items WHERE item_id=?", String.class, itemId);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,?,?,'audio/wav',?,'SENTENCE_MATCH',?,?,100.00,'NOT_EVALUATED','NOT_REQUIRED','WORD',?,'{}','jamo-align-v1',NOW())",
                id, studentId, exerciseId, String.valueOf(itemId), status, text, text, assessmentStatus);
        return id;
    }

    private String homeworkStatus(long homeworkId) { return jdbc.queryForObject("SELECT status FROM homeworks WHERE homework_id=?", String.class, homeworkId); }
    private int homeworkVersion(long homeworkId) { return jdbc.queryForObject("SELECT version FROM homeworks WHERE homework_id=?", Integer.class, homeworkId); }

    // ── 선생님: 자율·숙제 연습 기록 ─────────────────

    @Test
    void theAssignedTeacherSeesSelfAndHomeworkPracticeAndOthersAreBlocked() throws Exception {
        Student student = signupStudent(), otherStudent = signupStudent();
        String teacher = signupTeacher(), outsider = signupTeacher();
        link(teacher, student.studentId);
        link(outsider, otherStudent.studentId);
        long[] content = seededContent(0);
        String self = completedAnalysis(student.studentId, content[0], content[1]);
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content(attempt(content, self, null))).andExpect(status().isOk());
        long homeworkId = json(mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(homeworkBody(student.studentId, content[0])))).path("homeworkId").asLong();
        String hw = completedAnalysis(student.studentId, content[0], content[1]);
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content(attempt(content, hw, homeworkId))).andExpect(status().isOk());

        JsonNode all = json(mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-attempts", student.studentId).header("Authorization", bearer(teacher))).andExpect(status().isOk()));
        assertEquals(2, all.path("totalElements").asInt());
        JsonNode homeworkRow = all.path("content").get(0);
        assertEquals("HOMEWORK", homeworkRow.path("attemptType").asText());
        assertEquals(homeworkId, homeworkRow.path("homeworkId").asLong());
        assertEquals("COMPLETED", homeworkRow.path("analysisStatus").asText());
        assertEquals(hw, homeworkRow.path("analysisId").asText(), "AI 설명 조회(기존 선생님 API)에 쓸 분석 ID");
        assertEquals("PRACTICE", json(mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-attempts", student.studentId).param("type", "PRACTICE")
                .header("Authorization", bearer(teacher)))).path("content").get(0).path("attemptType").asText());
        mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-summary", student.studentId).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.practiceCount").value(1))
                .andExpect(jsonPath("$.homeworkCount").value(1)).andExpect(jsonPath("$.exerciseCount").value(1));
        // 담당한 학생의 기록 화면에서 기존 AI 설명 API도 그대로 쓸 수 있다(AI 호출 없이 상태만)
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/feedback", self).header("Authorization", bearer(teacher))).andExpect(status().isOk());

        // 차단: 다른 선생님, 다른 학생 ID, 학생 토큰, 잘못된 종류
        mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-attempts", student.studentId).header("Authorization", bearer(outsider)))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 403 || result.getResponse().getStatus() == 404));
        mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-summary", otherStudent.studentId).header("Authorization", bearer(teacher)))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 403 || result.getResponse().getStatus() == 404));
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/feedback", self).header("Authorization", bearer(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-attempts", student.studentId).header("Authorization", bearer(student.token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teachers/me/students/{id}/practice-attempts", student.studentId).param("type", "AI_CHAT")
                .header("Authorization", bearer(teacher))).andExpect(status().isBadRequest());
    }

    // ── helpers ─────────────────────────────────

    /** [exerciseId, 첫 문항 itemId] — seed 콘텐츠(data.sql)에서 index번째 세트 */
    private long[] seededContent(int index) {
        long exerciseId = jdbc.queryForObject("SELECT exercise_id FROM exercises WHERE pronunciation_rule IS NOT NULL AND active=TRUE ORDER BY exercise_id LIMIT 1 OFFSET " + index, Long.class);
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=? ORDER BY sort_order LIMIT 1", Long.class, exerciseId);
        return new long[]{exerciseId, itemId};
    }

    private String completedAnalysis(long studentId, long exerciseId, long itemId) {
        String id = UUID.randomUUID().toString();
        String text = jdbc.queryForObject("SELECT text_value FROM exercise_items WHERE item_id=?", String.class, itemId);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,?,?,'audio/wav','COMPLETED','SENTENCE_MATCH',?,?,100.00,'NOT_EVALUATED','NOT_REQUIRED','WORD','NO_CANDIDATES','{}','jamo-align-v1',NOW())",
                id, studentId, exerciseId, String.valueOf(itemId), text, text);
        return id;
    }

    private String attempt(long[] content, String analysisId, Long homeworkId) {
        return "{\"exerciseId\":\"" + content[0] + "\",\"itemId\":\"" + content[1] + "\",\"audioId\":\"" + analysisId + "\""
                + (homeworkId == null ? "" : ",\"homeworkId\":" + homeworkId) + "}";
    }

    private String homeworkBody(long studentId, Long exerciseId) {
        return "{\"studentId\":" + studentId + ",\"title\":\"받침 연습\",\"type\":\"말하기 연습\",\"dueDate\":\"" + LocalDate.now().plusDays(3)
                + "\",\"targetMinutes\":10" + (exerciseId == null ? "" : ",\"exerciseId\":" + exerciseId) + "}";
    }

    private void link(String teacherToken, long studentId) throws Exception {
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + studentId + ",\"name\":\"학습 학생\"}")).andExpect(status().isCreated());
    }

    private record Student(long studentId, String token) { }

    private Student signupStudent() throws Exception {
        String email = "learn-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"학습 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Student(studentId, login(email));
    }

    private String signupTeacher() throws Exception {
        String email = "learn-t-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"학습 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String bearer(String token) { return "Bearer " + token; }
}
