package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment;
import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import com.example.backend.chld.dto.response.SpeechRecognitionResult.SpeechSegment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 녹음 측정값에 따른 판정 보류(HOLD) 규칙, 발화 시간 계산, 말하기 속도·멈춤 측정값(점수 아님)을 확인한다. */
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

    private static final String SENTENCE = "오늘은 날씨가 좋아요"; // 9음절
    private static final List<SpeechSegment> PAUSED = List.of(new SpeechSegment(500, 1500), new SpeechSegment(1800, 2600), new SpeechSegment(2700, 3500));

    private SpeechAssessment.SpeechMetrics metrics(String target, String transcript, long audioMs, List<SpeechSegment> segments) {
        return evaluator.evaluate(target, transcript, result(audioMs, segments, 20000, 0), null, List.of()).metrics();
    }

    @Test
    void speakingRateAndPausesAreMeasuredWithinTheUtteranceOnly() {
        SpeechAssessment.SpeechMetrics m = metrics(SENTENCE, SENTENCE, 4000, PAUSED);
        assertEquals("speech-metrics-v1", m.version());
        // 발화 구간 500~3500ms = 3000ms, 말소리 1000+800+800 = 2600ms
        assertEquals("MEASURED", m.speakingRate().status());
        assertEquals(9, m.speakingRate().syllables());
        assertEquals("ASR_TRANSCRIPT_SYLLABLES", m.speakingRate().syllableBasis());
        assertEquals(3000L, m.speakingRate().utteranceMs());
        assertEquals(2600L, m.speakingRate().speechMs());
        assertEquals(new BigDecimal("3.00"), m.speakingRate().syllablesPerSecond());
        assertEquals(new BigDecimal("3.46"), m.speakingRate().articulationRate());
        // 무음 300ms는 멈춤, 100ms는 멈춤 아님(기준 250ms)
        assertEquals("MEASURED", m.pauses().status());
        assertEquals(1, m.pauses().pauseCount());
        assertEquals(300L, m.pauses().totalPauseMs());
        assertEquals(300L, m.pauses().longestPauseMs());
        assertEquals(new BigDecimal("0.100"), m.pauses().pauseRatio());
        assertEquals(3, m.pauses().speechSegmentCount());
        assertEquals(250L, m.pauses().pauseThresholdMs());
    }

    @Test
    void recordingMarginsBeforeAndAfterSpeechDoNotChangeTheMeasurements() {
        List<SpeechSegment> shifted = PAUSED.stream().map(s -> new SpeechSegment(s.startMs() + 1500, s.endMs() + 1500)).toList();
        SpeechAssessment.SpeechMetrics base = metrics(SENTENCE, SENTENCE, 4000, PAUSED);
        SpeechAssessment.SpeechMetrics later = metrics(SENTENCE, SENTENCE, 9000, shifted); // 앞 2초·뒤 3.5초 여유
        assertEquals(base.speakingRate(), later.speakingRate());
        assertEquals(base.pauses(), later.pauses());
    }

    @Test
    void pauseThresholdAndRateMinimumsAreInclusiveBoundaries() {
        List<SpeechSegment> at250 = List.of(new SpeechSegment(500, 1000), new SpeechSegment(1250, 2000));
        List<SpeechSegment> at249 = List.of(new SpeechSegment(500, 1000), new SpeechSegment(1249, 2000));
        assertEquals(1, metrics(SENTENCE, SENTENCE, 3000, at250).pauses().pauseCount());
        assertEquals(0, metrics(SENTENCE, SENTENCE, 3000, at249).pauses().pauseCount());
        assertEquals(0L, metrics(SENTENCE, SENTENCE, 3000, at249).pauses().totalPauseMs());
        // 발화 구간 1000ms·4음절은 측정, 999ms·3음절은 측정하지 않음
        assertEquals("MEASURED", metrics("사과 주스", "사과 주스", 3000, List.of(new SpeechSegment(500, 1500))).speakingRate().status());
        SpeechAssessment.SpeakingRateMetric short999 = metrics("사과 주스", "사과 주스", 3000, List.of(new SpeechSegment(500, 1499))).speakingRate();
        assertEquals("UNAVAILABLE", short999.status());
        assertEquals("UTTERANCE_TOO_SHORT", short999.reason());
        assertNull(short999.syllablesPerSecond());
        SpeechAssessment.SpeechMetrics three = metrics("새 공", "새 공", 3000, List.of(new SpeechSegment(500, 1800)));
        assertEquals("UNAVAILABLE", three.speakingRate().status());
        assertEquals("MEASURED", three.pauses().status(), "멈춤은 짧은 문장에서도 잰다");
    }

    @Test
    void wordItemsVadMissingAndHeldRecordingsAreNotMeasured() {
        SpeechAssessment.SpeechMetrics word = metrics("라디오", "라디오", 1500, List.of(new SpeechSegment(500, 1000)));
        assertEquals("NOT_SUPPORTED", word.speakingRate().status());
        assertEquals("WORD_ITEM", word.speakingRate().reason());
        assertEquals("NOT_SUPPORTED", word.pauses().status());
        SpeechAssessment.SpeechMetrics noVad = metrics(SENTENCE, SENTENCE, 4000, null);
        assertEquals("UNAVAILABLE", noVad.speakingRate().status());
        assertEquals("VAD_UNAVAILABLE", noVad.pauses().reason());
        assertNull(noVad.pauses().pauseCount());
        // 말소리가 녹음 끝까지 이어짐(잘린 녹음) → 판정 보류 → 측정하지 않음
        SpeechAssessment held = evaluator.evaluate(SENTENCE, SENTENCE, result(3550, PAUSED, 20000, 0), null, List.of());
        assertEquals("HOLD", held.assessmentStatus());
        assertEquals("UNAVAILABLE", held.metrics().speakingRate().status());
        assertEquals("ASSESSMENT_HOLD", held.metrics().pauses().reason());
    }

    @Test
    void pronunciationAccuracyIsNeverScoredFromTheTranscript() {
        for (String transcript : List.of(SENTENCE, "오늘은 날씨가 조아요", "오늘"))
            for (List<SpeechSegment> segments : java.util.Arrays.asList(PAUSED, null)) {
                SpeechAssessment.PronunciationMetric p = evaluator.evaluate(SENTENCE, transcript, result(4000, segments, 20000, 0), null, List.of())
                        .metrics().pronunciation();
                assertEquals("NOT_SUPPORTED", p.status());
                assertEquals("NO_PRONUNCIATION_MODEL", p.reason());
            }
    }
}
