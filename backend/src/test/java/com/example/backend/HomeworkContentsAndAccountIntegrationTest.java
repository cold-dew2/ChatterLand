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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 숙제 하나에 연습 콘텐츠 여러 개(공통 콘텐츠 참조) + 학생 계정 화면 API.
 * - 숙제 등록·수정에서 콘텐츠 목록 저장/교체, 기존 단일 exerciseId 호환, 학생 숙제 목록의 콘텐츠 순서
 * - 지정 콘텐츠만 숙제 연습으로 저장, 모든 콘텐츠를 정상 완료해야 자동 완료
 * - 학생 프로필의 센터·담당 선생님, 이름 수정, 알림 설정, 학습 상태
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class HomeworkContentsAndAccountIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void homeworkKeepsSeveralSharedContentsInOrderAndCanReplaceThem() throws Exception {
        Student student = signupStudent(); String teacher = signupTeacher(); link(teacher, student.studentId);
        List<Long> ids = jdbc.queryForList("SELECT exercise_id FROM exercises WHERE category_id='vocabulary' AND active=TRUE ORDER BY sort_order,exercise_id LIMIT 3", Long.class);
        long exercisesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM exercises", Long.class);
        JsonNode created = json(mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(body(student.studentId, "\"exerciseIds\":[" + ids.get(2) + "," + ids.get(0) + "," + ids.get(2) + "]"))).andExpect(status().isCreated()));
        long homeworkId = created.path("homeworkId").asLong();
        assertEquals(List.of(ids.get(2), ids.get(0)), exerciseIds(created), "선택한 순서, 중복은 한 번만");
        assertEquals(ids.get(2), created.path("exerciseId").asLong(), "기존 호환 필드는 첫 콘텐츠");
        assertEquals(exercisesBefore, jdbc.queryForObject("SELECT COUNT(*) FROM exercises", Long.class), "숙제를 위해 콘텐츠를 복제하지 않는다");

        JsonNode studentList = json(mvc.perform(get("/api/v1/students/me/homeworks").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals(List.of(ids.get(2), ids.get(0)), exerciseIds(studentList.path("content").get(0)));
        assertFalse(studentList.path("content").get(0).path("exercises").get(0).path("categoryName").asText().isBlank());

        int version = created.path("version").asInt();
        JsonNode updated = json(mvc.perform(patch("/api/v1/teachers/me/homeworks/" + homeworkId).header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"exerciseIds\":[" + ids.get(1) + "," + ids.get(2) + "]}")).andExpect(status().isOk()));
        assertEquals(List.of(ids.get(1), ids.get(2)), exerciseIds(updated));
        assertEquals(ids.get(1), updated.path("exerciseId").asLong());
        // 콘텐츠를 보내지 않는 수정(기존 호출)은 콘텐츠를 그대로 둔다.
        JsonNode titleOnly = json(mvc.perform(patch("/api/v1/teachers/me/homeworks/" + homeworkId).header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + updated.path("version").asInt() + ",\"title\":\"새 제목\"}")).andExpect(status().isOk()));
        assertEquals(List.of(ids.get(1), ids.get(2)), exerciseIds(titleOnly));
        // 오래된 버전으로 콘텐츠를 바꾸면 409이고 연결은 바뀌지 않는다.
        mvc.perform(patch("/api/v1/teachers/me/homeworks/" + homeworkId).header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"exerciseIds\":[" + ids.get(0) + "]}")).andExpect(status().isConflict());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM homework_exercises WHERE homework_id=?", Integer.class, homeworkId));
        // 기존 단일 exerciseId 등록도 그대로 동작한다.
        JsonNode single = json(mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(body(student.studentId, "\"exerciseId\":" + ids.get(0)))).andExpect(status().isCreated()));
        assertEquals(List.of(ids.get(0)), exerciseIds(single));
        // 없는 콘텐츠는 거절한다.
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(body(student.studentId, "\"exerciseIds\":[999999999]"))).andExpect(status().isBadRequest());
    }

    @Test
    void homeworkWithSeveralContentsCompletesOnlyAfterEveryContentIsPracticedNormally() throws Exception {
        Student student = signupStudent(); String teacher = signupTeacher(); link(teacher, student.studentId);
        List<Long> ids = jdbc.queryForList("SELECT exercise_id FROM exercises WHERE category_id='articulation' AND active=TRUE ORDER BY sort_order,exercise_id LIMIT 3", Long.class);
        JsonNode created = json(mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(body(student.studentId, "\"exerciseIds\":[" + ids.get(0) + "," + ids.get(1) + "]"))).andExpect(status().isCreated()));
        long homeworkId = created.path("homeworkId").asLong();

        save(student, ids.get(2), homeworkId).andExpect(status().isConflict()); // 지정하지 않은 콘텐츠
        JsonNode first = json(save(student, ids.get(0), homeworkId).andExpect(status().isOk()));
        assertFalse(first.path("homeworkCompleted").asBoolean(), "두 번째 콘텐츠가 남아 있다");
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM homeworks WHERE homework_id=?", String.class, homeworkId));
        JsonNode second = json(save(student, ids.get(1), homeworkId).andExpect(status().isOk()));
        assertTrue(second.path("homeworkCompleted").asBoolean());
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM homeworks WHERE homework_id=?", String.class, homeworkId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempts WHERE homework_id=? AND attempt_type='HOMEWORK'", Integer.class, homeworkId),
                "콘텐츠마다 숙제 연습 기록이 하나씩 저장된다");
    }

    @Test
    void studentAccountScreensUseOnlyRealData() throws Exception {
        Student student = signupStudent();
        JsonNode alone = json(mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals(0, alone.path("teachers").size(), "담당 선생님이 없으면 빈 목록(개인 학습)");
        String teacher = signupTeacher(); link(teacher, student.studentId);
        JsonNode linked = json(mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals("계정 선생님", linked.path("teachers").get(0).path("name").asText());

        JsonNode renamed = json(mvc.perform(patch("/api/v1/account/profile").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  새 이름  \"}")).andExpect(status().isOk()));
        assertEquals("새 이름", renamed.path("name").asText());
        assertEquals("새 이름", jdbc.queryForObject("SELECT name FROM student_profiles WHERE student_id=?", String.class, student.studentId), "선생님 화면 이름도 같이 바뀐다");
        mvc.perform(patch("/api/v1/account/profile").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}")).andExpect(status().isBadRequest());

        JsonNode defaults = json(mvc.perform(get("/api/v1/account/notification-settings").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertTrue(defaults.path("homeworkAssigned").asBoolean() && defaults.path("homeworkOverdue").asBoolean());
        JsonNode saved = json(mvc.perform(put("/api/v1/account/notification-settings").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"homeworkAssigned\":true,\"homeworkChanged\":false,\"homeworkDueSoon\":true,\"homeworkOverdue\":false}")).andExpect(status().isOk()));
        assertFalse(saved.path("homeworkChanged").asBoolean());
        assertFalse(json(mvc.perform(get("/api/v1/account/notification-settings").header("Authorization", bearer(student.token)))).path("homeworkOverdue").asBoolean());
        mvc.perform(put("/api/v1/account/notification-settings").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"homeworkAssigned\":true}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/account/notification-settings")).andExpect(status().isUnauthorized());

        long exerciseId = jdbc.queryForObject("SELECT exercise_id FROM exercises WHERE category_id='fluency' AND active=TRUE ORDER BY sort_order,exercise_id LIMIT 1", Long.class);
        long itemId = jdbc.queryForObject("SELECT MIN(item_id) FROM exercise_items WHERE exercise_id=?", Long.class, exerciseId);
        for (int i = 0; i < 2; i++)
            jdbc.update("INSERT INTO practice_attempts(student_id,exercise_id,item_id,attempt_type) VALUES(?,?,?,'SELF')", student.studentId, exerciseId, String.valueOf(itemId));
        JsonNode learning = json(mvc.perform(get("/api/v1/students/me/learning-status").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertEquals(2, learning.path("totalAttempts").asInt());
        assertEquals(1, learning.path("practicedExercises").asInt());
        assertEquals(1, learning.path("retriedExercises").asInt());
        assertEquals(5, learning.path("categories").size());
        for (JsonNode area : learning.path("categories"))
            assertEquals("fluency".equals(area.path("categoryId").asText()) ? 2 : 0, area.path("attempts").asInt());
        assertEquals(2, learning.path("trend").get(learning.path("trend").size() - 1).path("attempts").asInt());
        assertFalse(learning.has("score"), "학습 상태는 점수를 만들지 않는다");
        mvc.perform(get("/api/v1/students/me/learning-status").header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions save(Student student, long exerciseId, long homeworkId) throws Exception {
        long itemId = jdbc.queryForObject("SELECT MIN(item_id) FROM exercise_items WHERE exercise_id=?", Long.class, exerciseId);
        String analysisId = UUID.randomUUID().toString();
        String text = jdbc.queryForObject("SELECT text_value FROM exercise_items WHERE item_id=?", String.class, itemId);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,?,?,'audio/wav','COMPLETED','SENTENCE_MATCH',?,?,100.00,'NOT_EVALUATED','NOT_REQUIRED','WORD','NO_CANDIDATES','{}','jamo-align-v1',NOW())",
                analysisId, student.studentId, exerciseId, String.valueOf(itemId), text, text);
        return mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"exerciseId\":\"" + exerciseId + "\",\"itemId\":\"" + itemId + "\",\"audioId\":\"" + analysisId + "\",\"homeworkId\":" + homeworkId + "}"));
    }

    private static List<Long> exerciseIds(JsonNode homework) {
        List<Long> ids = new ArrayList<>();
        homework.path("exercises").forEach(row -> ids.add(row.path("exerciseId").asLong()));
        return ids;
    }

    private static String body(long studentId, String extra) {
        return "{\"studentId\":" + studentId + ",\"title\":\"여러 콘텐츠 숙제\",\"type\":\"말하기 연습\",\"dueDate\":\"" + LocalDate.now().plusDays(3) + "\",\"targetMinutes\":10," + extra + "}";
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private void link(String teacherToken, long studentId) throws Exception {
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + studentId + ",\"name\":\"계정 학생\"}")).andExpect(status().isCreated());
    }

    private record Student(long studentId, String token) { }

    private Student signupStudent() throws Exception {
        String email = "hwc-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"계정 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Student(studentId, login(email));
    }

    private String signupTeacher() throws Exception {
        String email = "hwc-t-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"계정 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
}
