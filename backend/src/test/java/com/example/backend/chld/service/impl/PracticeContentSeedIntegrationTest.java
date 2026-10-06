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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대량 연습 콘텐츠(data.sql, build_seed.py 생성) 검증.
 * - 1,000개 이상, 모든 발음 유형·난이도·콘텐츠 유형 존재, 빈 문항·같은 세트 안 중복 없음
 * - 음운 규칙 콘텐츠는 백엔드 PronunciationRuleDetector로 다시 판별해 분류와 맞는지 확인(생성기와 독립된 두 번째 검증)
 * - 전체 데이터에서 검색·필터·페이지 응답
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class PracticeContentSeedIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void theSeedHasAtLeastAThousandItemsCoveringEveryCategory() {
        long items = jdbc.queryForObject("SELECT COUNT(*) FROM exercise_items i JOIN exercises e ON e.exercise_id=i.exercise_id WHERE e.pronunciation_rule IS NOT NULL AND e.active=TRUE", Long.class);
        assertTrue(items >= 1000, "연습 콘텐츠 1,000개 이상: " + items);
        assertEquals(Set.of("BASIC_CONSONANT", "BASIC_VOWEL", "CODA", "LIAISON", "NASALIZATION", "TENSIFICATION", "PALATALIZATION", "ASPIRATION", "CONSONANT_ASSIMILATION", "COMPREHENSIVE"),
                Set.copyOf(jdbc.queryForList("SELECT DISTINCT pronunciation_rule FROM exercises WHERE pronunciation_rule IS NOT NULL", String.class)));
        assertEquals(Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED"), Set.copyOf(jdbc.queryForList("SELECT DISTINCT difficulty FROM exercises WHERE difficulty IS NOT NULL", String.class)));
        assertEquals(Set.of("WORD", "SHORT_SENTENCE", "LONG_SENTENCE"), Set.copyOf(jdbc.queryForList("SELECT DISTINCT content_type FROM exercises WHERE content_type IS NOT NULL", String.class)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM exercise_items WHERE TRIM(text_value)=''", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT exercise_id,text_value FROM exercise_items GROUP BY exercise_id,text_value HAVING COUNT(*)>1) d", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM exercises e WHERE e.pronunciation_rule IS NOT NULL AND NOT EXISTS (SELECT 1 FROM exercise_items i WHERE i.exercise_id=e.exercise_id)", Integer.class));
    }

    @Test
    void phonologicalRuleContentReallyContainsItsRuleAccordingToTheBackendDetector() {
        Map<String, Set<Integer>> expected = Map.of("LIAISON", Set.of(13), "NASALIZATION", Set.of(18, 19), "TENSIFICATION", Set.of(23),
                "PALATALIZATION", Set.of(17), "CONSONANT_ASSIMILATION", Set.of(20));
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT e.pronunciation_rule AS rule, i.text_value AS text FROM exercise_items i JOIN exercises e ON e.exercise_id=i.exercise_id " +
                "WHERE e.pronunciation_rule IN ('LIAISON','NASALIZATION','TENSIFICATION','PALATALIZATION','CONSONANT_ASSIMILATION')");
        assertTrue(rows.size() > 200);
        for (Map<String,Object> row : rows) {
            Set<Integer> found = PronunciationRuleDetector.detect(TranscriptComparator.normalize(String.valueOf(row.get("text")))).stream()
                    .map(PronunciationRuleDetector.RuleMatch::rule).collect(Collectors.toSet());
            Set<Integer> wanted = expected.get(String.valueOf(row.get("rule")));
            assertTrue(found.stream().anyMatch(wanted::contains), row + " → 감지된 조항 " + found);
        }
    }

    @Test
    void searchingTheFullCatalogIsPagedAndFast() throws Exception {
        String email = "seed-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TEACHER\",\"name\":\"검색 선생님\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,"
                        + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}")).andExpect(status().isCreated());
        String token = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        long started = System.nanoTime();
        JsonNode first = objectMapper.readTree(mvc.perform(get("/api/v1/practice-contents").param("size", "50").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long ms = (System.nanoTime() - started) / 1_000_000;
        assertEquals(50, first.path("content").size(), "한 페이지 최대 50개");
        assertTrue(first.path("totalElements").asLong() >= 200);
        assertTrue(ms < 2000, "전체 목록 첫 페이지 응답 " + ms + "ms");
        JsonNode last = objectMapper.readTree(mvc.perform(get("/api/v1/practice-contents").param("size", "50").param("page", String.valueOf(first.path("totalPages").asInt() - 1))
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        assertTrue(last.path("content").size() > 0);
        JsonNode advanced = objectMapper.readTree(mvc.perform(get("/api/v1/practice-contents").param("difficulty", "ADVANCED").param("rule", "COMPREHENSIVE").param("size", "50")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        assertTrue(advanced.path("totalElements").asInt() >= 5);
        for (JsonNode row : advanced.path("content")) assertEquals("LONG_SENTENCE", row.path("contentType").asText());
    }
}
