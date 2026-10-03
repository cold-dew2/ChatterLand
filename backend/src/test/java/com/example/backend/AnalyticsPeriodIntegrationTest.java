package com.example.backend;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 교사 통계의 현재·이전 기간 비교. 기록을 기간 경계 시각(00:00:00, 23:59:59)과 바깥에 직접 넣어
 * 이전 기간 = 조회 기간과 같은 일수·바로 앞 기간이며, 경계 기록이 정확히 한 기간에만 들어가는지 확인한다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class AnalyticsPeriodIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    // 현재 기간 2026-09-08 ~ 2026-09-14(7일) → 이전 기간 2026-09-01 ~ 2026-09-07
    private static final LocalDate START = LocalDate.of(2026, 9, 8), END = LocalDate.of(2026, 9, 14);

    @Test
    void previousPeriodUsesTheSameLengthAndBoundariesAreNotDoubleCounted() throws Exception {
        Ids ids = setup();
        attempt(ids.studentId, "2026-08-31 23:59:59", 10);  // 두 기간 모두 밖
        attempt(ids.studentId, "2026-09-01 00:00:00", 60);  // 이전 기간 시작
        attempt(ids.studentId, "2026-09-07 23:59:59", 80);  // 이전 기간 끝
        attempt(ids.studentId, "2026-09-08 00:00:00", 90);  // 현재 기간 시작
        attempt(ids.studentId, "2026-09-14 23:59:59", 100); // 현재 기간 끝
        attempt(ids.studentId, "2026-09-15 00:00:00", 0);   // 두 기간 모두 밖
        homework(ids, "2026-09-07", "COMPLETED"); homework(ids, "2026-09-03", "PENDING");   // 이전: 1/2
        homework(ids, "2026-09-08", "COMPLETED"); homework(ids, "2026-09-14", "COMPLETED"); homework(ids, "2026-09-10", "PENDING"); // 현재: 2/3

        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", ids.studentId).param("startDate", START.toString()).param("endDate", END.toString())
                        .header("Authorization", "Bearer " + ids.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempts").value(2))
                .andExpect(jsonPath("$.averageMatchRate").value(95))
                .andExpect(jsonPath("$.homeworkTotal").value(3)).andExpect(jsonPath("$.homeworkCompleted").value(2))
                .andExpect(jsonPath("$.previousPeriod.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.previousPeriod.endDate").value("2026-09-07"))
                .andExpect(jsonPath("$.previousPeriod.totalAttempts").value(2))
                .andExpect(jsonPath("$.previousPeriod.averageMatchRate").value(70))
                .andExpect(jsonPath("$.previousPeriod.homeworkTotal").value(2)).andExpect(jsonPath("$.previousPeriod.homeworkCompleted").value(1))
                .andExpect(jsonPath("$.comparison.averageMatchRateChange").value(25))
                .andExpect(jsonPath("$.comparison.totalAttemptsChange").value(0))
                .andExpect(jsonPath("$.comparison.homeworkCompletionRate").value(67))
                .andExpect(jsonPath("$.comparison.previousHomeworkCompletionRate").value(50))
                .andExpect(jsonPath("$.comparison.homeworkCompletionRateChange").value(17));
    }

    @Test
    void emptyPreviousPeriodAndSingleDayRange() throws Exception {
        Ids ids = setup();
        attempt(ids.studentId, "2026-09-08 12:00:00", 80);
        // 하루 조회: 이전 기간은 전날 하루, 기록이 없으므로 변화량·완료율은 null
        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", ids.studentId).param("startDate", "2026-09-08").param("endDate", "2026-09-08")
                        .header("Authorization", "Bearer " + ids.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageMatchRate").value(80))
                .andExpect(jsonPath("$.previousPeriod.startDate").value("2026-09-07"))
                .andExpect(jsonPath("$.previousPeriod.endDate").value("2026-09-07"))
                .andExpect(jsonPath("$.previousPeriod.averageMatchRate").doesNotExist())
                .andExpect(jsonPath("$.comparison.averageMatchRateChange").doesNotExist())
                .andExpect(jsonPath("$.comparison.totalAttemptsChange").value(1))
                .andExpect(jsonPath("$.comparison.homeworkCompletionRate").doesNotExist())
                .andExpect(jsonPath("$.comparison.homeworkCompletionRateChange").doesNotExist());
        // 종료일이 시작일보다 앞서거나 1년을 넘으면 400
        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", ids.studentId).param("startDate", "2026-09-08").param("endDate", "2026-09-07")
                .header("Authorization", "Bearer " + ids.token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", ids.studentId).param("startDate", "2025-01-01").param("endDate", "2026-09-07")
                .header("Authorization", "Bearer " + ids.token)).andExpect(status().isBadRequest());
    }

    private void attempt(long studentId, String createdAt, int matchRate) {
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        jdbc.update("INSERT INTO practice_attempts(student_id,exercise_id,item_id,match_rate,attempt_type,created_at) VALUES(?,1,?,?,'PRACTICE',?)",
                studentId, String.valueOf(itemId), matchRate, createdAt);
    }

    private void homework(Ids ids, String dueDate, String status) {
        jdbc.update("INSERT INTO homeworks(teacher_id,student_id,title,type,due_date,status) VALUES(?,?,'경계 숙제','발음',?,?)", ids.teacherId, ids.studentId, dueDate, status);
    }

    private Ids setup() throws Exception {
        String studentEmail = "period-s-" + UUID.randomUUID() + "@example.test", teacherEmail = "period-t-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STUDENT\",\"name\":\"기간 학생\",\"email\":\"" + studentEmail + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                        + "\"consents\":{\"privacy\":true,\"voice\":false,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TEACHER\",\"name\":\"기간 선생님\",\"email\":\"" + teacherEmail + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, studentEmail);
        long teacherId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, teacherEmail);
        String token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + teacherEmail + "\",\"password\":\"Chatterland!234\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + studentId + ",\"name\":\"기간 학생\"}")).andExpect(status().isCreated());
        return new Ids(studentId, teacherId, token);
    }

    private record Ids(long studentId, long teacherId, String token) { }
}
