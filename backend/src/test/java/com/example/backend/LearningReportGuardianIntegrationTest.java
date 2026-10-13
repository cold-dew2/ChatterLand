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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 누적 학습 리포트(학생·선생님·보호자 공통 집계), 규칙형 추천, 보호자↔자녀 연결과 권한.
 * 연습 기록은 날짜를 정해 직접 넣는다(완료된 분석 1건 = 기록 1행이라는 저장 규칙과 같은 모양).
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class LearningReportGuardianIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 누적 학습 리포트 ─────────────────────────────────────────────

    @Test
    void reportCountsSavedAttemptsByPeriodAndTeacherSeesTheSameNumbers() throws Exception {
        Student student = signupStudent(); String teacher = signupTeacher(); link(teacher, student.studentId);
        List<Long> ids = jdbc.queryForList("SELECT exercise_id FROM exercises WHERE category_id='articulation' AND active=TRUE ORDER BY sort_order,exercise_id LIMIT 3", Long.class);
        long a = ids.get(0), b = ids.get(1), c = ids.get(2);
        // A: 오늘 문항 2개(한 번 연습한 것, 반복 아님) + 10일 전 → 30일·전체에서는 서로 다른 날 반복
        attempt(student, a, 0, 0); attempt(student, a, 1, 0); attempt(student, a, 0, 10);
        // B: 3일 전 같은 문항 두 번 녹음(재시도 = 반복)
        attempt(student, b, 0, 3); attempt(student, b, 0, 3);
        // C: 40일 전
        attempt(student, c, 0, 40);

        JsonNode week = studentReport(student, "7d");
        assertEquals(4, week.path("totalAttempts").asLong());
        assertEquals(2, week.path("practicedExercises").asLong());
        assertEquals(2, week.path("practiceDays").asLong());
        assertEquals(1, week.path("retriedExercises").asLong(), "문항이 여러 개인 콘텐츠를 한 번 연습한 것은 반복이 아니다");
        assertEquals(LocalDate.now().minusDays(6).toString(), week.path("startDate").asText());
        assertEquals(LocalDate.now().toString(), week.path("endDate").asText());
        assertEquals(1, week.path("comparison").path("totalAttempts").asLong(), "이전 7일(8~14일 전)의 기록");
        assertEquals(3, week.path("comparison").path("totalAttemptsChange").asLong());
        assertEquals(4, sum(week.path("daily"), "attempts"), "일별 합계 = 전체");
        assertEquals(4, sum(week.path("categories"), "attempts"), "영역별 합계 = 전체");
        assertEquals(5, week.path("categories").size(), "기록이 없는 영역도 0으로 나온다");

        JsonNode month = studentReport(student, "30d");
        assertEquals(5, month.path("totalAttempts").asLong());
        assertEquals(2, month.path("retriedExercises").asLong(), "서로 다른 날 다시 연습한 A도 반복");
        assertEquals(2, month.path("repeated").size());
        assertEquals(1, month.path("comparison").path("totalAttempts").asLong(), "이전 30일(31~60일 전)의 C");

        JsonNode all = studentReport(student, null);
        assertEquals(6, all.path("totalAttempts").asLong());
        assertEquals(3, all.path("practicedExercises").asLong());
        assertTrue(all.path("comparison").isNull(), "전체 기간은 비교하지 않는다");
        assertTrue(all.path("activeExercises").asLong() >= 1500);
        assertEquals(6, all.path("recentAttempts").size());
        assertFalse(all.toString().contains("matchRate"), "리포트에 텍스트 일치율·점수를 넣지 않는다");
        // 기존 응답 키 유지
        assertTrue(all.has("trend") && all.has("recent"));

        // 이전 기간에 기록이 없으면 변화량을 만들지 않는다
        Student fresh = signupStudent(); attempt(fresh, a, 0, 0);
        assertTrue(studentReport(fresh, "7d").path("comparison").isNull());

        // 선생님(담당) 화면: 같은 집계 → 같은 숫자
        for (String period : new String[]{"7d", "30d", "all"}) {
            JsonNode mine = studentReport(student, period);
            JsonNode theirs = json(mvc.perform(get("/api/v1/teachers/me/students/" + student.studentId + "/learning-report?period=" + period)
                    .header("Authorization", bearer(teacher))).andExpect(status().isOk()));
            for (String key : new String[]{"totalAttempts", "practicedExercises", "practicedActiveExercises", "practiceDays", "homeworkAttempts", "retriedExercises", "startDate", "endDate"})
                assertEquals(mine.path(key).asText(), theirs.path(key).asText(), period + " " + key);
            assertEquals(mine.path("categories"), theirs.path("categories"));
            assertEquals(mine.path("daily"), theirs.path("daily"));
            assertEquals(mine.path("comparison"), theirs.path("comparison"));
        }
        // 담당이 아닌 선생님은 볼 수 없다
        mvc.perform(get("/api/v1/teachers/me/students/" + student.studentId + "/learning-report").header("Authorization", bearer(signupTeacher())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/me/learning-status?period=90d").header("Authorization", bearer(student.token))).andExpect(status().isBadRequest());
    }

    // ── 추천 ─────────────────────────────────────────────

    @Test
    void recommendationsAreActiveContentsWithReasonsAndSkipPendingHomework() throws Exception {
        Student student = signupStudent(); String teacher = signupTeacher(); link(teacher, student.studentId);
        JsonNode starter = json(mvc.perform(get("/api/v1/students/me/recommendations").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertFalse(starter.path("personalized").asBoolean(), "기록이 없으면 개인화 결과라고 하지 않는다");
        assertEquals(5, starter.path("items").size());
        starter.path("items").forEach(item -> assertEquals("STARTER", item.path("reasonCode").asText()));
        assertActive(starter);

        List<Long> articulation = jdbc.queryForList("SELECT exercise_id FROM exercises WHERE category_id='articulation' AND active=TRUE ORDER BY sort_order,exercise_id LIMIT 4", Long.class);
        attempt(student, articulation.get(0), 0, 1); attempt(student, articulation.get(0), 0, 0);
        // 다음 순서 콘텐츠를 숙제로 내면 자율 연습 추천에서는 빠진다
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student.studentId + ",\"title\":\"추천 숙제\",\"type\":\"말하기 연습\",\"dueDate\":\"" + LocalDate.now().plusDays(2)
                        + "\",\"targetMinutes\":10,\"exerciseIds\":[" + articulation.get(1) + "]}")).andExpect(status().isCreated());
        JsonNode personal = json(mvc.perform(get("/api/v1/students/me/recommendations").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
        assertTrue(personal.path("personalized").asBoolean());
        assertEquals(1, personal.path("pendingHomeworks").asLong());
        assertActive(personal);
        Set<Long> recommended = new HashSet<>();
        personal.path("items").forEach(item -> { recommended.add(item.path("exerciseId").asLong()); assertFalse(item.path("reason").asText().isBlank()); });
        assertFalse(recommended.contains(articulation.get(1)), "진행 중 숙제 콘텐츠는 빼고 숙제 개수만 알린다");
        assertEquals("REPEATED", personal.path("items").get(0).path("reasonCode").asText());
        assertEquals(articulation.get(0), personal.path("items").get(0).path("exerciseId").asLong());
        assertEquals(articulation.get(2), personal.path("items").get(1).path("exerciseId").asLong(), "최근 영역의 다음 콘텐츠(숙제 콘텐츠는 건너뜀)");
        mvc.perform(get("/api/v1/students/me/recommendations").header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
    }

    // ── 보호자 ─────────────────────────────────────────────

    @Test
    void guardianLinksOnlyWithChildIssuedCodeAndSeesOnlyLinkedChildren() throws Exception {
        Student child = signupStudent(); Student other = signupStudent();
        String guardian = signupGuardian(), stranger = signupGuardian(), teacher = signupTeacher();
        long exercise = jdbc.queryForObject("SELECT MIN(exercise_id) FROM exercises WHERE active=TRUE", Long.class);
        attempt(child, exercise, 0, 0);

        assertEquals(0, json(mvc.perform(get("/api/v1/guardians/me/children").header("Authorization", bearer(guardian))).andExpect(status().isOk())).size());
        // 코드 없이 학생 ID만으로는 연결·조회할 수 없다
        mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId).header("Authorization", bearer(guardian))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId + "/learning-report").header("Authorization", bearer(guardian))).andExpect(status().isNotFound());
        linkWith(guardian, "WRONG-CODE").andExpect(status().isBadRequest());

        String code = issueCode(child);
        assertTrue(code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM guardian_link_codes WHERE code_hash=?", Integer.class, code), "코드 원문은 저장하지 않는다");
        JsonNode linked = json(linkWith(guardian, " " + code.toLowerCase() + " ").andExpect(status().isCreated()));
        assertEquals(child.studentId, linked.path("studentId").asLong());
        linkWith(stranger, code).andExpect(status().isBadRequest()); // 1회용

        JsonNode detail = json(mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId).header("Authorization", bearer(guardian))).andExpect(status().isOk()));
        assertEquals("ACTIVE", detail.path("linkStatus").asText());
        assertTrue(detail.path("consents").size() >= 2, "자녀 계정의 동의 상태(보기만)");
        assertFalse(detail.toString().contains("guardianName"), "보호자 이름 등 동의 세부 정보는 내보내지 않는다");
        assertEquals(1, json(mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId + "/learning-report?period=7d").header("Authorization", bearer(guardian)))
                .andExpect(status().isOk())).path("totalAttempts").asLong());
        assertEquals(1, json(mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId + "/history").header("Authorization", bearer(guardian)))
                .andExpect(status().isOk())).path("totalElements").asLong());

        // ID를 바꿔 요청해도 다른 학생·다른 보호자의 자녀는 보이지 않는다
        mvc.perform(get("/api/v1/guardians/me/children/" + other.studentId).header("Authorization", bearer(guardian))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/guardians/me/children/" + other.studentId + "/history").header("Authorization", bearer(guardian))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/guardians/me/children/" + child.studentId).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        // 역할 분리: 학생·선생님은 보호자 API를, 보호자는 학생·선생님 API를 쓸 수 없다
        mvc.perform(get("/api/v1/guardians/me/children").header("Authorization", bearer(child.token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/guardians/me/children").header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(guardian))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teachers/me/students").header("Authorization", bearer(guardian))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/students/me/guardian-link-codes").header("Authorization", bearer(guardian))).andExpect(status().isForbidden());

        // 자녀 계정에서 보호자를 보고 해제하면 즉시 조회가 막힌다
        JsonNode guardians = json(mvc.perform(get("/api/v1/students/me/guardians").header("Authorization", bearer(child.token))).andExpect(status().isOk()));
        assertEquals(1, guardians.size());
        long guardianId = guardians.get(0).path("guardianId").asLong();
        mvc.perform(delete("/api/v1/students/me/guardians/" + guardianId).header("Authorization", bearer(other.token))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/students/me/guardians/" + guardianId).header("Authorization", bearer(child.token))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId + "/learning-report").header("Authorization", bearer(guardian))).andExpect(status().isNotFound());
        assertEquals("UNLINKED", jdbc.queryForObject("SELECT status FROM guardian_students WHERE student_id=?", String.class, child.studentId), "해제 이력은 남긴다");

        // 새 코드로 다시 연결 → 보호자 쪽에서 해제
        linkWith(guardian, issueCode(child)).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/guardians/me/children/" + child.studentId).header("Authorization", bearer(guardian))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId).header("Authorization", bearer(guardian))).andExpect(status().isNotFound());
    }

    @Test
    void linkCodesExpireAreReplacedAndGuessingIsLimited() throws Exception {
        Student child = signupStudent(); String guardian = signupGuardian();
        String first = issueCode(child), second = issueCode(child);
        linkWith(guardian, first).andExpect(status().isBadRequest()); // 새 코드를 만들면 이전 코드는 만료
        jdbc.update("UPDATE guardian_link_codes SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE student_id=?", child.studentId);
        linkWith(guardian, second).andExpect(status().isBadRequest()); // 10분이 지난 코드
        for (int i = 0; i < 3; i++) linkWith(guardian, "ABCD-EFGH").andExpect(status().isBadRequest());
        String valid = issueCode(child);
        linkWith(guardian, valid).andExpect(status().isTooManyRequests()); // 10분에 5회 실패 후에는 맞는 코드도 잠시 막힌다
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM guardian_students WHERE student_id=?", Integer.class, child.studentId));

        // 보호자 가입: 나이·학생 프로필·학생용 동의 없이, 개인정보 동의만
        String email = "guardian-" + UUID.randomUUID() + "@example.test";
        guardianSignup(email).andExpect(status().isCreated());
        assertEquals("GUARDIAN", jdbc.queryForObject("SELECT role FROM users WHERE email=?", String.class, email));
        assertNull(jdbc.queryForObject("SELECT age FROM users WHERE email=?", Integer.class, email));
        assertEquals(List.of("PRIVACY"), jdbc.queryForList("SELECT consent_type FROM user_consents uc JOIN users u USING(user_id) WHERE u.email=?", String.class, email));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles sp JOIN users u USING(user_id) WHERE u.email=?", Integer.class, email));
        JsonNode me = json(mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(login(email)))).andExpect(status().isOk()));
        assertEquals("GUARDIAN", me.path("role").asText());
    }

    // ── 도우미 ─────────────────────────────────────────────

    private void assertActive(JsonNode recommendations) {
        recommendations.path("items").forEach(item -> assertEquals(Boolean.TRUE,
                jdbc.queryForObject("SELECT active FROM exercises WHERE exercise_id=?", Boolean.class, item.path("exerciseId").asLong())));
    }

    private String issueCode(Student child) throws Exception {
        return json(mvc.perform(post("/api/v1/students/me/guardian-link-codes").header("Authorization", bearer(child.token))).andExpect(status().isCreated())).path("code").asText();
    }

    private ResultActions linkWith(String guardian, String code) throws Exception {
        return mvc.perform(post("/api/v1/guardians/me/children").header("Authorization", bearer(guardian)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    /** 문항 순서(itemIndex)의 문항을 daysAgo일 전에 녹음해 저장한 기록 1행 */
    private void attempt(Student student, long exerciseId, int itemIndex, int daysAgo) {
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=? ORDER BY sort_order,item_id LIMIT 1 OFFSET " + itemIndex, Long.class, exerciseId);
        jdbc.update("INSERT INTO practice_attempts(student_id,exercise_id,item_id,attempt_type,created_at) VALUES(?,?,?,'SELF',DATE_SUB(NOW(),INTERVAL ? DAY))",
                student.studentId, exerciseId, String.valueOf(itemId), daysAgo);
    }

    private JsonNode studentReport(Student student, String period) throws Exception {
        return json(mvc.perform(get("/api/v1/students/me/learning-status" + (period == null ? "" : "?period=" + period))
                .header("Authorization", bearer(student.token))).andExpect(status().isOk()));
    }

    private static long sum(JsonNode rows, String key) {
        long total = 0;
        for (JsonNode row : rows) total += row.path(key).asLong();
        return total;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private void link(String teacherToken, long studentId) throws Exception {
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + studentId + ",\"name\":\"리포트 학생\"}")).andExpect(status().isCreated());
    }

    private record Student(long studentId, String token) { }

    private Student signupStudent() throws Exception {
        String email = "lrg-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"리포트 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Student(studentId, login(email));
    }

    private String signupTeacher() throws Exception {
        String email = "lrg-t-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"리포트 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private ResultActions guardianSignup(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"GUARDIAN\",\"name\":\"보호자\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":40,"
                        + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"));
    }

    private String signupGuardian() throws Exception {
        String email = "lrg-g-" + UUID.randomUUID() + "@example.test";
        guardianSignup(email).andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
}
