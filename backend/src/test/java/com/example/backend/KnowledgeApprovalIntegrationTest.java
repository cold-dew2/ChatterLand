package com.example.backend;

import com.example.backend.chld.mapper.KnowledgeMapper;
import com.example.backend.chld.service.impl.KnowledgeRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 승인 우회 방지(테스트 DB): SQL 조회(KnowledgeMapper) 단계만으로도 미승인 자료가 나오지 않는지, 그리고 설정에
 * 미승인 상태를 넣어도 검색(KnowledgeRetriever)에 들어가지 않는지 확인한다. 자료·계정은 테스트 전용 행이고 롤백된다.
 * 다른 통합 테스트와 같은 설정(@AutoConfigureMockMvc 포함)을 써서 캐시된 스프링 컨텍스트를 재사용한다
 * (설정이 다르면 컨텍스트·커넥션 풀이 하나 더 생겨 테스트 DB 연결 수 한도를 넘을 수 있다).
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class KnowledgeApprovalIntegrationTest {
    private static final List<String> WIDE_SETTING = List.of("APPROVED", "DRAFT", "VERIFIED", "PENDING", "RETIRED");
    @Autowired KnowledgeMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private long teacher, student;

    private long user(String role) {
        String email = "rag-approval-" + role.toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO users(role,center_id,name,email,password_hash,terms_agreed) VALUES(?,1,'승인 시험 계정',?,'not-used',TRUE)", role, email);
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    /** 테스트 문서 1개 + 청크 1개. reviewedAt=false면 승인 시각 없음 */
    private void document(String id, String category, String verification, String review, Long reviewer, boolean reviewedAt, String tags) {
        jdbc.update("INSERT INTO knowledge_documents(document_id,title,source_citation,publisher,published_year,topic,scope,license_note,category,"
                        + "verification_status,verified_at,review_status,reviewed_by,reviewed_at,version) VALUES(?,?, '테스트 인용','테스트',2026,'승인 시험','테스트 전용','테스트',?,?,NOW(),?,?,"
                        + (reviewedAt ? "NOW()" : "NULL") + ",1)",
                id, id + " 제목", category, verification, review, reviewer);
        jdbc.update("INSERT INTO knowledge_chunks(chunk_id,document_id,chunk_order,location,content,tags) VALUES(?,?,1,'위치',?,?)", id + "-01", id, id + " 내용", tags);
    }

    @BeforeEach
    void seedDocumentsInEveryApprovalState() {
        teacher = user("TEACHER");
        student = user("STUDENT");
        String rule = "rule:20,phoneme:ㄴ,phoneme:ㄹ";
        document("apv-ok", "PRONUNCIATION_RULE", "VERIFIED", "APPROVED", teacher, true, rule);              // 정상 승인
        document("apv-draft", "PRONUNCIATION_RULE", "VERIFIED", "DRAFT", teacher, true, rule);              // 출처 확인됐지만 내용 미승인
        document("apv-verified-only", "PRONUNCIATION_RULE", "VERIFIED", "DRAFT", null, false, rule);        // 출처 확인만
        document("apv-status-verified", "PRONUNCIATION_RULE", "VERIFIED", "VERIFIED", teacher, true, rule); // 검수 상태 칸에 VERIFIED(잘못 입력)
        document("apv-no-approver", "PRONUNCIATION_RULE", "VERIFIED", "APPROVED", null, true, rule);        // 상태만 APPROVED
        document("apv-ghost-approver", "PRONUNCIATION_RULE", "VERIFIED", "APPROVED", 987_654_321_098L, true, rule); // 없는 계정
        document("apv-no-time", "PRONUNCIATION_RULE", "VERIFIED", "APPROVED", teacher, false, rule);        // 승인 시각 없음
        document("apv-pending-source", "PRONUNCIATION_RULE", "PENDING", "APPROVED", teacher, true, rule);   // 출처·권한 미확인
        document("apv-ex-teacher", "TEACHER_EXAMPLE", "VERIFIED", "APPROVED", teacher, true, "phoneme:ㄹ");  // 교사 예시, 선생님 승인
        document("apv-ex-student", "TEACHER_EXAMPLE", "VERIFIED", "APPROVED", student, true, "phoneme:ㄹ");  // 교사 예시, 학생 계정이 승인
        document("apv-ex-no-approver", "TEACHER_EXAMPLE", "VERIFIED", "APPROVED", null, true, "phoneme:ㄹ"); // 교사 예시, 승인자 없음
    }

    private Set<String> testDocuments(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> String.valueOf(row.get("documentId"))).filter(id -> id.startsWith("apv-")).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void theQueryAloneReturnsOnlyApprovedSourcesWithAValidApprovalRecordEvenWithAWideSetting() {
        // Java 재검증 없이 SQL 결과만 본다: 설정에 미승인 상태를 넣어도 APPROVED + 승인 기록이 있는 자료만
        assertEquals(Set.of("apv-ok", "apv-ex-teacher"), testDocuments(mapper.findSearchableChunks(WIDE_SETTING)));
        assertEquals(Set.of("apv-ok", "apv-ex-teacher"), testDocuments(mapper.findSearchableChunks(List.of("APPROVED"))));
        assertEquals(Set.of(), testDocuments(mapper.findSearchableChunks(List.of("DRAFT", "VERIFIED"))), "APPROVED가 없는 설정");
        assertEquals(Set.of(), testDocuments(mapper.findSearchableChunks(List.of())), "빈 설정은 결과 없음(SQL 오류 아님)");
        Map<String, Object> row = mapper.findSearchableChunks(List.of("APPROVED")).stream().filter(r -> "apv-ok".equals(r.get("documentId"))).findFirst().orElseThrow();
        assertEquals(teacher, ((Number) row.get("reviewerUserId")).longValue());
        assertEquals("TEACHER", row.get("reviewerRole"));
        assertNotNull(row.get("reviewedAt"));
        // 시드 원문 자료(검수 전 DRAFT)도 나오지 않는다
        assertTrue(mapper.findSearchableChunks(WIDE_SETTING).stream().noneMatch(r -> String.valueOf(r.get("documentId")).startsWith("nikl-pron-")));
    }

    @Test
    void theRetrieverWithAWideSettingStillUsesOnlyApprovedSources() {
        KnowledgeRetriever retriever = new KnowledgeRetriever(mapper, String.join(",", WIDE_SETTING));
        // 조항 경로(신라 → 제20항)와 안내 경로(ㄹ → 교사 예시)에서 각각 승인된 자료만 나온다
        List<String> ruleSources = retriever.retrieve("신라", Set.of("신"), Set.of("ㄴ", "ㄹ")).sources().stream()
                .map(KnowledgeRetriever.Source::documentId).filter(id -> id.startsWith("apv-")).toList();
        assertEquals(List.of("apv-ok", "apv-ex-teacher"), ruleSources);
        List<String> guideSources = retriever.retrieve("라디오", Set.of("라"), Set.of("ㄹ")).sources().stream()
                .map(KnowledgeRetriever.Source::documentId).filter(id -> id.startsWith("apv-")).toList();
        assertEquals(List.of("apv-ex-teacher"), guideSources);
        assertTrue(new KnowledgeRetriever(mapper, "DRAFT,VERIFIED").retrieve("신라", Set.of("신"), Set.of("ㄴ", "ㄹ")).sources().isEmpty(),
                "APPROVED가 없는 설정이면 아무 자료도 쓰지 않는다");
    }
}
