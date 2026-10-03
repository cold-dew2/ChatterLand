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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 인증·인가 경계 테스트. 미인증, 역할 위반(학생↔선생님), 담당 범위 밖 학생, 다른 학생의 데이터, 존재하지 않는 ID,
 * 잘못된 요청, 응답의 민감 정보 노출을 확인한다. 음성 분석 행은 Whisper 없이 DB에 직접 만든다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class AuthorizationIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void protectedApisRequireAuthenticationAndRejectForgedTokens() throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/v1/auth/me"), get("/api/v1/students/me"), get("/api/v1/students/me/history"),
                get("/api/v1/teachers/me/students"), get("/api/v1/consents/me"), get("/api/v1/speech/analyses/" + UUID.randomUUID()),
                post("/api/v1/practice/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/v1/teachers/me/speech-analyses/" + UUID.randomUUID() + "/audio"));
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotCallTeacherApisAndTeacherCannotCallStudentApis() throws Exception {
        Account student = signupStudent();
        Account teacher = signupTeacher();
        String today = LocalDate.now().toString();
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/teachers/me/students"), get("/api/v1/teachers/me/homeworks"),
                get("/api/v1/teachers/me/students/" + student.studentId + "/analytics").param("startDate", today).param("endDate", today),
                get("/api/v1/teachers/me/students/" + student.studentId + "/speech-analyses"),
                patch("/api/v1/teachers/me/speech-analyses/" + UUID.randomUUID() + "/review").contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"ACCEPTABLE\"}"))) {
            mvc.perform(request.header("Authorization", bearer(student.token))).andExpect(status().isForbidden());
        }
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/students/me"), get("/api/v1/students/me/homeworks"), get("/api/v1/speech/analyses/" + UUID.randomUUID()),
                post("/api/v1/practice/attempts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"1\",\"itemId\":\"1\",\"audioId\":\"" + UUID.randomUUID() + "\"}"))) {
            mvc.perform(request.header("Authorization", bearer(teacher.token))).andExpect(status().isForbidden());
        }
    }

    @Test
    void studentCannotReadOrUseAnotherStudentsAnalysisOrHomework() throws Exception {
        Account owner = signupStudent();
        Account other = signupStudent();
        String analysisId = insertCompletedAnalysis(owner.studentId);
        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analysisId").value(analysisId)).andExpect(jsonPath("$.audioPath").doesNotExist());
        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/speech/analyses/{id}", UUID.randomUUID().toString()).header("Authorization", bearer(owner.token)))
                .andExpect(status().isNotFound());
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(other.token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"1\",\"itemId\":\"" + itemId + "\",\"audioId\":\"" + analysisId + "\"}"))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempts WHERE analysis_id=?", Integer.class, analysisId));

        Account teacher = signupTeacher();
        link(teacher, owner);
        long homeworkId = createHomework(teacher, owner.studentId);
        mvc.perform(patch("/api/v1/students/me/homeworks/{id}/complete", homeworkId).header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM homeworks WHERE homework_id=?", String.class, homeworkId));
    }

    @Test
    void teacherCanOnlyAccessLinkedStudents() throws Exception {
        Account student = signupStudent();
        Account assigned = signupTeacher();
        Account outsider = signupTeacher();
        link(assigned, student);
        String analysisId = insertCompletedAnalysis(student.studentId);
        String today = LocalDate.now().toString();

        mvc.perform(get("/api/v1/teachers/me/students/{id}", student.studentId).header("Authorization", bearer(assigned.token))).andExpect(status().isOk());
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/teachers/me/students/" + student.studentId),
                get("/api/v1/teachers/me/students/" + student.studentId + "/sessions"),
                get("/api/v1/teachers/me/students/" + student.studentId + "/analytics").param("startDate", today).param("endDate", today),
                get("/api/v1/teachers/me/students/" + student.studentId + "/report").param("startDate", today).param("endDate", today),
                get("/api/v1/teachers/me/students/" + student.studentId + "/report/download").param("startDate", today).param("endDate", today),
                get("/api/v1/teachers/me/students/" + student.studentId + "/speech-analyses"))) {
            mvc.perform(request.header("Authorization", bearer(outsider.token))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/audio", analysisId).header("Authorization", bearer(outsider.token)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", bearer(outsider.token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"ACCEPTABLE\"}"))
                .andExpect(status().isNotFound());
        assertNull(jdbc.queryForObject("SELECT teacher_judgement FROM speech_analyses WHERE analysis_id=?", String.class, analysisId));

        int before = jdbc.queryForObject("SELECT COUNT(*) FROM homeworks WHERE student_id=?", Integer.class, student.studentId);
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(outsider.token)).contentType(MediaType.APPLICATION_JSON)
                        .content(homeworkBody(student.studentId)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/teachers/me/students/{id}", student.studentId).header("Authorization", bearer(outsider.token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"변경 시도\",\"learnerType\":\"THERAPY\"}"))
                .andExpect(status().isForbidden());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM homeworks WHERE student_id=?", Integer.class, student.studentId));
        assertEquals("GENERAL", jdbc.queryForObject("SELECT learner_type FROM student_profiles WHERE student_id=?", String.class, student.studentId));
        // 다른 선생님의 숙제는 수정·삭제할 수 없다.
        long homeworkId = createHomework(assigned, student.studentId);
        mvc.perform(delete("/api/v1/teachers/me/homeworks/{id}", homeworkId).param("version", "0").header("Authorization", bearer(outsider.token))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/teachers/me/homeworks/{id}", homeworkId).header("Authorization", bearer(outsider.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"남의 숙제\",\"version\":0}")).andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM homeworks WHERE homework_id=?", Integer.class, homeworkId));
    }

    @Test
    void malformedRequestsAreRejectedWithClientErrors() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"짧은비번\",\"email\":\"x@example.test\",\"password\":\"short\",\"centerId\":1,\"termsAgreed\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher.token)).contentType(MediaType.APPLICATION_JSON)
                        .content(homeworkBody(student.studentId).replace(LocalDate.now().plusDays(3).toString(), "2000-01-01")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"1\",\"itemId\":\"1\",\"audioId\":\"x\",\"score\":150}"))
                .andExpect(status().isBadRequest());
        // 숙제 수정: 공백만 있는 제목은 저장하지 않는다(생성과 같은 규칙).
        long homeworkId = createHomework(teacher, student.studentId);
        mvc.perform(patch("/api/v1/teachers/me/homeworks/{id}", homeworkId).header("Authorization", bearer(teacher.token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \",\"version\":0}"))
                .andExpect(status().isBadRequest());
        // version은 수정·삭제 모두 필수다(누락 시 400, 변경 없음).
        mvc.perform(patch("/api/v1/teachers/me/homeworks/{id}", homeworkId).header("Authorization", bearer(teacher.token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"버전 없음\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/teachers/me/homeworks/{id}", homeworkId).header("Authorization", bearer(teacher.token)))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/teachers/me/homeworks/{id}", homeworkId).param("version", "abc").header("Authorization", bearer(teacher.token)))
                .andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM homeworks WHERE homework_id=?", Integer.class, homeworkId));
        assertEquals("ㄹ 연습", jdbc.queryForObject("SELECT title FROM homeworks WHERE homework_id=?", String.class, homeworkId));
        // 없는 경로·지원하지 않는 메서드·형식이 틀린 경로 변수는 서버 오류(500)가 아니라 각각 404·405·400이어야 한다.
        mvc.perform(get("/api/v1/students/me/homeworks/abc").header("Authorization", bearer(student.token)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/students/me").header("Authorization", bearer(student.token)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/v1/students/me/homeworks/abc/complete").header("Authorization", bearer(student.token)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token)).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void corsPreflightAllowsTheIdempotencyKeyHeaderForTheWebOrigin() throws Exception {
        // 화면(다른 출처)이 Idempotency-Key 헤더를 보내려면 사전 요청에서 허용되어야 한다. 빠지면 브라우저가 분석 요청을 막는다.
        mvc.perform(options("/api/v1/speech/analyze").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
    }

    @Test
    void wrongPasswordAndUnknownAccountGetTheSameFailure() throws Exception {
        Account student = signupStudent();
        String wrong = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + student.email + "\",\"password\":\"Wrong-Password-1\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.test\",\"password\":\"Wrong-Password-1\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(objectMapper.readTree(wrong).path("message").asText(), objectMapper.readTree(unknown).path("message").asText());
    }

    @Test
    void responsesDoNotExposePasswordHashesOrTokensOfOthers() throws Exception {
        Account student = signupStudent();
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + student.email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String me = mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String profile = mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String body : List.of(login, me, profile)) {
            assertFalse(body.contains("$2a$") || body.contains("$2b$"), "BCrypt 해시가 응답에 포함됨");
            assertFalse(body.toLowerCase().contains("passwordhash") || body.contains("\"password\""), body);
            assertFalse(body.contains("tokenHash") || body.contains("token_hash"), body);
        }
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Account student = signupStudent();
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + student.email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String refresh = objectMapper.readTree(login).path("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String insertCompletedAnalysis(long studentId) {
        String id = UUID.randomUUID().toString();
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                "pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                "VALUES(?,?,1,?,'audio/wav','COMPLETED','PRONUNCIATION_REVIEW','라디오','라디오',NULL,'NOT_EVALUATED','PENDING','WORD','NO_CANDIDATES','{}','jamo-align-v1',NOW())",
                id, studentId, String.valueOf(itemId));
        return id;
    }

    private String homeworkBody(long studentId) {
        return "{\"studentId\":" + studentId + ",\"title\":\"ㄹ 연습\",\"type\":\"발음\",\"dueDate\":\"" + LocalDate.now().plusDays(3) + "\",\"targetMinutes\":10}";
    }

    private long createHomework(Account teacher, long studentId) throws Exception {
        String body = mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher.token))
                        .contentType(MediaType.APPLICATION_JSON).content(homeworkBody(studentId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("homeworkId").asLong();
    }

    private void link(Account teacher, Account student) throws Exception {
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacher.token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + student.studentId + ",\"name\":\"권한 학생\"}"))
                .andExpect(status().isCreated());
    }

    private Account signupStudent() throws Exception {
        String email = "authz-student-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"권한 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Account(email, login(email), studentId);
    }

    private Account signupTeacher() throws Exception {
        String email = "authz-teacher-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"권한 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return new Account(email, login(email), 0);
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode payload = objectMapper.readTree(response);
        return payload.path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record Account(String email, String token, long studentId) { }
}
