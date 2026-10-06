package com.example.backend.chld.service.impl;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 연습 영역 5개(발음·어휘력·유창성·표현력·이해력)의 개인 연습 콘텐츠 검증.
 * - 영역별 최소 개수와 총 1,500개 이상, 중복·빈 세트·자리 채움 제목 없음
 * - 영역별 목록이 전체 콘텐츠를 페이지 단위로 끝까지 제공(이전/다음·랜덤 연습이 쓰는 1개 단위 조회 포함)
 * - 다시 연습 목록: 학생이 그 영역에서 연습한 세트만 세트당 한 번, 최근 연습 순(기록은 읽기만 함)
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class PracticeCatalogIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    private static final Map<String, Integer> MINIMUM = Map.of("articulation", 350, "vocabulary", 350, "fluency", 300, "expression", 300, "comprehension", 250);

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everyAreaHasEnoughDistinctRealContent() {
        long total = 0;
        for (Map.Entry<String, Integer> area : MINIMUM.entrySet()) {
            long count = jdbc.queryForObject("SELECT COUNT(*) FROM exercises WHERE category_id=? AND active=TRUE", Long.class, area.getKey());
            assertTrue(count >= area.getValue(), area.getKey() + " 콘텐츠 " + count + "개 < " + area.getValue());
            total += count;
        }
        assertTrue(total >= 1500, "전체 콘텐츠 " + total);
        assertEquals(Set.of("발음", "어휘력", "유창성", "표현력", "이해력"),
                Set.copyOf(jdbc.queryForList("SELECT name FROM practice_categories WHERE active=TRUE", String.class)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT category_id,title FROM exercises WHERE active=TRUE GROUP BY category_id,title HAVING COUNT(*)>1) d", Integer.class),
                "같은 영역 안 제목 중복");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM exercises e WHERE e.active=TRUE AND NOT EXISTS (SELECT 1 FROM exercise_items i WHERE i.exercise_id=e.exercise_id)", Integer.class),
                "문항 없는 세트");
        assertEquals(0, duplicatedContentGroups(), "안내문과 문항이 똑같은 세트");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM exercises WHERE active=TRUE AND (title LIKE '%문제 1' OR title LIKE '%연습 문제%' OR title LIKE '%Lorem%')", Integer.class),
                "자리 채움 제목");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM exercises WHERE category_id<>'articulation' AND pronunciation_rule IS NOT NULL", Integer.class),
                "발음 외 영역에 발음 규칙 분류가 붙지 않는다");
    }

    private int duplicatedContentGroups() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT x.instruction, x.texts FROM (SELECT e.instruction, GROUP_CONCAT(i.text_value ORDER BY i.sort_order SEPARATOR '|') AS texts "
                + "FROM exercises e JOIN exercise_items i ON i.exercise_id=e.exercise_id WHERE e.active=TRUE GROUP BY e.exercise_id, e.instruction) x "
                + "GROUP BY x.instruction, x.texts HAVING COUNT(*)>1) d", Integer.class);
    }

    @Test
    void eachAreaPagesThroughItsWholeCatalogWithoutGapsOrOverlap() throws Exception {
        String token = signupStudent().token;
        for (String category : MINIMUM.keySet()) {
            long expected = jdbc.queryForObject("SELECT COUNT(*) FROM exercises WHERE category_id=? AND active=TRUE", Long.class, category);
            JsonNode first = page(token, category, 0, 20);
            assertEquals(expected, first.path("totalElements").asLong(), category);
            assertEquals(20, first.path("content").size());
            for (JsonNode row : first.path("content")) {
                assertEquals(category, row.path("categoryId").asText());
                assertTrue(row.path("items").size() > 0, "문항이 함께 온다");
            }
            JsonNode second = page(token, category, 1, 20);
            Set<Long> firstIds = ids(first), secondIds = ids(second);
            secondIds.retainAll(firstIds);
            assertTrue(secondIds.isEmpty(), category + " 페이지끼리 겹치지 않는다");
            // 1개 단위 조회(n번째 콘텐츠)가 20개 페이지의 같은 위치와 같다: 이전/다음·랜덤 연습이 페이지 경계에서도 같은 순서를 쓴다.
            assertEquals(first.path("content").get(19).path("exerciseId").asLong(), page(token, category, 19, 1).path("content").get(0).path("exerciseId").asLong());
            assertEquals(second.path("content").get(0).path("exerciseId").asLong(), page(token, category, 20, 1).path("content").get(0).path("exerciseId").asLong());
            JsonNode last = page(token, category, first.path("totalPages").asInt() - 1, 20);
            assertTrue(last.path("content").size() > 0 && last.path("last").asBoolean(), category + " 마지막 페이지");
        }
    }

    @Test
    void practicedListShowsEachPracticedSetOnceMostRecentFirstWithinTheArea() throws Exception {
        Student student = signupStudent(), other = signupStudent();
        List<Long> vocabulary = jdbc.queryForList("SELECT exercise_id FROM exercises WHERE category_id='vocabulary' AND active=TRUE ORDER BY sort_order,exercise_id LIMIT 2", Long.class);
        long fluency = jdbc.queryForObject("SELECT exercise_id FROM exercises WHERE category_id='fluency' AND active=TRUE ORDER BY sort_order,exercise_id LIMIT 1", Long.class);
        attempt(student.studentId, vocabulary.get(0), 3);
        attempt(student.studentId, vocabulary.get(0), 2);
        attempt(student.studentId, vocabulary.get(1), 1);
        attempt(student.studentId, fluency, 0);

        JsonNode practiced = practiced(student.token, "vocabulary");
        assertEquals(2, practiced.path("totalElements").asInt(), "세트당 한 번");
        List<Long> order = new ArrayList<>();
        practiced.path("content").forEach(row -> order.add(row.path("exerciseId").asLong()));
        assertEquals(List.of(vocabulary.get(1), vocabulary.get(0)), order, "최근 연습 순");
        assertEquals(2, practiced.path("content").get(1).path("attemptCount").asInt());
        assertTrue(practiced.path("content").get(0).path("items").size() > 0);
        assertEquals(1, practiced(student.token, "fluency").path("totalElements").asInt(), "영역별로 나뉜다");
        assertEquals(0, practiced(student.token, "comprehension").path("totalElements").asInt());
        assertEquals(0, practiced(other.token, "vocabulary").path("totalElements").asInt(), "다른 학생 기록은 보이지 않는다");
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempts WHERE student_id=?", Integer.class, student.studentId), "조회는 기록을 바꾸지 않는다");

        mvc.perform(get("/api/v1/practice/vocabulary/practiced").header("Authorization", "Bearer " + signupTeacher())).andExpect(status().isForbidden());
    }

    private void attempt(long studentId, long exerciseId, int daysAgo) {
        long itemId = jdbc.queryForObject("SELECT MIN(item_id) FROM exercise_items WHERE exercise_id=?", Long.class, exerciseId);
        jdbc.update("INSERT INTO practice_attempts(student_id,exercise_id,item_id,attempt_type,created_at) VALUES(?,?,?,'SELF',DATE_SUB(NOW(),INTERVAL ? DAY))",
                studentId, exerciseId, String.valueOf(itemId), daysAgo);
    }

    private JsonNode page(String token, String category, int page, int size) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/v1/practice/" + category + "/exercises").param("page", String.valueOf(page)).param("size", String.valueOf(size))
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode practiced(String token, String category) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/v1/practice/" + category + "/practiced").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static Set<Long> ids(JsonNode page) {
        Set<Long> ids = new HashSet<>();
        page.path("content").forEach(row -> ids.add(row.path("exerciseId").asLong()));
        return ids;
    }

    private record Student(long studentId, String token) { }

    private Student signupStudent() throws Exception {
        String email = "catalog-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"연습 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":false,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Student(studentId, login(email));
    }

    private String signupTeacher() throws Exception {
        String email = "catalog-t-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"연습 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
}
