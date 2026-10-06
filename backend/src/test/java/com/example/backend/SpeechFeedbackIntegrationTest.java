package com.example.backend;

import com.example.backend.chld.exception.AiProviderException;
import com.example.backend.chld.service.impl.AiTextClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI 학습 피드백 API(외부 AI는 mock — 실제 호출은 LiveAiIntegrationTest).
 * 본인 분석만, AI 외부 전송 동의 필요, 피드백은 별도 테이블에 저장하고 분석 결과·점수는 바꾸지 않으며, AI가 실패해도 분석 조회는 그대로다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class SpeechFeedbackIntegrationTest {
    private static final String PASSWORD = "Chatterland!234";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiTextClient ai;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String GROUNDED = "'신라'를 끝까지 말했어요! ㄴ이 ㄹ 앞에서 ㄹ로 소리 나서 [실라]처럼 말해요 [S1].";

    /** 원문 자료 중 조항 자료(출처·권한 확인 VERIFIED)는 검수 전(DRAFT)이라 검색되지 않는다. 이 테스트에서만 승인 상태로 바꾼다(트랜잭션 롤백). */
    private static final String RULE_DOCUMENT = "nikl-pron-assimilation";

    @org.junit.jupiter.api.BeforeEach
    void approveTheVerifiedStandardPronunciationSource() {
        jdbc.update("UPDATE knowledge_documents SET review_status='APPROVED', reviewed_at=NOW() WHERE document_id=?", RULE_DOCUMENT);
    }

    @Test
    void aStudentGetsAStoredAiExplanationForTheirOwnAnalysisWithoutChangingTheResult() throws Exception {
        Student owner = signupStudent(true);
        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        when(ai.isConfigured()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-flash-latest");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn(GROUNDED);

        mvc.perform(get("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_GENERATED"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.consentRequired").value(false));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());

        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY")).andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.text").value(GROUNDED)).andExpect(jsonPath("$.sources[0].location").value("제5장 제20항")).andExpect(jsonPath("$.sources[0].cited").value(true))
                .andExpect(jsonPath("$.modelName").value("gemini-flash-latest")).andExpect(jsonPath("$.basedOn[0]").value("AUTO_ANALYSIS"));
        mvc.perform(get("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.status").value("READY"));
        // 분석 결과(일치율·점수·자동 판정)는 그대로이고, 분석 응답에 AI 설명이 섞이지 않는다.
        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.textMatchRate").value(50.0)).andExpect(jsonPath("$.pronunciationScore").doesNotExist())
                .andExpect(jsonPath("$.assessmentStatus").value("ERROR_CANDIDATES")).andExpect(jsonPath("$.aiFeedback").doesNotExist());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM speech_ai_feedback WHERE analysis_id=?", Integer.class, analysisId));
        verify(ai, times(1)).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void otherStudentsTeachersAndUnconsentedStudentsCannotGenerate() throws Exception {
        Student owner = signupStudent(true);
        Student other = signupStudent(true);
        Student noConsent = signupStudent(false);
        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        String unconsentedAnalysis = insertSentenceAnalysis(noConsent.studentId, "ERROR_CANDIDATES");
        when(ai.isConfigured()).thenReturn(true);

        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/speech/analyses/{id}/feedback", unconsentedAnalysis).header("Authorization", noConsent.bearer()))
                .andExpect(jsonPath("$.consentRequired").value(true));
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", unconsentedAnalysis).header("Authorization", noConsent.bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void heldRecordingsAreNotEvaluableAndAiFailuresDoNotBreakTheAnalysis() throws Exception {
        Student owner = signupStudent(true);
        String held = insertSentenceAnalysis(owner.studentId, "HOLD");
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", held).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_EVALUABLE"))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("다시 녹음")));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());

        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt()))
                .thenThrow(new AiProviderException("AI_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, "AI 서비스 응답 시간이 초과되었어요."))
                .thenThrow(new AiProviderException("AI_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE, "설정 필요"))
                .thenReturn("발음 점수는 90점이에요.");
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("AI_TIMEOUT"));
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
        // 점수를 지어낸 응답은 저장하지 않는다.
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_BAD_RESPONSE"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM speech_ai_feedback WHERE analysis_id=?", Integer.class, analysisId));
        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void theAssignedTeacherSeesTheSameStoredExplanationAndOthersCannot() throws Exception {
        Student owner = signupStudent(true);
        Student other = signupStudent(true);
        String analysisId = insertReviewedTherapyAnalysis(owner.studentId);
        String teacher = signupTeacher(), outsider = signupTeacher();
        link(teacher, owner.studentId);
        link(outsider, other.studentId); // 다른 학생 담당
        when(ai.isConfigured()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-flash-latest");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn(GROUNDED);

        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/feedback", analysisId).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_GENERATED"))
                .andExpect(jsonPath("$.reason").value("학생이 아직 AI 설명을 만들지 않았어요."));
        String studentView = mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String teacherView = mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/feedback", analysisId).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY")).andExpect(jsonPath("$.outdated").value(false))
                .andReturn().getResponse().getContentAsString();
        assertEquals(objectMapper.readTree(studentView).path("text"), objectMapper.readTree(teacherView).path("text"), "학생과 선생님이 같은 저장 결과를 본다");
        assertEquals(objectMapper.readTree(studentView).path("sources"), objectMapper.readTree(teacherView).path("sources"));

        // 담당이 아닌 선생님·다른 학생·학생 토큰으로는 볼 수 없다(분석 ID를 알아도). 조회는 AI를 다시 부르지 않는다.
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/feedback", analysisId).header("Authorization", "Bearer " + outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer())).andExpect(status().isForbidden());
        verify(ai, times(1)).generate(anyString(), anyList(), anyDouble(), anyInt());

        // 선생님이 다시 판정하면(실제 검토 API) 이전 근거로 만든 설명임을 표시한다. 설명이 교사 결과를 바꾸지 않는다.
        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"ACCEPTABLE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/feedback", analysisId).header("Authorization", "Bearer " + teacher))
                .andExpect(jsonPath("$.status").value("READY")).andExpect(jsonPath("$.outdated").value(true)).andExpect(jsonPath("$.text").value(GROUNDED));
        assertEquals("ACCEPTABLE", jdbc.queryForObject("SELECT teacher_judgement FROM speech_analyses WHERE analysis_id=?", String.class, analysisId));
        assertEquals("TEACHER_CONFIRMED", objectMapper.readTree(teacherView).path("basedOn").get(0).asText());
    }

    @Test
    void unapprovedDraftSourcesAreNeverUsed() throws Exception {
        jdbc.update("UPDATE knowledge_documents SET review_status='DRAFT' WHERE document_id=?", RULE_DOCUMENT); // 기본 상태(검수 전)
        Student owner = signupStudent(true);
        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        when(ai.isConfigured()).thenReturn(true);
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSUFFICIENT_SOURCES"))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("검수된 교육 자료가 없어")));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void withdrawingConsentStopsNewRequestsButKeepsStoredExplanations() throws Exception {
        Student owner = signupStudent(true);
        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        when(ai.isConfigured()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-flash-latest");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn(GROUNDED);
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer())).andExpect(jsonPath("$.status").value("READY"));
        mvc.perform(put("/api/v1/consents/me/AI_FEEDBACK").header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"agreed\":false,\"policyVersion\":\"2026-10-01\"}")).andExpect(status().isOk());
        // 철회 뒤: 저장된 설명은 학습 기록으로 남아 조회되고, 새로 만드는 요청(근거가 바뀐 경우)은 외부 호출 없이 403.
        mvc.perform(get("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer())).andExpect(jsonPath("$.status").value("READY"));
        String newer = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", newer).header("Authorization", owner.bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        verify(ai, times(1)).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void approvedButUnverifiedOrPendingSourcesAreNeverUsed() throws Exception {
        // 개발자 요약 자료(std-pronunciation-1988)는 출처·권한 확인 전(PENDING)이라 승인 상태여도 쓰지 않는다.
        assertEquals("PENDING", jdbc.queryForObject("SELECT verification_status FROM knowledge_documents WHERE document_id='std-pronunciation-1988'", String.class));
        jdbc.update("UPDATE knowledge_documents SET review_status='APPROVED' WHERE document_id='std-pronunciation-1988'");
        jdbc.update("UPDATE knowledge_documents SET verification_status='PENDING' WHERE document_id=?", RULE_DOCUMENT);
        Student owner = signupStudent(true);
        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        when(ai.isConfigured()).thenReturn(true);
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSUFFICIENT_SOURCES"));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void seededOfficialSourcesCarryVerifiedProvenanceButWaitForApproval() {
        // 원문 자료: 출처 URL·판본·사용 권한·확인 시각이 있고 VERIFIED, 내용 검수는 사람이 하므로 DRAFT로 들어간다.
        Integer documents = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents WHERE document_id LIKE 'nikl-pron-%'", Integer.class);
        assertEquals(7, documents);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents WHERE document_id LIKE 'nikl-pron-%' AND (verification_status<>'VERIFIED' "
                + "OR source_url IS NULL OR source_version IS NULL OR license_note='' OR verified_at IS NULL OR category IS NULL OR publisher<>'국립국어원')", Integer.class));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents WHERE document_id LIKE 'nikl-pron-%' AND document_id<>? AND review_status='DRAFT' AND reviewed_by IS NULL",
                Integer.class, RULE_DOCUMENT), "승인은 seed가 아니라 교사·운영자가 한다");
        assertEquals(java.util.Set.of("ARTICULATION_PLACE", "ARTICULATION_MANNER", "CONSONANT", "VOWEL", "CODA", "PRONUNCIATION_RULE"),
                new java.util.HashSet<>(jdbc.queryForList("SELECT DISTINCT category FROM knowledge_documents WHERE document_id LIKE 'nikl-pron-%'", String.class)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents WHERE category='TEACHER_EXAMPLE'", Integer.class), "교사 설명 예시는 임의로 넣지 않는다");
    }

    @Test
    void theStoredExplanationLinksTheOfficialSourceIdTitleAndLocation() throws Exception {
        Student owner = signupStudent(true);
        String analysisId = insertSentenceAnalysis(owner.studentId, "ERROR_CANDIDATES");
        when(ai.isConfigured()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-flash-latest");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn(GROUNDED);
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sources[0].sourceId").value(RULE_DOCUMENT))
                .andExpect(jsonPath("$.sources[0].title").value("표준 발음법 제5장 음의 동화·제6장 경음화(일부 조항)"))
                .andExpect(jsonPath("$.sources[0].location").value("제5장 제20항"))
                .andExpect(jsonPath("$.sources[0].category").value("PRONUNCIATION_RULE"))
                .andExpect(jsonPath("$.sources[0].url").value("https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002"))
                .andExpect(jsonPath("$.sources[0].excerpt").value(org.hamcrest.Matchers.startsWith("‘ㄴ’은 ‘ㄹ’의 앞이나 뒤에서 [ㄹ]로 발음한다.")));
        String stored = jdbc.queryForObject("SELECT sources_json FROM speech_ai_feedback WHERE analysis_id=?", String.class, analysisId);
        assertTrue(stored.contains("\"sourceId\":\"" + RULE_DOCUMENT + "\"") && stored.contains("\"chunkId\":\"nikl-pron-assimilation-20\""));
        verify(ai).generate(anyString(), argThat(turns -> turns.get(0).text().contains("[S1] (표준 발음법 제5장 음의 동화·제6장 경음화(일부 조항) 제5장 제20항)")), anyDouble(), anyInt());
    }

    @Test
    void approvedGuideSourcesExplainWordsWithoutAnApplicableRule() throws Exception {
        // 조항이 적용되지 않는 낱말(라디오)의 첫소리 후보 ㄹ→ㄴ: 승인된 조음 위치 안내 자료로 설명한다.
        jdbc.update("UPDATE knowledge_documents SET review_status='APPROVED', reviewed_at=NOW() WHERE document_id='nikl-pron-place'");
        Student owner = signupStudent(true);
        String analysisId = insertAnalysis(owner.studentId, "라디오", "나디오", "ONSET", "ㄹ", "ㄴ", "라");
        when(ai.isConfigured()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-flash-latest");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn("'라디오'를 끝까지 말해 봤어요! 컴퓨터에는 ㄹ이 ㄴ처럼 들렸어요. ㄴ과 ㄹ은 혀끝으로 내는 소리예요 [S1].");
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sources[0].sourceId").value("nikl-pron-place"))
                .andExpect(jsonPath("$.sources[0].category").value("ARTICULATION_PLACE"))
                .andExpect(jsonPath("$.sources[0].location").value("제2장 제2항 해설(자음 분류표)"))
                .andExpect(jsonPath("$.sources[0].excerpt").value(org.hamcrest.Matchers.containsString("치조음: ㄷ(파열음 평음)")));
    }

    /**
     * 교사 설명 예시: 출처 확인(VERIFIED)·승인(APPROVED)에 더해 승인자(reviewed_by)가 기록돼야 쓴다.
     * 한 트랜잭션 안에서 JdbcTemplate로 바꾼 값은 MyBatis 1차 캐시에 보이지 않으므로 승인 전·후를 별도 테스트로 나눈다.
     */
    @Test
    void teacherExamplesWithoutARecordedApproverAreNotUsed() throws Exception {
        insertTestTeacherExample(null);
        String analysisId = insertAnalysis(signupStudentId(), "라디오", "나디오", "ONSET", "ㄹ", "ㄴ", "라");
        when(ai.isConfigured()).thenReturn(true);
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", lastStudent.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSUFFICIENT_SOURCES"));
        verify(ai, never()).generate(anyString(), anyList(), anyDouble(), anyInt());
    }

    @Test
    void approvedTeacherExamplesAreUsedAsTheirOwnSourceType() throws Exception {
        insertTestTeacherExample(jdbc.queryForObject("SELECT MIN(user_id) FROM users", Long.class));
        String analysisId = insertAnalysis(signupStudentId(), "라디오", "나디오", "ONSET", "ㄹ", "ㄴ", "라");
        when(ai.isConfigured()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-flash-latest");
        when(ai.generate(anyString(), anyList(), anyDouble(), anyInt())).thenReturn("'라디오'를 끝까지 말해 봤어요! ㄹ 첫소리 낱말을 천천히 따라 말해 봐요 [S1].");
        mvc.perform(post("/api/v1/speech/analyses/{id}/feedback", analysisId).header("Authorization", lastStudent.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sources[0].category").value("TEACHER_EXAMPLE"))
                .andExpect(jsonPath("$.sources[0].sourceId").value("test-teacher-example"));
    }

    private Student lastStudent;

    private long signupStudentId() throws Exception {
        lastStudent = signupStudent(true);
        return lastStudent.studentId;
    }

    /** 테스트 전용 교사 예시(롤백). 실제 교사 예시는 seed로 넣지 않는다. */
    private void insertTestTeacherExample(Long approverUserId) {
        jdbc.update("INSERT INTO knowledge_documents(document_id,title,source_citation,source_url,publisher,author,published_year,topic,scope,license_note,category,"
                + "verification_status,verified_at,review_status,reviewed_by,reviewed_at,version) VALUES('test-teacher-example','테스트 교사 설명 예시','테스트 기관 내부 작성',NULL,"
                + "'테스트 센터','테스트 선생님',2026,'ㄹ 첫소리','테스트 전용','기관 내부 작성물(테스트)','TEACHER_EXAMPLE','VERIFIED',NOW(),'APPROVED',?,NOW(),1)", approverUserId);
        jdbc.update("INSERT INTO knowledge_chunks(chunk_id,document_id,chunk_order,location,content,tags) VALUES('test-teacher-example-01','test-teacher-example',1,'예시 1',"
                + "'테스트 예시: ㄹ 첫소리 낱말을 천천히 따라 말해 보기','phoneme:ㄹ')");
    }

    private String insertAnalysis(long studentId, String target, String transcript, String slot, String expected, String produced, String syllable) {
        String id = UUID.randomUUID().toString();
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "comparison_json,pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,1,?,'audio/wav','COMPLETED','SENTENCE_MATCH',?,?,50.00,?,'NOT_EVALUATED','NOT_REQUIRED','WORD','ERROR_CANDIDATES',?,'jamo-align-v1',NOW())",
                id, studentId, String.valueOf(itemId), target, transcript,
                "[{\"type\":\"SUBSTITUTED\",\"expected\":\"" + target + "\",\"recognized\":\"" + transcript + "\"}]",
                "{\"holdReasons\":[],\"candidates\":[{\"type\":\"SUBSTITUTION\",\"slot\":\"" + slot + "\",\"expected\":\"" + expected + "\",\"produced\":\"" + produced + "\",\"targetSyllable\":\"" + syllable + "\"}]}");
        return id;
    }

    private String insertReviewedTherapyAnalysis(long studentId) {
        String id = UUID.randomUUID().toString();
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "pronunciation_status,review_status,teacher_judgement,teacher_note,analysis_type,assessment_status,assessment_json,analysis_version,completed_at,reviewed_at) " +
                        "VALUES(?,?,1,?,'audio/wav','COMPLETED','PRONUNCIATION_REVIEW','신라','실라',NULL,'NOT_EVALUATED','REVIEWED','NEEDS_PRACTICE','비공개 메모','WORD','ERROR_CANDIDATES','{}','jamo-align-v1',NOW(),NOW())",
                id, studentId, String.valueOf(itemId));
        return id;
    }

    private String insertSentenceAnalysis(long studentId, String assessmentStatus) {
        String id = UUID.randomUUID().toString();
        long itemId = jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=1 ORDER BY sort_order LIMIT 1", Long.class);
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_mime,status,evaluation_mode,target_text,transcript,match_rate," +
                        "comparison_json,pronunciation_status,review_status,analysis_type,assessment_status,assessment_json,analysis_version,completed_at) " +
                        "VALUES(?,?,1,?,'audio/wav','COMPLETED','SENTENCE_MATCH','신라','실라',50.00,?,'NOT_EVALUATED','NOT_REQUIRED','WORD',?,?,'jamo-align-v1',NOW())",
                id, studentId, String.valueOf(itemId), "[{\"type\":\"SUBSTITUTED\",\"expected\":\"신라\",\"recognized\":\"실라\"}]", assessmentStatus,
                "{\"holdReasons\":[],\"candidates\":[{\"type\":\"SUBSTITUTION\",\"slot\":\"CODA\",\"expected\":\"ㄴ\",\"produced\":\"ㄹ\",\"targetSyllable\":\"신\"}]}");
        return id;
    }

    private String signupTeacher() throws Exception {
        String email = "feedback-t-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"피드백 선생님\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,"
                                + "\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
        return objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }

    private void link(String teacherToken, long studentId) throws Exception {
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", "Bearer " + teacherToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + studentId + ",\"name\":\"피드백 학생\"}")).andExpect(status().isCreated());
    }

    private record Student(long studentId, String token) { String bearer() { return "Bearer " + token; } }

    private Student signupStudent(boolean aiConsent) throws Exception {
        String email = "feedback-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"피드백 학생\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"centerId\":1,\"termsAgreed\":true,\"age\":8,"
                                + "\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":" + aiConsent + ",\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        String token = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        // AI 학습 피드백은 AI 대화와 별도 동의(AI_FEEDBACK)다. 마이페이지 동의와 같은 API로 기록한다.
        if (aiConsent) mvc.perform(put("/api/v1/consents/me/AI_FEEDBACK").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"}"))
                .andExpect(status().isOk());
        return new Student(studentId, token);
    }
}
