package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import com.example.backend.chld.mapper.StudentMapper;
import com.example.backend.chld.service.AudioRetentionService;
import com.example.backend.chld.service.SpeechRecognitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 분석 흐름의 실패·저장 규칙(외부 의존성은 mock). 실제 whisper·DB 연동은 SpeechAnalysisIntegrationTest가 검증한다.
 * - 저장 실패·인식 실패·환각 문구 → 결과를 완료로 저장하지 않고 FAILED로 표시, 녹음은 삭제
 * - 일반 아동: 텍스트 일치율만 저장하고 녹음 즉시 삭제 / 언어재활 아동: 일치율 없이 검토 대기, 녹음 보관
 */
@ExtendWith(OutputCaptureExtension.class)
class SpeechAnalysisServiceImplTest {
    private final StudentMapper mapper = mock(StudentMapper.class);
    private final SpeechRecognitionService recognizer = mock(SpeechRecognitionService.class);
    private final AudioStorageService storage = mock(AudioStorageService.class);
    private final AudioRetentionService retention = mock(AudioRetentionService.class);
    private final AudioStorageService.StoredAudio stored = new AudioStorageService.StoredAudio("/tmp/a.wav", "audio/wav", "a.wav");
    private final MockMultipartFile upload = new MockMultipartFile("audio", "a.wav", "audio/wav", new byte[]{1});
    private SpeechAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpeechAnalysisServiceImpl(mapper, recognizer, new TranscriptComparator(), storage, mock(ExternalProviderService.class),
                retention, new SpeechAssessmentEvaluator(new HangulPhonemeAnalyzer()), "local", java.time.Duration.ofMinutes(5), java.time.Duration.ofSeconds(60));
        when(storage.store(any())).thenReturn(stored);
        when(retention.retentionMonths()).thenReturn(6);
        when(mapper.findRecentItemAnalyses(anyLong(), anyLong(), anyString(), anyString(), anyInt())).thenReturn(List.of());
    }

    private static SpeechRecognitionResult recognized(String text) {
        return new SpeechRecognitionResult(text, new BigDecimal("0.9"), "whisper.cpp", "model.bin", 1500, 100,
                List.of(new SpeechRecognitionResult.SpeechSegment(500, 1000)), 20000, 0);
    }

    @Test
    void databaseSaveFailureMarksAnalysisFailedAndDeletesAudio() {
        when(recognizer.recognize(any())).thenReturn(recognized("라디오"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
        verify(mapper).failAnalysis(anyString(), eq("500 INTERNAL_SERVER_ERROR"));
        verify(storage, atLeastOnce()).discard(stored);
    }

    @Test
    void timingLogRecordsOutcomeAndDurationButNoSpeechContent(CapturedOutput output) {
        when(recognizer.recognize(any())).thenReturn(recognized("타디오"))
                .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "시간 초과"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, null);
        assertThrows(ResponseStatusException.class, () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, null));
        assertTrue(output.getOut().matches("(?s).*speech\\.timing stage=analysis outcome=COMPLETED totalMs=\\d+ staleAfterMs=300000.*"), output.getOut());
        assertTrue(output.getOut().contains("outcome=FAILED:504"), output.getOut());
        for (String sensitive : List.of("타디오", "라디오", stored.path()))
            assertFalse(output.getOut().contains(sensitive), "로그에 음성 내용·목표 문장·파일 경로를 남기지 않는다: " + sensitive);
    }

    /** 기준 시간보다 오래 걸려 이미 STALE_PROCESSING으로 정리된 뒤 끝난 작업(정상 작업의 오탐 실패)은 결과를 덮어쓰지 않고 따로 경고한다. */
    @Test
    void aJobFinishingAfterItWasMarkedStaleIsReportedAsLateWithoutOverwriting(CapturedOutput output) {
        when(recognizer.recognize(any())).thenReturn(recognized("라디오"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        when(mapper.findAnalysisErrorCode(anyString())).thenReturn("STALE_PROCESSING");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
        assertTrue(output.getOut().contains("WARN"), output.getOut());
        assertTrue(output.getOut().contains("outcome=LATE_AFTER_STALE"), output.getOut());
        // 상태 조건(PROCESSING) 때문에 DB에서는 바뀌지 않는다(통합 테스트에서 실제 DB로 확인).
        verify(mapper).failAnalysis(anyString(), eq("500 INTERNAL_SERVER_ERROR"));
    }

    @Test
    void recognitionFailureIsStoredAsFailedWithoutResult() {
        when(recognizer.recognize(any())).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "시간 초과"));
        assertThrows(ResponseStatusException.class, () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, null));
        verify(mapper).failAnalysis(anyString(), eq("504 GATEWAY_TIMEOUT"));
        verify(mapper, never()).completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(storage).discard(stored);
    }

    @Test
    void hallucinatedOrBlankTranscriptIsRejected() {
        when(recognizer.recognize(any())).thenReturn(recognized("시청해주셔서 감사합니다."), recognized(""));
        for (int i = 0; i < 2; i++) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, null));
            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, error.getStatusCode());
        }
        verify(mapper, never()).completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generalLearnerStoresTextMatchRateAndAssessmentWithoutScoreAndDeletesAudio() {
        when(recognizer.recognize(any())).thenReturn(recognized("타디오"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        Map<String,Object> response = service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, null);
        assertEquals("COMPLETED", response.get("status"));
        ArgumentCaptor<BigDecimal> rate = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<String> assessmentJson = ArgumentCaptor.forClass(String.class);
        verify(mapper).completeLocalAnalysis(anyString(), eq("타디오"), any(), rate.capture(), any(), eq("NOT_EVALUATED"), eq("NOT_REQUIRED"),
                any(), any(), eq("WORD"), eq("ERROR_CANDIDATES"), assessmentJson.capture(), eq("jamo-align-v1"));
        assertEquals(new BigDecimal("66.67"), rate.getValue());
        assertTrue(assessmentJson.getValue().contains("\"expected\":\"ㄹ\""), assessmentJson.getValue());
        assertFalse(assessmentJson.getValue().toLowerCase().contains("score"), "자동 분석 근거에 점수를 넣지 않는다");
        verify(storage).discard(stored);
        verify(mapper).markAudioDeleted(anyString());
    }

    @Test
    void therapyLearnerKeepsAudioForReviewAndStoresNoTextMatchRate() {
        when(recognizer.recognize(any())).thenReturn(recognized("라디오"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        service.analyze(1, "THERAPY", 1, 11, "라디오", "ㄹ", upload, null);
        verify(mapper).completeLocalAnalysis(anyString(), eq("라디오"), any(), isNull(), any(), eq("NOT_EVALUATED"), eq("PENDING"),
                any(), any(), eq("WORD"), eq("NO_CANDIDATES"), anyString(), eq("jamo-align-v1"));
        verify(storage, never()).discard(any());
    }

    @Test
    void analysisIsRecordedAsProcessingBeforeInference() {
        when(recognizer.recognize(any())).thenReturn(recognized("라디오"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        service.analyze(5, "GENERAL", 1, 11, "라디오", null, upload, null);
        var order = inOrder(mapper, recognizer);
        order.verify(mapper).insertAnalysis(anyString(), eq(5L), eq(1L), eq("11"), eq(stored.path()), eq("audio/wav"), eq("SENTENCE_MATCH"),
                eq("라디오"), eq("whisper.cpp"), isNull(), eq(6), isNull(), isNull());
        order.verify(recognizer).recognize(Path.of(stored.path()));
    }

    // ── 중복 분석 방지(Idempotency-Key) ──────────────
    private static final String KEY = "rec-0f6c1e2a-key";

    private void completedRun() {
        when(recognizer.recognize(any())).thenReturn(recognized("라디오"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void sameKeyReturnsTheExistingAnalysisWithoutStoringOrInferringAgain() {
        String hash = SpeechAnalysisServiceImpl.requestHash(upload, 1, 11);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(Map.of("analysisId", "an-1", "status", "COMPLETED", "requestHash", hash));
        Map<String,Object> response = service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY);
        assertEquals(Map.of("analysisId", "an-1", "status", "COMPLETED", "reused", true), response);
        verify(storage, never()).store(any());
        verify(recognizer, never()).recognize(any());
        verify(mapper, never()).insertAnalysis(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), any(), any());
    }

    @Test
    void sameKeyWithDifferentRecordingOrItemIsRejected() {
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(Map.of("analysisId", "an-1", "status", "COMPLETED",
                "requestHash", SpeechAnalysisServiceImpl.requestHash(upload, 1, 12)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(recognizer, never()).recognize(any());
    }

    @Test
    void losingAConcurrentInsertReturnsTheWinnerAndDeletesItsOwnAudio() {
        String hash = SpeechAnalysisServiceImpl.requestHash(upload, 1, 11);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(null, Map.of("analysisId", "winner", "status", "PROCESSING", "requestHash", hash));
        when(mapper.insertAnalysis(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), any(), any()))
                .thenThrow(new DuplicateKeyException("uq_analysis_request_key"));
        Map<String,Object> response = service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY);
        assertEquals("winner", response.get("analysisId"));
        assertEquals("PROCESSING", response.get("status"));
        verify(storage).discard(stored);
        verify(recognizer, never()).recognize(any());
        verify(mapper, never()).failAnalysis(anyString(), anyString());
    }

    /** DEF-11: 같은 키의 동시 재시도에서 InnoDB 교착 상태로 문장이 되돌려져도 500이 아니라 재확인·재시도한다. */
    @Test
    void aDeadlockWhileInsertingReusesTheWinnerOrRetriesInsteadOfFailing() {
        String hash = SpeechAnalysisServiceImpl.requestHash(upload, 1, 11);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(null, Map.of("analysisId", "winner", "status", "PROCESSING", "requestHash", hash));
        when(mapper.insertAnalysis(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), any(), any()))
                .thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("deadlock", null));
        Map<String,Object> reused = service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY);
        assertEquals("winner", reused.get("analysisId"));
        verify(recognizer, never()).recognize(any());

        // 다른 요청이 아직 만들지 않았으면 다시 INSERT해 정상 분석한다.
        reset(mapper);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(null);
        when(mapper.findRecentItemAnalyses(anyLong(), anyLong(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(mapper.insertAnalysis(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), any(), any()))
                .thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("deadlock", null)).thenReturn(1);
        when(recognizer.recognize(any())).thenReturn(recognized("라디오"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        assertEquals("COMPLETED", service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY).get("status"));
        verify(mapper, times(2)).insertAnalysis(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), any(), any());

        // 계속 교착 상태면 3번째에 503으로 끝낸다(500 아님, 녹음 삭제).
        reset(mapper, storage);
        when(storage.store(any())).thenReturn(stored);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(null);
        when(mapper.insertAnalysis(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), any(), any()))
                .thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("deadlock", null));
        ResponseStatusException busy = assertThrows(ResponseStatusException.class, () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());
        verify(storage).discard(stored);
    }

    @Test
    void aDeadlockWhileClearingAStuckAnalysisIsTreatedAsLosingTheRace() {
        String hash = SpeechAnalysisServiceImpl.requestHash(upload, 1, 11);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(
                new java.util.HashMap<>(Map.of("analysisId", "stuck", "status", "PROCESSING", "requestHash", hash, "ageSeconds", 900)),
                Map.of("analysisId", "fresh", "status", "PROCESSING", "requestHash", hash));
        when(mapper.failStaleAnalysis(eq("stuck"), anyLong())).thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("deadlock", null));
        Map<String,Object> response = service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY);
        assertEquals("fresh", response.get("analysisId"));
        verify(recognizer, never()).recognize(any());
    }

    @Test
    void retryAfterFailureWithTheSameKeyRunsANewAnalysis() {
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(null); // 실패한 분석은 failAnalysis가 키를 비운다
        when(recognizer.recognize(any())).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "시간 초과")).thenReturn(recognized("라디오"));
        when(mapper.completeLocalAnalysis(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        assertThrows(ResponseStatusException.class, () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY));
        assertEquals("COMPLETED", service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY).get("status"));
        verify(mapper, times(2)).insertAnalysis(anyString(), eq(1L), eq(1L), eq("11"), anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), eq(KEY), anyString());
    }

    @Test
    void requestsWithoutKeyAreNotDeduplicatedAndInvalidKeysAreRejected() {
        completedRun();
        service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, null);
        service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, " ");
        verify(mapper, never()).findAnalysisByRequestKey(anyLong(), anyString());
        verify(recognizer, times(2)).recognize(any());
        for (String invalid : new String[]{"short", "has space in key", "x".repeat(65), "키값한글입니다아"}) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, invalid));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
    }

    // ── 처리 중(PROCESSING) 고착 복구 ──────────────
    private Map<String,Object> processing(String id, long ageSeconds, String hash) {
        Map<String,Object> row = new java.util.HashMap<>();
        row.put("analysisId", id); row.put("status", "PROCESSING"); row.put("requestHash", hash);
        row.put("audioPath", "/storage/" + id + ".wav"); row.put("ageSeconds", ageSeconds);
        return row;
    }

    @Test
    void staleProcessingWithTheSameKeyIsFailedAndANewAnalysisRuns() {
        String hash = SpeechAnalysisServiceImpl.requestHash(upload, 1, 11);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(processing("stuck", 600, hash));
        when(mapper.failStaleAnalysis("stuck", 300)).thenReturn(1);
        completedRun();
        Map<String,Object> response = service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY);
        assertEquals("COMPLETED", response.get("status"));
        assertNotEquals("stuck", response.get("analysisId"));
        verify(storage).deleteStoredPath("/storage/stuck.wav");
        verify(mapper).markAudioDeleted("stuck");
        verify(recognizer).recognize(any());
    }

    @Test
    void processingWithinTheThresholdIsReusedNotRestarted() {
        String hash = SpeechAnalysisServiceImpl.requestHash(upload, 1, 11);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(processing("running", 120, hash));
        Map<String,Object> response = service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY);
        assertEquals(Map.of("analysisId", "running", "status", "PROCESSING", "reused", true), response);
        verify(mapper, never()).failStaleAnalysis(anyString(), anyLong());
        verify(recognizer, never()).recognize(any());
    }

    @Test
    void losingTheStaleRecoveryRaceReusesWhatTheOtherRequestProduced() {
        String hash = SpeechAnalysisServiceImpl.requestHash(upload, 1, 11);
        when(mapper.findAnalysisByRequestKey(1, KEY)).thenReturn(processing("stuck", 600, hash),
                Map.of("analysisId", "finished", "status", "COMPLETED", "requestHash", hash, "ageSeconds", 1));
        when(mapper.failStaleAnalysis("stuck", 300)).thenReturn(0); // 다른 요청이 먼저 정리·재분석함
        Map<String,Object> response = service.analyze(1, "GENERAL", 1, 11, "라디오", "ㄹ", upload, KEY);
        assertEquals("finished", response.get("analysisId"));
        verify(storage, never()).deleteStoredPath(anyString());
        verify(recognizer, never()).recognize(any());
    }

    @Test
    void sweepFailsOnlyRowsItActuallyTransitionedAndDeletesTheirAudio() {
        when(mapper.findStaleProcessingAnalyses(300, 100)).thenReturn(List.of(
                Map.of("analysisId", "a", "audioPath", "/storage/a.wav"), Map.of("analysisId", "b", "audioPath", "/storage/b.wav")));
        when(mapper.failStaleAnalysis("a", 300)).thenReturn(1);
        when(mapper.failStaleAnalysis("b", 300)).thenReturn(0); // 조회 직후 정상 완료된 분석
        assertEquals(1, service.recoverStaleAnalyses());
        verify(storage).deleteStoredPath("/storage/a.wav");
        verify(storage, never()).deleteStoredPath("/storage/b.wav");
    }

    @Test
    void thresholdIsNeverShorterThanTheLongestLegitimateAnalysis() {
        assertEquals(300, SpeechAnalysisServiceImpl.staleThresholdSeconds(java.time.Duration.ofMinutes(5), java.time.Duration.ofSeconds(60)));
        assertEquals(240, SpeechAnalysisServiceImpl.staleThresholdSeconds(java.time.Duration.ofSeconds(30), java.time.Duration.ofSeconds(60)));
        assertEquals(420, SpeechAnalysisServiceImpl.staleThresholdSeconds(java.time.Duration.ofMinutes(5), java.time.Duration.ofSeconds(120)));
    }
}
