package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import java.nio.charset.StandardCharsets;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.backend.support.TestPrerequisites.requireSpeech;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 녹음 업로드 → 로컬 whisper.cpp 추론 → 목표 문장 비교 → DB 저장 → 조회/연습 기록/선생님 검토까지 실제로 실행한다.
 * 일반 아동(텍스트 일치율)과 언어재활 아동(발음 미평가 + 선생님 검토), 단어/문장 유형, 판정 보류, 오류 후보와 교사 확정 결과 분리를 검증한다.
 * 입력 음성은 macOS TTS 합성음이며 아동 음성 성능을 대표하지 않는다.
 */
@SpringBootTest(properties = {
        "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "app.speech.engine=local",
        "app.audio.storage-path=${java.io.tmpdir}/chatterland-test-audio"
})
@AutoConfigureMockMvc
@Transactional
class SpeechAnalysisIntegrationTest {
    private static final String MODEL = System.getenv().getOrDefault("WHISPER_MODEL_PATH",
            System.getProperty("user.home") + "/.cache/chatterland/whisper/ggml-large-v3-turbo-q5_0.bin");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void requireLocalModel() {
        requireSpeech(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 음성 통합 테스트를 건너뜁니다.");
    }

    @Test
    void generalLearnerGetsSentenceMatchWithoutPronunciationScore() throws Exception {
        Student student = signupStudent();
        long exerciseId = 4; // 천천히 말하기 (fluency)
        long itemId = itemId(exerciseId, "오늘은 날씨가 좋아요.");

        String analyze = mvc.perform(multipart("/api/v1/speech/analyze").file(audio("sentence-weather-recorded.wav"))
                        .part(part("exerciseId", String.valueOf(exerciseId))).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        String analysisId = objectMapper.readTree(analyze).path("analysisId").asText();

        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationMode").value("SENTENCE_MATCH"))
                .andExpect(jsonPath("$.targetText").value("오늘은 날씨가 좋아요."))
                .andExpect(jsonPath("$.matchRate").value(100.0))
                .andExpect(jsonPath("$.textMatchRate").value(100.0))
                .andExpect(jsonPath("$.analysisType").value("SENTENCE"))
                .andExpect(jsonPath("$.assessmentStatus").value("NO_CANDIDATES"))
                .andExpect(jsonPath("$.holdReasons").isEmpty())
                .andExpect(jsonPath("$.analysisVersion").value("jamo-align-v1"))
                .andExpect(jsonPath("$.speechTiming.source").value("VAD"))
                .andExpect(jsonPath("$.speechTiming.speechMs").isNumber())
                .andExpect(jsonPath("$.speechTiming.syllablesPerSecond").isNumber())
                .andExpect(jsonPath("$.comparison.words[0].type").value("MATCH"))
                .andExpect(jsonPath("$.pronunciationScore").doesNotExist())
                .andExpect(jsonPath("$.speechRateScore").doesNotExist())
                .andExpect(jsonPath("$.fluencyScore").doesNotExist())
                .andExpect(jsonPath("$.overallScore").doesNotExist())
                .andExpect(jsonPath("$.pronunciationStatus").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.reviewStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.modelName").value(Path.of(MODEL).getFileName().toString()));
        // 일반 연습 녹음은 인식 직후 삭제된다.
        assertNull(jdbc.queryForObject("SELECT audio_path FROM speech_analyses WHERE analysis_id=?", String.class, analysisId));

        String attemptBody = "{\"exerciseId\":\"" + exerciseId + "\",\"itemId\":\"" + itemId + "\",\"audioId\":\"" + analysisId + "\"}";
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token))
                        .contentType(MediaType.APPLICATION_JSON).content(attemptBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").doesNotExist()).andExpect(jsonPath("$.matchRate").value(100.0));
        // 측정하지 않은 점수를 끼워 넣으면 거부한다.
        mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token))
                        .contentType(MediaType.APPLICATION_JSON).content(attemptBody.replace("{", "{\"score\":100,")))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAttempts").value(1))
                .andExpect(jsonPath("$.averageMatchRate").value(100)).andExpect(jsonPath("$.overallScore").doesNotExist());
        mvc.perform(get("/api/v1/students/me/history").param("type", "word").header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].matchRate").value(100.0));
    }

    @Test
    void therapyLearnerIsNotScoredAndRequiresTeacherReview() throws Exception {
        Student student = signupStudent();
        String teacherEmail = "speech-teacher-" + UUID.randomUUID() + "@example.test";
        signupTeacher(teacherEmail);
        String teacherToken = login(teacherEmail);
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + student.studentId + ",\"name\":\"음성 학생\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.learnerType").value("GENERAL"));
        mvc.perform(put("/api/v1/teachers/me/students/{id}", student.studentId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"음성 학생\",\"age\":8,\"learnerType\":\"THERAPY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.learnerType").value("THERAPY"));

        long exerciseId = 1; // ㄹ 발음
        long itemId = itemId(exerciseId, "라디오");
        String analyze = mvc.perform(multipart("/api/v1/speech/analyze").file(audio("word-radio-recorded.wav"))
                        .part(part("exerciseId", String.valueOf(exerciseId))).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String analysisId = objectMapper.readTree(analyze).path("analysisId").asText();

        mvc.perform(get("/api/v1/speech/analyses/{id}", analysisId).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationMode").value("PRONUNCIATION_REVIEW"))
                .andExpect(jsonPath("$.transcript").isNotEmpty())
                .andExpect(jsonPath("$.matchRate").doesNotExist())
                .andExpect(jsonPath("$.textMatchRate").doesNotExist())
                .andExpect(jsonPath("$.analysisType").value("WORD"))
                .andExpect(jsonPath("$.targetPhonemes[0]").value("ㄹ"))
                .andExpect(jsonPath("$.pronunciationScore").doesNotExist())
                .andExpect(jsonPath("$.pronunciationStatus").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"));

        mvc.perform(get("/api/v1/teachers/me/students/{id}/speech-analyses", student.studentId)
                        .param("reviewStatus", "PENDING").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].analysisId").value(analysisId))
                .andExpect(jsonPath("$.content[0].hasAudio").value(true))
                .andExpect(jsonPath("$.content[0].audioPath").doesNotExist());
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/audio", analysisId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(content().contentType("audio/wav"));
        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", student.studentId)
                        .param("startDate", java.time.LocalDate.now().minusDays(1).toString()).param("endDate", java.time.LocalDate.now().toString())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingReviews").value(1));

        // 다른 선생님은 녹음과 분석 결과에 접근할 수 없다.
        String otherEmail = "speech-other-" + UUID.randomUUID() + "@example.test";
        signupTeacher(otherEmail);
        String otherToken = login(otherEmail);
        mvc.perform(get("/api/v1/teachers/me/speech-analyses/{id}/audio", analysisId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"ACCEPTABLE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/teachers/me/students/{id}/speech-analyses", student.studentId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgement\":\"NEEDS_PRACTICE\",\"confirmedErrors\":[{\"phoneme\":\"ㄹ\",\"errorType\":\"GUESS\"}]}"))
                .andExpect(status().isBadRequest());
        String autoCandidates = jdbc.queryForObject("SELECT assessment_json FROM speech_analyses WHERE analysis_id=?", String.class, analysisId);
        mvc.perform(patch("/api/v1/teachers/me/speech-analyses/{id}/review", analysisId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"judgement\":\"NEEDS_PRACTICE\",\"note\":\"ㄹ 받침 연습 필요\","
                                + "\"confirmedErrors\":[{\"phoneme\":\"ㄹ\",\"errorType\":\"SUBSTITUTION\",\"produced\":\"ㄷ\",\"position\":\"어두 초성\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.teacherJudgement").value("NEEDS_PRACTICE"))
                .andExpect(jsonPath("$.teacherConfirmedErrors[0].phoneme").value("ㄹ"))
                .andExpect(jsonPath("$.teacherConfirmedErrors[0].produced").value("ㄷ"))
                .andExpect(jsonPath("$.pronunciationStatus").value("NOT_EVALUATED"));
        // 교사 확정 결과는 자동 분석 결과(오류 후보)를 덮어쓰지 않고 별도 컬럼에 저장된다.
        assertEquals(autoCandidates, jdbc.queryForObject("SELECT assessment_json FROM speech_analyses WHERE analysis_id=?", String.class, analysisId));
        assertTrue(jdbc.queryForObject("SELECT teacher_confirmed_json FROM speech_analyses WHERE analysis_id=?", String.class, analysisId).contains("SUBSTITUTION"));

        // PDF 리포트에 한글 이름·AI 인식 결과·선생님 판단과 메모, '미평가'가 실제 데이터로 들어간다.
        byte[] pdf = mvc.perform(get("/api/v1/teachers/me/students/{id}/report/download", student.studentId)
                        .param("startDate", java.time.LocalDate.now().minusDays(1).toString()).param("endDate", java.time.LocalDate.now().toString())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("음성 학생"), text);
            assertTrue(text.contains("언어재활 아동"));
            assertTrue(text.replaceAll("\\s+", "").contains("연습필요/ㄹ받침연습필요"), text);
            assertTrue(text.contains("미평가"));
            assertTrue(text.contains("라디오"));
        }
    }

    @Test
    void silentOrInvalidRecordingFailsWithoutStoringResult() throws Exception {
        Student student = signupStudent();
        long itemId = itemId(1, "라디오");
        mvc.perform(multipart("/api/v1/speech/analyze").file(audio("silence.wav"))
                        .part(part("exerciseId", "1")).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isUnprocessableContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='FAILED' AND audio_path IS NULL", Integer.class, student.studentId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='COMPLETED'", Integer.class, student.studentId));

        MockMultipartFile empty = new MockMultipartFile("audio", "speech.wav", "audio/wav", new byte[0]);
        mvc.perform(multipart("/api/v1/speech/analyze").file(empty).part(part("exerciseId", "1")).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isBadRequest());
        MockMultipartFile fake = new MockMultipartFile("audio", "speech.wav", "audio/wav", "not audio".getBytes());
        mvc.perform(multipart("/api/v1/speech/analyze").file(fake).part(part("exerciseId", "1")).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void wordAnalysisRecordsTargetPhonemePositionsAndRepetition() throws Exception {
        Student student = signupStudent();
        long itemId = itemId(1, "라디오");
        String first = analyze(student, 1, itemId, "word-radio-recorded.wav");
        mvc.perform(get("/api/v1/speech/analyses/{id}", first).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisType").value("WORD"))
                .andExpect(jsonPath("$.assessmentStatus").value("NO_CANDIDATES"))
                .andExpect(jsonPath("$.textMatchRate").value(100.0))
                .andExpect(jsonPath("$.targetPhonemes[0]").value("ㄹ"))
                .andExpect(jsonPath("$.targetPositions[0].syllable").value("라"))
                .andExpect(jsonPath("$.targetPositions[0].slot").value("ONSET"))
                .andExpect(jsonPath("$.targetPositions[0].wordPosition").value("INITIAL"))
                .andExpect(jsonPath("$.phonemeCandidates").isEmpty())
                .andExpect(jsonPath("$.repetition.previousAttempts").value(0))
                .andExpect(jsonPath("$.pronunciationScore").doesNotExist())
                .andExpect(jsonPath("$.overallScore").doesNotExist());
        String second = analyze(student, 1, itemId, "word-radio-recorded.wav");
        mvc.perform(get("/api/v1/speech/analyses/{id}", second).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetition.previousAttempts").value(1))
                .andExpect(jsonPath("$.repetition.sameTranscriptCount").value(1))
                .andExpect(jsonPath("$.repetition.recent[0].textMatchRate").value(100.0));
    }

    @Test
    void mispronouncedWordProducesUnconfirmedCandidatesNotAScore() throws Exception {
        Student student = signupStudent();
        long itemId = itemId(1, "라디오");
        // 합성음 "다디오"를 목표 "라디오"로 분석한다(ㄹ → ㄷ 대치를 흉내 낸 입력).
        String first = analyze(student, 1, itemId, "word-dadio-recorded.wav");
        String body = mvc.perform(get("/api/v1/speech/analyses/{id}", first).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisType").value("WORD"))
                .andExpect(jsonPath("$.assessmentStatus").value("ERROR_CANDIDATES"))
                .andExpect(jsonPath("$.assessmentBasis").value("ORTHOGRAPHIC_JAMO_FROM_ASR_TEXT"))
                .andExpect(jsonPath("$.phonemeCandidates[0].type").value("SUBSTITUTION"))
                .andExpect(jsonPath("$.phonemeCandidates[0].slot").value("ONSET"))
                .andExpect(jsonPath("$.phonemeCandidates[0].expected").value("ㄹ"))
                .andExpect(jsonPath("$.phonemeCandidates[0].targetPhoneme").value(true))
                .andExpect(jsonPath("$.pronunciationScore").doesNotExist())
                .andExpect(jsonPath("$.pronunciationStatus").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.teacherConfirmedErrors").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertTrue(objectMapper.readTree(body).path("textMatchRate").asDouble() < 100, body);
        String second = analyze(student, 1, itemId, "word-dadio-recorded.wav");
        mvc.perform(get("/api/v1/speech/analyses/{id}", second).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetition.previousAttempts").value(1))
                .andExpect(jsonPath("$.repetition.recurringCandidates[0]").value(org.hamcrest.Matchers.startsWith("SUBSTITUTION:ONSET:ㄹ>")));
    }

    @Test
    void cutOffRecordingIsHeldInsteadOfJudged() throws Exception {
        Student student = signupStudent();
        long itemId = itemId(4, "오늘은 날씨가 좋아요.");
        // 문장 앞부분 60%만 남고 말소리가 녹음 끝까지 이어지는 녹음(말하는 중에 녹음을 멈춘 경우)
        String id = analyze(student, 4, itemId, "sentence-weather-cut.wav");
        mvc.perform(get("/api/v1/speech/analyses/{id}", id).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.analysisType").value("SENTENCE"))
                .andExpect(jsonPath("$.assessmentStatus").value("HOLD"))
                .andExpect(jsonPath("$.holdReasons").value(org.hamcrest.Matchers.hasItem("SPEECH_CUT_OFF_END")))
                .andExpect(jsonPath("$.pronunciationScore").doesNotExist());
    }

    @Test
    void noiseAndNonSpeechAreRejectedWithoutResult() throws Exception {
        Student student = signupStudent();
        long itemId = itemId(1, "라디오");
        for (String file : new String[]{"room-noise.wav", "tone-1khz.wav"}) {
            mvc.perform(multipart("/api/v1/speech/analyze").file(audio(file))
                            .part(part("exerciseId", "1")).part(part("itemId", String.valueOf(itemId)))
                            .header("Authorization", bearer(student.token)))
                    .andExpect(status().isUnprocessableContent());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND status='COMPLETED'", Integer.class, student.studentId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM speech_analyses WHERE student_id=? AND transcript IS NOT NULL", Integer.class, student.studentId));
    }

    @Test
    void sentenceOmissionAndAdditionProduceSyllableCandidates() throws Exception {
        Student student = signupStudent();
        // "토끼가 걸어가요"(목표: 토끼가 숲속을 걸어가요) → 숲·속·을 음절 생략 후보, 단어 비교에서 누락
        String omission = analyze(student, 5, itemId(5, "토끼가 숲속을 걸어가요."), "sentence-turtle-omission-recorded.wav");
        mvc.perform(get("/api/v1/speech/analyses/{id}", omission).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisType").value("SENTENCE"))
                .andExpect(jsonPath("$.assessmentStatus").value("ERROR_CANDIDATES"))
                .andExpect(jsonPath("$.textMatchRate").value(70.0))
                .andExpect(jsonPath("$.phonemeCandidates.length()").value(3))
                .andExpect(jsonPath("$.phonemeCandidates[0].type").value("SYLLABLE_OMISSION"))
                .andExpect(jsonPath("$.phonemeCandidates[0].word").value("숲속을"))
                .andExpect(jsonPath("$.phonemeCandidates[0].wordIndex").value(2))
                .andExpect(jsonPath("$.comparison.words[1].type").value("MISSING"))
                .andExpect(jsonPath("$.comparison.words[1].expected").value("숲속을"));
        // "오늘은 날씨가 정말 좋아요"(목표: 오늘은 날씨가 좋아요) → 정·말 음절 첨가 후보
        String addition = analyze(student, 4, itemId(4, "오늘은 날씨가 좋아요."), "sentence-weather-addition-recorded.wav");
        mvc.perform(get("/api/v1/speech/analyses/{id}", addition).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentStatus").value("ERROR_CANDIDATES"))
                .andExpect(jsonPath("$.textMatchRate").value(77.78))
                .andExpect(jsonPath("$.phonemeCandidates.length()").value(2))
                .andExpect(jsonPath("$.phonemeCandidates[0].type").value("SYLLABLE_ADDITION"))
                .andExpect(jsonPath("$.phonemeCandidates[0].produced").value("정"))
                .andExpect(jsonPath("$.comparison.words[2].type").value("INSERTED"))
                .andExpect(jsonPath("$.pronunciationScore").doesNotExist());
    }

    @Test
    void reportStatisticsMatchStoredAttemptsAndDuplicateSaveIsNotCounted() throws Exception {
        Student student = signupStudent();
        String teacherEmail = "speech-report-" + UUID.randomUUID() + "@example.test";
        signupTeacher(teacherEmail);
        String teacherToken = login(teacherEmail);
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + student.studentId + ",\"name\":\"리포트 학생\"}"))
                .andExpect(status().isCreated());
        long itemId = itemId(1, "라디오");
        for (String file : new String[]{"word-radio-recorded.wav", "word-dadio-recorded.wav"}) {
            String id = analyze(student, 1, itemId, file);
            String body = "{\"exerciseId\":\"1\",\"itemId\":\"" + itemId + "\",\"audioId\":\"" + id + "\"}";
            for (int i = 0; i < 2; i++) // 같은 분석의 중복 저장(재시도)은 한 건으로 남아야 한다.
                mvc.perform(post("/api/v1/practice/attempts").header("Authorization", bearer(student.token))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempts WHERE student_id=?", Integer.class, student.studentId));
        int expected = jdbc.queryForObject("SELECT ROUND(AVG(match_rate),0) FROM practice_attempts WHERE student_id=?", Integer.class, student.studentId);
        assertTrue(expected < 100, "틀린 발음 녹음이 포함되어 평균이 100보다 작아야 한다");
        String today = java.time.LocalDate.now().toString();
        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", student.studentId).param("startDate", today).param("endDate", today)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageMatchRate").value(expected))
                .andExpect(jsonPath("$.matchedAttempts").value(2))
                .andExpect(jsonPath("$.overallScore").doesNotExist());
        byte[] pdf = mvc.perform(get("/api/v1/teachers/me/students/{id}/report/download", student.studentId)
                        .param("startDate", today).param("endDate", today).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertTrue(text.contains("평균텍스트일치율" + expected + "%"), text);
        }
        // 다른 학생의 기록은 이 학생의 통계에 섞이지 않는다.
        Student other = signupStudent();
        analyze(other, 1, itemId, "word-radio-recorded.wav");
        mvc.perform(get("/api/v1/teachers/me/students/{id}/analytics", student.studentId).param("startDate", today).param("endDate", today)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.matchedAttempts").value(2));
    }

    @Test
    void teacherCanReviseConfirmedErrorsWhileAutomaticAssessmentStaysIntact() throws Exception {
        Student student = signupStudent();
        String teacherEmail = "speech-revise-" + UUID.randomUUID() + "@example.test";
        signupTeacher(teacherEmail);
        String teacherToken = login(teacherEmail);
        mvc.perform(post("/api/v1/teachers/me/students").header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + student.studentId + ",\"name\":\"검토 학생\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/teachers/me/students/{id}", student.studentId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"검토 학생\",\"age\":8,\"learnerType\":\"THERAPY\"}"))
                .andExpect(status().isOk());
        String id = analyze(student, 1, itemId(1, "라디오"), "word-dadio-recorded.wav");
        String automatic = jdbc.queryForObject("SELECT assessment_json FROM speech_analyses WHERE analysis_id=?", String.class, id);
        String review = "/api/v1/teachers/me/speech-analyses/{id}/review";
        mvc.perform(patch(review, id).header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgement\":\"NEEDS_PRACTICE\",\"confirmedErrors\":[{\"phoneme\":\"ㄹ\",\"errorType\":\"SUBSTITUTION\",\"produced\":\"ㅌ\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teacherConfirmedErrors.length()").value(1));
        // 다시 들어 보고 "ㄷ 대치"가 아니라 "왜곡"으로 수정 → 이전 확정 결과를 대체한다.
        mvc.perform(patch(review, id).header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgement\":\"NEEDS_PRACTICE\",\"note\":\"왜곡\",\"confirmedErrors\":[{\"phoneme\":\"ㄹ\",\"errorType\":\"DISTORTION\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/teachers/me/students/{id}/speech-analyses", student.studentId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].teacherConfirmedErrors.length()").value(1))
                .andExpect(jsonPath("$.content[0].teacherConfirmedErrors[0].errorType").value("DISTORTION"))
                .andExpect(jsonPath("$.content[0].phonemeCandidates[0].expected").value("ㄹ"))
                .andExpect(jsonPath("$.content[0].reviewStatus").value("REVIEWED"));
        // 확정 오류 없이 "목표 발음에 가까움"으로 바꾸면 확정 오류가 비워진다.
        mvc.perform(patch(review, id).header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgement\":\"ACCEPTABLE\",\"confirmedErrors\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teacherConfirmedErrors").doesNotExist());
        assertEquals(automatic, jdbc.queryForObject("SELECT assessment_json FROM speech_analyses WHERE analysis_id=?", String.class, id));
        // 학생 본인도 같은 확정 결과를 조회한다(다른 학생은 조회 불가는 AuthorizationIntegrationTest에서 확인).
        mvc.perform(get("/api/v1/speech/analyses/{id}", id).header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teacherJudgement").value("ACCEPTABLE"));
    }

    private String analyze(Student student, long exerciseId, long itemId, String file) throws Exception {
        String response = mvc.perform(multipart("/api/v1/speech/analyze").file(audio(file))
                        .part(part("exerciseId", String.valueOf(exerciseId))).part(part("itemId", String.valueOf(itemId)))
                        .header("Authorization", bearer(student.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("analysisId").asText();
    }

    private MockMultipartFile audio(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(getClass().getResource("/speech/" + name).toURI()));
        return new MockMultipartFile("audio", name, "audio/wav", bytes);
    }

    private MockPart part(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private long itemId(long exerciseId, String text) {
        return jdbc.queryForObject("SELECT item_id FROM exercise_items WHERE exercise_id=? AND text_value=? ORDER BY item_id LIMIT 1", Long.class, exerciseId, text);
    }

    private Student signupStudent() throws Exception {
        String email = "speech-student-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"name\":\"음성 학생\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"voice\":true,\"aiChat\":true,\"policyVersion\":\"2026-10-01\",\"guardianConfirmed\":true,\"guardianName\":\"보호자\",\"guardianRelation\":\"부모\"},\"age\":8}"))
                .andExpect(status().isCreated());
        long studentId = jdbc.queryForObject("SELECT student_id FROM student_profiles WHERE user_id=(SELECT user_id FROM users WHERE email=?)", Long.class, email);
        return new Student(studentId, login(email));
    }

    private void signupTeacher(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"name\":\"검토 치료사\",\"email\":\"" + email + "\",\"password\":\"Chatterland!234\",\"centerId\":1,\"termsAgreed\":true,\"consents\":{\"privacy\":true,\"policyVersion\":\"2026-10-01\"}}"))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Chatterland!234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode payload = objectMapper.readTree(response);
        return payload.path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record Student(long studentId, String token) { }
}

