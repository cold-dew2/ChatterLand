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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보호자 기능 정책: 연결·해제 알림(연결 기록 기반, 중복·실패 시 미생성·권한), 해제 후 접근 차단과 학습 기록 보존,
 * 보호자의 동의 변경 차단, 보호자 아이디 찾기 비노출, 회원가입 센터 필수·임의 센터 방지.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class GuardianPolicyIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void childGetsOneNoticePerLinkAndUnlinkAndNoneForFailedAttempts() throws Exception {
        Student child = signupStudent(); Student other = signupStudent(); String guardian = signupGuardian("김보호");
        assertEquals(0, events(child).size());

        // 실패한 연결 시도(잘못된 코드·만료된 코드)에는 알림이 없다
        link(guardian, "ABCD-EFGH").andExpect(status().isBadRequest());
        String expired = issueCode(child);
        jdbc.update("UPDATE guardian_link_codes SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE student_id=?", child.studentId);
        link(guardian, expired).andExpect(status().isBadRequest());
        assertEquals(0, events(child).size());

        // 연결 성공 → 연결 알림 1개(보호자 이름·시각)
        String code = issueCode(child);
        link(guardian, code).andExpect(status().isCreated());
        JsonNode afterLink = events(child);
        assertEquals(1, afterLink.size());
        assertEquals("GUARDIAN_LINKED", afterLink.get(0).path("type").asText());
        assertEquals("김보호", afterLink.get(0).path("guardianName").asText());
        String linkedKey = afterLink.get(0).path("key").asText();

        // 같은 코드 재사용(이미 처리된 요청)·이미 연결된 상태에서 새 코드로 다시 연결해도 알림은 늘지 않는다
        link(guardian, code).andExpect(status().isBadRequest());
        link(guardian, issueCode(child)).andExpect(status().isCreated());
        JsonNode repeated = events(child);
        assertEquals(1, repeated.size(), "같은 연결에 알림이 다시 생기지 않는다");
        assertEquals(linkedKey, repeated.get(0).path("key").asText());

        // 자녀가 해제 → 해제 알림(해제 주체 STUDENT). 같은 해제를 다시 요청해도(404) 알림은 그대로
        long guardianId = json(mvc.perform(get("/api/v1/students/me/guardians").header("Authorization", bearer(child.token)))).get(0).path("guardianId").asLong();
        mvc.perform(delete("/api/v1/students/me/guardians/" + guardianId).header("Authorization", bearer(child.token))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/students/me/guardians/" + guardianId).header("Authorization", bearer(child.token))).andExpect(status().isNotFound());
        JsonNode afterUnlink = events(child);
        assertEquals(List.of("GUARDIAN_LINKED", "GUARDIAN_UNLINKED"), types(afterUnlink).stream().sorted().toList());
        JsonNode unlinked = find(afterUnlink, "GUARDIAN_UNLINKED");
        assertEquals("STUDENT", unlinked.path("unlinkedBy").asText());
        // 연결 상태와 알림이 같은 기록에서 나온다(일관성)
        assertEquals("UNLINKED", jdbc.queryForObject("SELECT status FROM guardian_students WHERE student_id=?", String.class, child.studentId));

        // 권한: 다른 학생에게는 보이지 않고, 보호자·선생님은 이 API를 쓸 수 없다
        assertEquals(0, events(other).size());
        mvc.perform(get("/api/v1/students/me/guardian-events").header("Authorization", bearer(guardian))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/me/guardian-events").header("Authorization", bearer(signupTeacher()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/me/guardian-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void guardianSideUnlinkIsReportedAndRecordsStayAfterUnlink() throws Exception {
        Student child = signupStudent(); String guardian = signupGuardian("이보호");
        long exercise = jdbc.queryForObject("SELECT MIN(exercise_id) FROM exercises WHERE active=TRUE", Long.class);
        long item = jdbc.queryForObject("SELECT MIN(item_id) FROM exercise_items WHERE exercise_id=?", Long.class, exercise);
        jdbc.update("INSERT INTO practice_attempts(student_id,exercise_id,item_id,attempt_type) VALUES(?,?,?,'SELF')", child.studentId, exercise, String.valueOf(item));
        link(guardian, issueCode(child)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId + "/history").header("Authorization", bearer(guardian))).andExpect(status().isOk());
        long attemptsBefore = attempts(child);

        // 보호자가 해제 → 같은 토큰으로도 즉시 모든 자녀 API 차단
        mvc.perform(delete("/api/v1/guardians/me/children/" + child.studentId).header("Authorization", bearer(guardian))).andExpect(status().isNoContent());
        for (String path : new String[]{"", "/learning-report", "/learning-report?period=7d", "/history"})
            mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId + path).header("Authorization", bearer(guardian))).andExpect(status().isNotFound());
        assertEquals(0, json(mvc.perform(get("/api/v1/guardians/me/children").header("Authorization", bearer(guardian)))).size());
        assertEquals("GUARDIAN", find(events(child), "GUARDIAN_UNLINKED").path("unlinkedBy").asText());

        // 해제는 연결만 끝낸다: 자녀의 학습 기록·계정은 그대로
        assertEquals(attemptsBefore, attempts(child), "연결 해제가 학습 기록을 지우지 않는다");
        assertEquals(1, json(mvc.perform(get("/api/v1/students/me/history").header("Authorization", bearer(child.token)))).path("totalElements").asLong());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM guardian_students WHERE student_id=?", Integer.class, child.studentId), "연결 이력 행도 남는다");
    }

    @Test
    void guardianCanOnlyViewChildConsentsAndCannotChangeThem() throws Exception {
        Student child = signupStudent(); String guardian = signupGuardian("박보호");
        link(guardian, issueCode(child)).andExpect(status().isCreated());
        JsonNode before = json(mvc.perform(get("/api/v1/consents/me").header("Authorization", bearer(child.token))));
        assertTrue(json(mvc.perform(get("/api/v1/guardians/me/children/" + child.studentId).header("Authorization", bearer(guardian)))).path("consents").size() >= 2);

        // 동의 변경 API는 언제나 로그인한 본인 계정만 바꾼다. 보호자 계정에는 자녀용 동의 항목이 없어 거절되고, 필수 동의 철회도 막힌다.
        for (String type : new String[]{"AI_CHAT", "AI_FEEDBACK", "VOICE", "GUARDIAN"})
            consentUpdate(guardian, type, true).andExpect(status().isBadRequest());
        consentUpdate(guardian, "PRIVACY", false).andExpect(status().isBadRequest());
        // 자녀 ID를 붙인 경로나 다른 방식의 쓰기 API는 없다
        for (String path : new String[]{"/api/v1/guardians/me/children/" + child.studentId + "/consents/AI_CHAT", "/api/v1/consents/" + child.studentId + "/AI_CHAT"}) {
            int code = mvc.perform(put(path).header("Authorization", bearer(guardian)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"agreed\":true,\"policyVersion\":\"2026-10-01\"}")).andReturn().getResponse().getStatus();
            assertTrue(code == 404 || code == 405 || code == 403, path + " → " + code);
        }
        assertEquals(before, json(mvc.perform(get("/api/v1/consents/me").header("Authorization", bearer(child.token)))), "자녀 동의는 그대로");
    }

    @Test
    void guardianFindIdIsNotOfferedAndRevealsNothing() throws Exception {
        String name = "찾기보호자" + UUID.randomUUID().toString().substring(0, 6);
        signupGuardianNamed(name);
        // 같은 이름의 보호자가 있든 없든 똑같이 거절(계정 존재 여부가 드러나지 않음)
        ResultActions existing = findId(name, "GUARDIAN"), missing = findId("없는보호자" + UUID.randomUUID().toString().substring(0, 6), "GUARDIAN");
        existing.andExpect(status().isBadRequest()); missing.andExpect(status().isBadRequest());
        assertEquals(json(existing).path("code").asText(), json(missing).path("code").asText());
        assertFalse(existing.andReturn().getResponse().getContentAsString().contains("@"));
        // 학생·선생님 아이디 찾기(기존 흐름)는 그대로 동작한다
        String student = "찾기학생" + UUID.randomUUID().toString().substring(0, 6);
        signupStudentNamed(student);
        findId(student, "STUDENT").andExpect(status().isOk());
    }

    /**
     * KNOWN RISK(현재 동작 기록): 학생·선생님 아이디 찾기는 이름·센터가 맞는 계정이 있으면 200(가린 이메일), 없으면 404라
     * 계정 존재 여부가 드러난다. 안전한 방식(예: 결과를 등록 이메일로만 보냄)은 정책 결정 후 바꾸고, 그때 이 테스트를 반대로 바꾼다.
     */
    @Test
    void knownRisk_studentAndTeacherFindIdRevealsWhetherAnAccountExists() throws Exception {
        String name = "존재확인학생" + UUID.randomUUID().toString().substring(0, 6);
        signupStudentNamed(name);
        findId(name, "STUDENT").andExpect(status().isOk());
        findId("없는학생" + UUID.randomUUID().toString().substring(0, 6), "STUDENT").andExpect(status().isNotFound());
    }

    @Test
    void signupRequiresAnActiveCenterChosenByTheUserAndNeverAssignsOne() throws Exception {
        long usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        for (String role : new String[]{"STUDENT", "TEACHER", "GUARDIAN"}) {
            // 센터를 보내지 않으면 기본 센터를 넣지 않고 거절
            signup(role, "센터없음", null).andExpect(status().isBadRequest());
            // 없는 센터·비활성 센터는 거절
            signup(role, "없는센터", 999999L).andExpect(status().isBadRequest());
        }
        assertEquals(usersBefore, jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class), "실패한 가입은 계정을 만들지 않는다");
        // 보호자의 자녀 접근은 센터가 아니라 연결로만 정해진다: 같은 센터의 학생이라도 연결 없이는 볼 수 없다
        Student sameCenter = signupStudent(); String guardian = signupGuardian("센터같은보호자");
        mvc.perform(get("/api/v1/guardians/me/children/" + sameCenter.studentId).header("Authorization", bearer(guardian))).andExpect(status().isNotFound());
    }

    // ── 도우미 ─────────────────────────────────────────────

    private JsonNode events(Student student) throws Exception {
        return json(mvc.perform(get("/api/v1/students/me/guardian-events").header("Authorization", bearer(student.token))).andExpect(status().isOk()));
    }

    private static List<String> types(JsonNode events) {
        List<String> types = new ArrayList<>();
        events.forEach(event -> types.add(event.path("type").asText()));
        return types;
    }

    private static JsonNode find(JsonNode events, String type) {
        for (JsonNode event : events) if (type.equals(event.path("type").asText())) return event;
        throw new AssertionError(type + " 알림이 없습니다: " + events);
    }

    private long attempts(Student student) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempts WHERE student_id=?", Long.class, student.studentId);
    }

    private String issueCode(Student child) throws Exception {
        return json(mvc.perform(post("/api/v1/students/me/guardian-link-codes").header("Authorization", bearer(child.token))).andExpect(status().isCreated())).path("code").asText();
    }

    private ResultActions link(String guardian, String code) throws Exception {
        return mvc.perform(post("/api/v1/guardians/me/children").header("Authorization", bearer(guardian)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions consentUpdate(String token, String type, boolean agreed) throws Exception {
        return mvc.perform(put("/api/v1/consents/me/" + type).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"agreed\":" + agreed + ",\"policyVersion\":\"2026-10-01\"}"));
    }

    private ResultActions findId(String name, String role) throws Exception {
        return mvc.perform(post("/api/v1/auth/find-id").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"centerId\":1,\"role\":\"" + role + "\"}"));
    }

    private ResultActions signup(String role, String name, Long centerId) throws Exception {
        String email = "gp-" + UUID.randomUUID() + "@example.test";
        String student = "STUDENT".equals(role)
                ? ",\"age\":8,\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}"
                : ",\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}";
        return mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\",\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                        + (centerId == null ? "" : "\"centerId\":" + centerId + ",") + "\"termsAgreed\":true" + student + "}"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Student(long studentId, String token) { }

    private Student signupStudent() throws Exception { return signupStudentNamed("정책 학생"); }

    private Student signupStudentNamed(String name) throws Exception {
        String email = "gp-s-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Student(studentId, login(email));
    }

    private String signupTeacher() throws Exception {
        String email = "gp-t-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"정책 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String signupGuardian(String name) throws Exception { return login(signupGuardianNamed(name)); }

    private String signupGuardianNamed(String name) throws Exception {
        String email = "gp-g-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"GUARDIAN\",\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return email;
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
}
