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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 선생님의 학생 정보 접근 권한.
 * - 학생 단위 API는 모두 담당 관계(teacher_students ACTIVE)를 서버에서 확인한다: 다른 센터·담당이 아닌·담당 해제된 학생은 막힌다.
 * - 요청 본문·쿼리의 centerId는 쓰지 않는다(로그인한 선생님 계정의 센터만 쓴다).
 * - 알려진 위험(KNOWN RISK): 선생님 센터는 가입 때 본인이 고르고 확인 절차가 없다. 그 센터의 학생 목록(연락처 포함)을 보고
 *   승인 없이 담당으로 추가할 수 있다. 아래 knownRisk 테스트는 현재 동작을 기록한 것이며, 소속 승인 모델이 도입되면 반대로 바꿔야 한다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class TeacherAccessIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 학생 ID로 접근하는 선생님 API(읽기) */
    private static final String[] STUDENT_READ_PATHS = {"", "/sessions", "/practice-attempts", "/practice-summary", "/learning-report",
            "/speech-analyses", "/analytics?startDate=" + LocalDate.now().minusDays(7) + "&endDate=" + LocalDate.now(),
            "/report?startDate=" + LocalDate.now().minusDays(7) + "&endDate=" + LocalDate.now(),
            "/report/download?startDate=" + LocalDate.now().minusDays(7) + "&endDate=" + LocalDate.now()};

    @Test
    void otherCenterStudentsAreBlockedOnEveryStudentApiEvenWithTheirId() throws Exception {
        String teacher = signupTeacher(1);
        Student other = signupStudent(2, "다른센터학생");
        String analysisId = insertAnalysis(other);
        assertBlocked(teacher, other, analysisId);
        // 담당으로 추가하려 해도 다른 센터 학생은 찾을 수 없다
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + other.studentId + ",\"name\":\"x\"}")).andExpect(status().isNotFound());
        assertEquals(0, links(other));
    }

    @Test
    void sameCenterStudentsWithoutAssignmentAndUnassignedStudentsAreBlocked() throws Exception {
        String teacher = signupTeacher(1), otherTeacher = signupTeacher(1);
        Student student = signupStudent(1, "담당아닌학생");
        String analysisId = insertAnalysis(student);
        // 같은 센터라도 담당이 아니면 학생 단위 API는 막힌다
        assertBlocked(teacher, student, analysisId);
        // 다른 선생님이 담당해도 이 선생님에게는 막힌다
        link(otherTeacher, student).andExpect(status().isCreated());
        assertBlocked(teacher, student, analysisId);
        // 담당했다가 해제하면 바로 막힌다
        link(teacher, student).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/teachers/me/students/" + student.studentId).header("Authorization", bearer(teacher))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/teachers/me/students/" + student.studentId).header("Authorization", bearer(teacher))).andExpect(status().isNoContent());
        assertBlocked(teacher, student, analysisId);
    }

    @Test
    void centerIdInBodyOrQueryIsIgnored() throws Exception {
        String teacher = signupTeacher(1);
        Student other = signupStudent(2, "다른센터학생");
        // 본문에 centerId를 넣어도 다른 센터 학생을 추가할 수 없다
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + other.studentId + ",\"centerId\":2,\"name\":\"x\"}")).andExpect(status().isNotFound());
        // 새 학생을 만들 때 centerId를 넣어도 선생님 계정의 센터로 만든다
        JsonNode created = json(mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"새학생\",\"age\":7,\"centerId\":2}")).andExpect(status().isCreated()));
        assertEquals(1L, jdbc.queryForObject("SELECT center_id FROM student_profiles WHERE student_id=?", Long.class, created.path("studentId").asLong()));
        // 쿼리의 centerId는 쓰지 않는다: 다른 센터 학생은 목록에 나오지 않는다
        JsonNode available = json(mvc.perform(get("/api/v1/teachers/me/students/available?centerId=2").header("Authorization", bearer(teacher))).andExpect(status().isOk()));
        available.forEach(row -> assertNotEquals(other.studentId, row.path("studentId").asLong()));
        // 다른 역할은 선생님 API를 쓸 수 없다
        mvc.perform(get("/api/v1/teachers/me/students/available").header("Authorization", bearer(other.token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teachers/me/students/" + other.studentId).header("Authorization", bearer(other.token))).andExpect(status().isForbidden());
    }

    /**
     * 담당 전 학생 목록은 고르는 데 필요한 최소 정보(ID·이름·나이·센터명)만 준다. 연락처·메모·치료 영역은 담당 관계가 생긴 뒤 상세에서만.
     * KNOWN RISK(현재 동작 기록, DB 변경 필요): 센터는 가입 때 본인이 고르고, 같은 센터 학생을 승인 없이 담당으로 추가할 수 있다.
     * 선생님 소속 승인 모델 도입 후에는 이 테스트의 '승인 없이 추가' 부분을 '승인 전 차단'으로 바꿔야 한다.
     */
    @Test
    void availableListHidesPersonalDetails_andKnownRiskLinkingWithoutApproval() throws Exception {
        Student student = signupStudent(3, "공개되는학생");
        jdbc.update("UPDATE student_profiles SET parent_phone='010-0000-0000', memo='민감 메모' WHERE student_id=?", student.studentId);
        // 아무 확인 없이 센터 3을 골라 가입한 선생님
        String anyone = signupTeacher(3);
        JsonNode available = json(mvc.perform(get("/api/v1/teachers/me/students/available").header("Authorization", bearer(anyone))).andExpect(status().isOk()));
        JsonNode row = null;
        for (JsonNode candidate : available) if (candidate.path("studentId").asLong() == student.studentId) row = candidate;
        assertNotNull(row, "현재(KNOWN RISK): 같은 센터를 고르기만 하면 학생이 목록에 보인다");
        java.util.List<String> fields = new java.util.ArrayList<>(); row.fieldNames().forEachRemaining(fields::add);
        assertEquals(java.util.Set.of("studentId", "name", "age", "centerName"), java.util.Set.copyOf(fields), "담당 전에는 최소 정보만");
        assertFalse(available.toString().contains("010-0000-0000"), "담당 전 목록에 보호자 연락처가 없다");
        assertFalse(available.toString().contains("민감 메모"), "담당 전 목록에 메모가 없다");
        // 승인 없이 담당으로 추가 → 이후 학생 단위 API 전부 접근 가능
        link(anyone, student).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/teachers/me/students/" + student.studentId + "/practice-attempts").header("Authorization", bearer(anyone))).andExpect(status().isOk());
        // 담당 관계가 생긴 뒤 상세에서는 업무 정보가 보이고, 연결 메모가 학생 프로필을 바꾸지 않는다
        JsonNode detail = json(mvc.perform(get("/api/v1/teachers/me/students/" + student.studentId).header("Authorization", bearer(anyone))).andExpect(status().isOk()));
        assertEquals("010-0000-0000", detail.path("parentPhone").asText());
        assertEquals("민감 메모", jdbc.queryForObject("SELECT memo FROM student_profiles WHERE student_id=?", String.class, student.studentId));
    }

    // ── 도우미 ─────────────────────────────────────────────

    private void assertBlocked(String teacher, Student student, String analysisId) throws Exception {
        for (String path : STUDENT_READ_PATHS) {
            int code = mvc.perform(get("/api/v1/teachers/me/students/" + student.studentId + path).header("Authorization", bearer(teacher)))
                    .andReturn().getResponse().getStatus();
            assertTrue(code == 403 || code == 404, path + " → " + code);
        }
        mvc.perform(put("/api/v1/teachers/me/students/" + student.studentId).header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"바꿈\",\"age\":9}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/teachers/me/students/" + student.studentId).header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/teachers/me/homeworks").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student.studentId + ",\"title\":\"숙제\",\"type\":\"말하기 연습\",\"dueDate\":\"" + LocalDate.now().plusDays(2) + "\",\"targetMinutes\":10}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teachers/me/homeworks?studentId=" + student.studentId).header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/" + analysisId + "/audio").header("Authorization", bearer(teacher))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/" + analysisId + "/feedback").header("Authorization", bearer(teacher))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/" + analysisId + "/review").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"judgement\":\"ACCEPTABLE\",\"note\":\"x\"}")).andExpect(status().isNotFound());
        assertNotEquals("바꿈", jdbc.queryForObject("SELECT name FROM student_profiles WHERE student_id=?", String.class, student.studentId),
                "담당이 아닌 선생님이 학생 프로필을 바꿀 수 없다");
        assertEquals("NOT_REQUIRED", jdbc.queryForObject("SELECT review_status FROM speech_analyses WHERE analysis_id=?", String.class, analysisId));
    }

    private int links(Student student) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM teacher_students WHERE student_id=?", Integer.class, student.studentId);
    }

    private ResultActions link(String teacher, Student student) throws Exception {
        return mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student.studentId + ",\"name\":\"우리학생\"}"));
    }

    private String insertAnalysis(Student student) {
        long exerciseId = jdbc.queryForObject("SELECT MIN(exercise_id) FROM exercises WHERE active=TRUE", Long.class);
        long itemId = jdbc.queryForObject("SELECT MIN(item_id) FROM exercise_items WHERE exercise_id=?", Long.class, exerciseId);
        String text = jdbc.queryForObject("SELECT text_value FROM exercise_items WHERE item_id=?", String.class, itemId);
        String analysisId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,?,?,'audio/wav','COMPLETED','SENTENCE_MATCH',?,?,100.00,'NOT_EVALUATED','NOT_REQUIRED','WORD','NO_CANDIDATES','{}','jamo-align-v1',NOW())",
                analysisId, student.studentId, exerciseId, String.valueOf(itemId), text, text);
        return analysisId;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Student(long studentId, String token) { }

    private Student signupStudent(long centerId, String name) throws Exception {
        String email = "ta-s-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"우리학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":" + centerId + ",\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Student(studentId, login(email));
    }

    private String signupTeacher(long centerId) throws Exception {
        String email = "ta-t-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"권한 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":" + centerId + ",\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
}
