package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class BackendApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void studentSignupAuthenticatesAndReadsDatabasePracticeCatalog() throws Exception {
        String email = "integration-student-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"통합 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"},\"age\":7}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.studentId").isNumber());

        String token = login(email, "STUDENT");
        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("통합 학생"))
                .andExpect(jsonPath("$.streak").isNumber());
        mvc.perform(get("/api/v1/practice/categories").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/v1/practice/articulation/exercises").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].items.length()").isNumber());
        mvc.perform(get("/api/v1/teachers/me/students").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanManageAssignedStudentHomeworkAndAnalytics() throws Exception {
        String email = "integration-teacher-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"통합 치료사\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        String token = login(email, "TEACHER");

        String studentBody = mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"담당 학생\",\"age\":8,\"status\":\"ACTIVE\",\"sessionsTotal\":12}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long studentId = objectMapper.readTree(studentBody).path("studentId").asLong();
        mvc.perform(put("/api/v1/teachers/me/students/{id}", studentId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정된 담당 학생\",\"age\":8,\"status\":\"ACTIVE\",\"sessionsTotal\":16}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("수정된 담당 학생"));

        String homeworkBody = mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"title\":\"통합 숙제\",\"type\":\"발음\",\"dueDate\":\"2030-01-01\",\"targetMinutes\":10}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long homeworkId = objectMapper.readTree(homeworkBody).path("homeworkId").asLong();

        mvc.perform(patch("/api/v1/teachers/me/homeworks/{id}", homeworkId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", studentId)
                        .param("startDate", "2026-09-01").param("endDate", "2026-10-01")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.areaScores.length()").value(5))
                .andExpect(jsonPath("$.homeworkTotal").isNumber()).andExpect(jsonPath("$.homeworkCompleted").isNumber());
        mvc.perform(delete("/api/v1/teachers/me/homeworks/{id}", homeworkId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/teachers/me/students/{id}", studentId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void studentCanViewAssignedHomeworkAndMarkItComplete() throws Exception {
        String studentEmail = "ihs-" + UUID.randomUUID() + "@example.test";
        var signupResult = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"숙제 학생\",\"email\":\"" + studentEmail + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"},\"age\":8}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(201, signupResult.getResponse().getStatus(), signupResult.getResponse().getContentAsString());
        String studentSignup = signupResult.getResponse().getContentAsString();
        long studentId = objectMapper.readTree(studentSignup).path("studentId").asLong();
        String teacherEmail = "iht-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"숙제 치료사\",\"email\":\"" + teacherEmail + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        String teacherToken = login(teacherEmail, "TEACHER");
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"name\":\"숙제 학생\"}"))
                .andExpect(status().isCreated());
        String homeworkBody = mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"title\":\"오늘의 발음 연습\",\"type\":\"발음\",\"dueDate\":\"2030-01-01\",\"targetMinutes\":10}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long homeworkId = objectMapper.readTree(homeworkBody).path("homeworkId").asLong();
        String studentToken = login(studentEmail, "STUDENT");

        mvc.perform(get("/api/v1/students/me/homeworks").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].homeworkId").value(homeworkId))
                .andExpect(jsonPath("$.content[0].done").value(false));
        mvc.perform(patch("/api/v1/students/me/homeworks/{id}/complete", homeworkId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.done").value(true));
        mvc.perform(get("/api/v1/teachers/me/homeworks").param("studentId", String.valueOf(studentId))
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    void practiceAttemptIsBoundToItsOwnedCompletedAnalysisAndRetriesAreIdempotent() throws Exception {
        String email = "attempt-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"연습 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"},\"age\":8}"))
                .andExpect(status().isCreated());
        String token = login(email, "STUDENT");
        String exerciseBody = mvc.perform(get("/api/v1/practice/articulation/exercises").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode exercise = objectMapper.readTree(exerciseBody).path("content").get(0);
        long exerciseId = exercise.path("exerciseId").asLong();
        String itemId = exercise.path("items").get(0).path("itemId").asText();
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        String analysisId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_path,audio_mime,status,pronunciation_score,speech_rate_score,fluency_score,overall_score,transcript,feedback,completed_at) VALUES(?,?,?,?,?,'audio/webm','COMPLETED',70,75,74,73,'라디오','연습용 피드백',CURRENT_TIMESTAMP)",
                analysisId, studentId, exerciseId, itemId, "test/audio.webm");
        String body = "{\"exerciseId\":\"" + exerciseId + "\",\"itemId\":\"" + itemId + "\",\"score\":73,\"audioId\":\"" + analysisId + "\"}";
        String first = mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(73)).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(73)).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(objectMapper.readTree(first).path("attemptId").asLong(), objectMapper.readTree(second).path("attemptId").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempts WHERE analysis_id=?", Integer.class, analysisId));

        String changedScoreBody = body.replace("\"score\":73", "\"score\":1");
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(changedScoreBody))
                .andExpect(status().isConflict());

        String wrongItemBody = "{\"exerciseId\":\"" + exerciseId + "\",\"itemId\":\"999999999\",\"score\":73,\"audioId\":\"" + analysisId + "\"}";
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(wrongItemBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherCannotReadStudentAssignedToAnotherTeacher() throws Exception {
        String studentEmail = "owner-student-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"소유권 학생\",\"email\":\"" + studentEmail + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"},\"age\":8}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, studentEmail);
        String ownerEmail = "owner-teacher-" + UUID.randomUUID() + "@example.test";
        String otherEmail = "other-teacher-" + UUID.randomUUID() + "@example.test";
        signupTeacher(ownerEmail);
        signupTeacher(otherEmail);
        String ownerToken = login(ownerEmail, "TEACHER");
        String otherToken = login(otherEmail, "TEACHER");
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + studentId + ",\"name\":\"소유권 학생\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/teachers/me/students/{studentId}", studentId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshTokenCanOnlyBeRotatedOnce() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"토큰 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}," + "\"age\":8}"))
                .andExpect(status().isCreated());
        String loginResponse = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginResponse).path("refreshToken").asText();
        String refreshBody = "{\"refreshToken\":\"" + refreshToken + "\"}";
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exerciseItemSeedIsNotDuplicatedOnRestart() {
        // 서버를 여러 번 시작해도(테스트마다 schema.sql 실행) 문항이 늘어나지 않아야 한다.
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT exercise_id,sort_order FROM exercise_items GROUP BY exercise_id,sort_order HAVING COUNT(*)>1) dup", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM exercise_items WHERE exercise_id=4", Integer.class));
    }

    private void signupTeacher(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"권한 치료사\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
    }

    private String login(String email, String role) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode payload = objectMapper.readTree(response);
        org.junit.jupiter.api.Assertions.assertEquals(role, payload.path("user").path("role").asText());
        return payload.path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
