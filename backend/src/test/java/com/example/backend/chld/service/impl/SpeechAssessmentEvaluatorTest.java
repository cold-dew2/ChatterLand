package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment;
import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import com.example.backend.chld.dto.response.SpeechRecognitionResult.SpeechSegment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 녹음 측정값에 따른 판정 보류(HOLD) 규칙과 발화 시간 계산을 확인한다. */
class SpeechAssessmentEvaluatorTest {
    private final SpeechAssessmentEvaluator evaluator = new SpeechAssessmentEvaluator(new HangulPhonemeAnalyzer());

    private static SpeechRecognitionResult result(long audioMs, List<SpeechSegment> segments, int peak, double clipped) {
        return new SpeechRecognitionResult("", BigDecimal.ONE, "whisper.cpp", "model", audioMs, 10, segments, peak, clipped);
    }

    @Test
    void cleanWordHasNoHoldAndMeasuresTiming() {
        SpeechAssessment a = evaluator.evaluate("라디오", "라디오", result(1500, List.of(new SpeechSegment(500, 1000)), 20000, 0), "ㄹ", List.of());
        assertEquals("WORD", a.analysisType());
        assertEquals("NO_CANDIDATES", a.assessmentStatus());
        assertTrue(a.holdReasons().isEmpty());
        assertEquals(500L, a.timing().speechMs());
        assertEquals(500L, a.timing().leadingSilenceMs());
        assertEquals(500L, a.timing().trailingSilenceMs());
        assertEquals(new BigDecimal("6.00"), a.timing().syllablesPerSecond());
    }

    @Test
    void sentenceTimingCountsPauses() {
        SpeechAssessment a = evaluator.evaluate("오늘은 날씨가 좋아요", "오늘은 날씨가 좋아요",
                result(3000, List.of(new SpeechSegment(400, 1200), new SpeechSegment(1900, 2600)), 20000, 0), null, List.of());
        assertEquals("SENTENCE", a.analysisType());
        assertEquals(1, a.timing().pauseCount());
        assertEquals(700L, a.timing().longestPauseMs());
        assertEquals(1500L, a.timing().speechMs());
    }

    @Test
    void uncertainRecordingsAreHeld() {
        List<SpeechSegment> ok = List.of(new SpeechSegment(500, 1000));
        assertEquals(List.of("SPEECH_CUT_OFF_END"), evaluator.evaluate("라디오", "라디오", result(1050, ok, 20000, 0), null, List.of()).holdReasons());
        assertEquals(List.of("SPEECH_CUT_OFF_START"), evaluator.evaluate("라디오", "라디오", result(1500, List.of(new SpeechSegment(0, 1000)), 20000, 0), null, List.of()).holdReasons());
        assertEquals(List.of("SPEECH_TOO_SHORT"), evaluator.evaluate("불", "불", result(1500, List.of(new SpeechSegment(500, 650)), 20000, 0), null, List.of()).holdReasons());
        assertEquals(List.of("CLIPPING"), evaluator.evaluate("라디오", "라디오", result(1500, ok, 32767, 0.02), null, List.of()).holdReasons());
        assertEquals(List.of("LOW_VOLUME"), evaluator.evaluate("라디오", "라디오", result(1500, ok, 900, 0), null, List.of()).holdReasons());
        assertEquals(List.of("NON_HANGUL_TRANSCRIPT"), evaluator.evaluate("사과 세 개", "사과 3개", result(1500, ok, 20000, 0), null, List.of()).holdReasons());
        assertEquals(List.of("LENGTH_MISMATCH"), evaluator.evaluate("포도", "포도 포도 포도", result(1500, ok, 20000, 0), null, List.of()).holdReasons());
        SpeechAssessment held = evaluator.evaluate("오늘은 날씨가 좋아요", "오늘", result(1500, ok, 20000, 0), null, List.of());
        assertEquals("HOLD", held.assessmentStatus());
        assertTrue(held.holdReasons().contains("LENGTH_MISMATCH"));
    }

    @Test
    void timingIsUnavailableWithoutVad() {
        SpeechAssessment a = evaluator.evaluate("라디오", "라디오", result(1500, null, 20000, 0), null, List.of());
        assertEquals("UNAVAILABLE", a.timing().source());
        assertNull(a.timing().speechMs());
        assertEquals("NO_CANDIDATES", a.assessmentStatus());
    }

    @Test
    void repetitionFindsRecurringCandidates() {
        List<SpeechAssessmentEvaluator.PreviousAnalysis> previous = List.of(
                new SpeechAssessmentEvaluator.PreviousAnalysis("2026-10-02 10:00", "다디오", new BigDecimal("66.67"), "ERROR_CANDIDATES", List.of("SUBSTITUTION:ONSET:ㄹ>ㄷ")),
                new SpeechAssessmentEvaluator.PreviousAnalysis("2026-10-02 09:00", "라디오", new BigDecimal("100.00"), "NO_CANDIDATES", List.of()));
        SpeechAssessment a = evaluator.evaluate("라디오", "다디오", result(1500, List.of(new SpeechSegment(500, 1000)), 20000, 0), "ㄹ", previous);
        assertEquals("ERROR_CANDIDATES", a.assessmentStatus());
        assertEquals(2, a.repetition().previousAttempts());
        assertEquals(1, a.repetition().sameTranscriptCount());
        assertEquals(List.of("SUBSTITUTION:ONSET:ㄹ>ㄷ"), a.repetition().recurringCandidates());
    }
}
