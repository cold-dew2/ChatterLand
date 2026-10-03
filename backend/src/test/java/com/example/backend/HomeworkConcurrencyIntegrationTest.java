package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 숙제 등록의 Idempotency-Key 중복 방지와 숙제 수정의 버전(낙관적 잠금) 충돌 처리를 실제 DB로 확인한다.
 * 동시 요청은 서로 다른 스레드·커넥션에서 실행되므로 테스트 트랜잭션(롤백)을 쓰지 않고, 만든 데이터를 @AfterEach에서 직접 지운다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
class HomeworkConcurrencyIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            Long userId = jdbc.query("SELECT user_id FROM users WHERE email=?", rs -> rs.next() ? rs.getLong(1) : null, email);
            if (userId == null) continue;
            Long studentId = jdbc.query("SELECT student_id FROM student_profiles WHERE user_id=?", rs -> rs.next() ? rs.getLong(1) : null, userId);
            jdbc.update("DELETE FROM homeworks WHERE teacher_id=? OR student_id=?", userId, studentId == null ? -1 : studentId);
            jdbc.update("DELETE FROM teacher_students WHERE teacher_id=? OR student_id=?", userId, studentId == null ? -1 : studentId);
            if (studentId != null) jdbc.update("DELETE FROM student_profiles WHERE student_id=?", studentId);
            jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        }
    }

    // ── 1. 숙제 중복 생성 방지 ─────────────────────────────

    @Test
    void retryWithTheSameKeyReturnsTheFirstHomeworkInsteadOfCreatingAnother() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        String key = UUID.randomUUID().toString();

        JsonNode first = json(create(teacher, key, body(student, "ㄹ 연습", 10)).andExpect(status().isCreated()).andReturn());
        // 재전송은 새로 만들지 않았으므로 201이 아니라 200으로 처음 숙제를 돌려준다.
        JsonNode retry = json(create(teacher, key, body(student, "ㄹ 연습", 10)).andExpect(status().isOk()).andReturn());

        assertEquals(first.path("homeworkId").asLong(), retry.path("homeworkId").asLong(), "응답이 유실된 뒤의 재시도는 같은 숙제를 돌려준다");
        assertFalse(first.has("reused"));
        assertTrue(retry.path("reused").asBoolean());
        assertEquals(0, retry.path("version").asInt());
        assertFalse(retry.has("requestHash"), "요청 지문은 응답에 노출하지 않는다");
        assertEquals(1, homeworkCount(teacher));
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOnlyOneHomework() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        String key = UUID.randomUUID().toString();
        String body = body(student, "동시 등록", 15);

        List<MvcResult> results = runConcurrently(6, () -> create(teacher, key, body).andReturn());

        Set<Long> ids = new HashSet<>();
        int created = 0;
        for (MvcResult result : results) {
            int code = result.getResponse().getStatus();
            assertTrue(code == 201 || code == 200, result.getResponse().getContentAsString());
            if (code == 201) created++;
            else assertTrue(json(result).path("reused").asBoolean());
            ids.add(json(result).path("homeworkId").asLong());
        }
        assertEquals(1, created, "실제로 만든 요청 하나만 201이다");
        assertEquals(1, ids.size(), "동시에 도착한 같은 요청은 모두 같은 숙제를 받아야 한다");
        assertEquals(1, homeworkCount(teacher));
    }

    @Test
    void theSameKeyWithDifferentContentIsRejectedWithoutChangingData() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        String key = UUID.randomUUID().toString();
        create(teacher, key, body(student, "처음 내용", 10)).andExpect(status().isCreated());

        create(teacher, key, body(student, "다른 내용", 10))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        create(teacher, key, body(student, "처음 내용", 20))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertEquals(1, homeworkCount(teacher));
        assertEquals("처음 내용", jdbc.queryForObject("SELECT title FROM homeworks WHERE teacher_id=?", String.class, teacher.userId));
    }

    @Test
    void differentKeysOrNoKeyCreateSeparateHomeworksAndKeysAreScopedPerTeacher() throws Exception {
        Account teacher = signupTeacher();
        Account other = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        link(other, student);
        String body = body(student, "같은 제목", 10);

        create(teacher, UUID.randomUUID().toString(), body).andExpect(status().isCreated());
        create(teacher, UUID.randomUUID().toString(), body).andExpect(status().isCreated());
        // 키 없는 기존 클라이언트: 기존처럼 매번 새로 만든다.
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher.token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        assertEquals(3, homeworkCount(teacher));

        // 다른 선생님이 같은 키를 써도 서로의 숙제를 돌려받거나 충돌하지 않는다.
        String shared = UUID.randomUUID().toString();
        long mine = json(create(teacher, shared, body).andExpect(status().isCreated()).andReturn()).path("homeworkId").asLong();
        JsonNode theirs = json(create(other, shared, body).andExpect(status().isCreated()).andReturn());
        assertNotEquals(mine, theirs.path("homeworkId").asLong());
        assertFalse(theirs.has("reused"));
        assertEquals(1, homeworkCount(other));
    }

    @Test
    void aFailedRequestDoesNotConsumeTheKeyAndInvalidKeysAreRejected() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        String key = UUID.randomUUID().toString();

        // 담당 학생이 아니어서 실패한 요청은 키를 남기지 않는다.
        create(teacher, key, body(student, "실패 후 재시도", 10)).andExpect(status().isForbidden());
        assertEquals(0, homeworkCount(teacher));
        link(teacher, student);
        JsonNode retried = json(create(teacher, key, body(student, "실패 후 재시도", 10)).andExpect(status().isCreated()).andReturn());
        assertFalse(retried.has("reused"), "실패한 요청 뒤의 재시도는 새로 저장된다");
        assertEquals(1, homeworkCount(teacher));

        create(teacher, "short", body(student, "형식 오류", 10)).andExpect(status().isBadRequest());
        create(teacher, "has space in key!", body(student, "형식 오류", 10)).andExpect(status().isBadRequest());
        assertEquals(1, homeworkCount(teacher));
    }

    // ── 2. 숙제 동시 수정 충돌 방지 ─────────────────────────

    @Test
    void anUpdateWithAStaleVersionIsRejectedAndDoesNotOverwrite() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        long id = json(create(teacher, UUID.randomUUID().toString(), body(student, "원래 제목", 10)).andReturn()).path("homeworkId").asLong();

        // 탭 A가 version 0으로 먼저 저장한다.
        update(teacher, id, "{\"title\":\"A가 바꾼 제목\",\"version\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.title").value("A가 바꾼 제목"));
        // 탭 B는 아직 version 0을 보고 있다: 덮어쓰지 않는다.
        update(teacher, id, "{\"title\":\"B가 바꾼 제목\",\"version\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertEquals("A가 바꾼 제목", jdbc.queryForObject("SELECT title FROM homeworks WHERE homework_id=?", String.class, id));

        // 최신 목록을 다시 불러오면 version 1을 받고, 그 버전으로는 저장된다.
        JsonNode list = json(mvc.perform(get("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher.token)))
                .andExpect(status().isOk()).andReturn());
        assertEquals(1, list.path("content").get(0).path("version").asInt());
        update(teacher, id, "{\"title\":\"B가 다시 저장\",\"version\":1}").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));

        // version은 필수다: 보내지 않으면 400이고 바뀌지 않는다.
        update(teacher, id, "{\"targetMinutes\":30}").andExpect(status().isBadRequest());
        assertEquals(2, jdbc.queryForObject("SELECT version FROM homeworks WHERE homework_id=?", Integer.class, id));
        // 없는 숙제는 버전과 관계없이 404다.
        update(teacher, id + 100000, "{\"title\":\"없음\",\"version\":0}").andExpect(status().isNotFound());
    }

    @Test
    void concurrentUpdatesWithTheSameVersionLetExactlyOneSucceed() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        long id = json(create(teacher, UUID.randomUUID().toString(), body(student, "동시 수정", 10)).andReturn()).path("homeworkId").asLong();

        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
        List<MvcResult> results = runConcurrently(5, () ->
                update(teacher, id, "{\"title\":\"동시 수정 " + counter.incrementAndGet() + "\",\"version\":0}").andReturn());
        long ok = results.stream().filter(r -> r.getResponse().getStatus() == 200).count();
        long conflict = results.stream().filter(r -> r.getResponse().getStatus() == 409).count();
        assertEquals(1, ok, "같은 버전으로 동시에 저장하면 하나만 반영된다");
        assertEquals(4, conflict);
        assertEquals(1, jdbc.queryForObject("SELECT version FROM homeworks WHERE homework_id=?", Integer.class, id));
    }

    @Test
    void teacherEditsRacingAStudentCompletionNeverFailWithAServerError() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        for (int round = 0; round < 3; round++) {
            long id = json(create(teacher, UUID.randomUUID().toString(), body(student, "경쟁 " + round, 10)).andReturn()).path("homeworkId").asLong();
            java.util.concurrent.atomic.AtomicInteger turn = new java.util.concurrent.atomic.AtomicInteger();
            List<MvcResult> results = runConcurrently(4, () -> turn.getAndIncrement() == 0
                    ? mvc.perform(patch("/api/v1/students/me/homeworks/{id}/complete", id).header("Authorization", bearer(student.token))).andReturn()
                    : update(teacher, id, "{\"targetMinutes\":" + (20 + turn.get()) + ",\"version\":0}").andReturn());
            int successes = 0;
            for (MvcResult result : results) {
                int code = result.getResponse().getStatus();
                assertTrue(code == 200 || code == 409, "동시 수정은 성공 또는 409여야 한다(500 금지): " + code + " " + result.getResponse().getContentAsString());
                if (code == 200) successes++;
            }
            assertEquals(successes, jdbc.queryForObject("SELECT version FROM homeworks WHERE homework_id=?", Integer.class, id),
                    "성공한 변경 수만큼만 버전이 올라간다(덮어쓴 변경 없음)");
        }
    }

    // ── 정책 1·4: 삭제 시 버전 비교, 삭제 후 요청 키 ─────────────

    @Test
    void deleteRequiresTheCurrentVersionAndDoesNotRemoveAChangedHomework() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        long id = json(create(teacher, UUID.randomUUID().toString(), body(student, "삭제 대상", 10)).andReturn()).path("homeworkId").asLong();
        update(teacher, id, "{\"title\":\"다른 탭이 고침\",\"version\":0}").andExpect(status().isOk());

        // 고치기 전 화면(version 0)에서 누른 삭제는 지우지 않는다.
        remove(teacher, id, "0").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/teachers/me/homeworks/{id}", id)
                .header("Authorization", bearer(teacher.token))).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM homeworks WHERE homework_id=?", Integer.class, id));
        remove(teacher, id, "1").andExpect(status().isNoContent());
        remove(teacher, id, "1").andExpect(status().isNotFound());
    }

    @Test
    void concurrentEditAndDeleteNeverBothApply() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        for (int round = 0; round < 3; round++) {
            long id = json(create(teacher, UUID.randomUUID().toString(), body(student, "수정·삭제 경쟁 " + round, 10)).andReturn()).path("homeworkId").asLong();
            java.util.concurrent.atomic.AtomicInteger turn = new java.util.concurrent.atomic.AtomicInteger();
            List<MvcResult> results = runConcurrently(2, () -> turn.getAndIncrement() == 0
                    ? remove(teacher, id, "0").andReturn()
                    : update(teacher, id, "{\"title\":\"경쟁 수정\",\"version\":0}").andReturn());
            List<Integer> codes = results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
            int remaining = jdbc.queryForObject("SELECT COUNT(*) FROM homeworks WHERE homework_id=?", Integer.class, id);
            // 둘 중 하나만 반영된다: 삭제가 이기면 수정은 404, 수정이 이기면 삭제는 409(숙제 남음).
            if (remaining == 0) assertEquals(List.of(204, 404), codes);
            else {
                assertEquals(List.of(200, 409), codes);
                assertEquals("경쟁 수정", jdbc.queryForObject("SELECT title FROM homeworks WHERE homework_id=?", String.class, id));
            }
        }
    }

    /** 현재 정책의 한계: 요청 키는 숙제와 함께 지워지므로, 삭제 뒤 같은 키로 늦게 도착한 재시도는 새 숙제를 만든다. */
    @Test
    void aKeyIsKeptWhileTheHomeworkExistsAndReleasedWhenItIsDeleted() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        String key = UUID.randomUUID().toString();
        long first = json(create(teacher, key, body(student, "키 수명", 10)).andExpect(status().isCreated()).andReturn()).path("homeworkId").asLong();
        assertEquals(key, jdbc.queryForObject("SELECT request_key FROM homeworks WHERE homework_id=?", String.class, first));
        // 수정해도 키는 유지되고, 같은 키·같은 내용 재전송은 여전히 처음 숙제를 돌려준다.
        update(teacher, first, "{\"targetMinutes\":25,\"version\":0}").andExpect(status().isOk());
        create(teacher, key, body(student, "키 수명", 10)).andExpect(status().isOk()).andExpect(jsonPath("$.homeworkId").value(first));
        assertEquals(1, homeworkCount(teacher));

        remove(teacher, first, "1").andExpect(status().isNoContent());
        long second = json(create(teacher, key, body(student, "키 수명", 10)).andExpect(status().isCreated()).andReturn()).path("homeworkId").asLong();
        assertNotEquals(first, second);
        assertEquals(1, homeworkCount(teacher));
    }

    @Test
    void studentCompletionBumpsTheVersionSoAStaleTeacherEditConflicts() throws Exception {
        Account teacher = signupTeacher();
        Account student = signupStudent();
        link(teacher, student);
        long id = json(create(teacher, UUID.randomUUID().toString(), body(student, "완료 경쟁", 10)).andReturn()).path("homeworkId").asLong();

        mvc.perform(patch("/api/v1/students/me/homeworks/{id}/complete", id).header("Authorization", bearer(student.token)))
                .andExpect(status().is2xxSuccessful());
        // 선생님 화면은 완료 전(version 0)을 보고 '미완료'로 바꾸려 한다: 학생의 완료를 되돌리지 않는다.
        update(teacher, id, "{\"done\":false,\"version\":0}").andExpect(status().isConflict());
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM homeworks WHERE homework_id=?", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM homeworks WHERE homework_id=?", Integer.class, id));
    }

    // ── helpers ─────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions create(Account teacher, String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher.token))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions update(Account teacher, long id, String body) throws Exception {
        return mvc.perform(patch("/api/v1/teachers/me/homeworks/{id}", id).header("Authorization", bearer(teacher.token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions remove(Account teacher, long id, String version) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/teachers/me/homeworks/{id}", id)
                .param("version", version).header("Authorization", bearer(teacher.token)));
    }

    private String body(Account student, String title, int minutes) {
        return "{\"studentId\":" + student.studentId + ",\"title\":\"" + title + "\",\"type\":\"말하기 연습\",\"dueDate\":\""
                + LocalDate.now().plusDays(3) + "\",\"targetMinutes\":" + minutes + ",\"description\":\"설명\"}";
    }

    private int homeworkCount(Account teacher) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM homeworks WHERE teacher_id=?", Integer.class, teacher.userId);
    }

    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) futures.add(pool.submit(() -> { start.await(); return task.call(); }));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void link(Account teacher, Account student) throws Exception {
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacher.token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + student.studentId + ",\"name\":\"동시성 학생\"}"))
                .andExpect(status().isCreated());
    }

    private Account signupStudent() throws Exception {
        String email = "hw-student-" + UUID.randomUUID() + "@example.test";
        createdEmails.add(email);
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"동시성 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=?", Long.class, userId);
        return new Account(userId, login(email), studentId);
    }

    private Account signupTeacher() throws Exception {
        String email = "hw-teacher-" + UUID.randomUUID() + "@example.test";
        createdEmails.add(email);
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"동시성 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return new Account(jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email), login(email), 0);
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record Account(long userId, String token, long studentId) { }
}
