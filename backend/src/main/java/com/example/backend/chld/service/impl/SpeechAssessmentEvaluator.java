package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment;
import com.example.backend.chld.dto.response.SpeechAssessment.PhonemeCandidate;
import com.example.backend.chld.dto.response.SpeechAssessment.PreviousAttempt;
import com.example.backend.chld.dto.response.SpeechAssessment.Repetition;
import com.example.backend.chld.dto.response.SpeechAssessment.SpeechTiming;
import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import com.example.backend.chld.dto.response.SpeechRecognitionResult.SpeechSegment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 인식 결과·녹음 측정값으로 자동 분석 결과(SpeechAssessment)를 만든다.
 * 녹음 품질이나 인식 결과가 불확실하면 판정 보류(HOLD)로 두고 이유를 남긴다. 발음 점수는 만들지 않는다.
 */
@Component
public class SpeechAssessmentEvaluator {
    static final String VERSION = "jamo-align-v1";
    /** 말소리가 녹음 끝(또는 시작)에 이만큼 가까이 붙어 있으면 잘린 녹음으로 본다(VAD 구간 여유 30ms 포함). */
    static final long CUT_OFF_END_MS = 100;
    static final long CUT_OFF_START_MS = 20;
    static final long MIN_SPEECH_MS = 200;
    static final int LOW_VOLUME_PEAK = 1500;
    static final double CLIPPING_RATIO = 0.005;

    /** 같은 문항의 이전 분석(반복 비교용) */
    public record PreviousAnalysis(String createdAt, String transcript, BigDecimal textMatchRate, String assessmentStatus, List<String> candidateSignatures) { }

    private final HangulPhonemeAnalyzer phonemes;

    public SpeechAssessmentEvaluator(HangulPhonemeAnalyzer phonemes) { this.phonemes = phonemes; }

    public SpeechAssessment evaluate(String targetText, String transcript, SpeechRecognitionResult recognition,
                                     String targetPhonemeSetting, List<PreviousAnalysis> previous) {
        String target = TranscriptComparator.normalize(targetText);
        String recognized = TranscriptComparator.normalize(transcript);
        String type = target.isEmpty() || target.split(" ").length <= 1 ? SpeechAssessment.TYPE_WORD : SpeechAssessment.TYPE_SENTENCE;
        List<String> targetPhonemes = HangulPhonemeAnalyzer.parseTargetPhonemes(targetPhonemeSetting);
        List<PhonemeCandidate> candidates = phonemes.candidates(target, recognized, targetPhonemes);
        int recognizedSyllables = phonemes.syllableCount(recognized);
        SpeechTiming timing = timing(recognition, recognizedSyllables);
        List<String> holdReasons = holdReasons(recognition, timing, recognized, phonemes.syllableCount(target), recognizedSyllables);
        String status = !holdReasons.isEmpty() ? SpeechAssessment.STATUS_HOLD
                : candidates.isEmpty() ? SpeechAssessment.STATUS_NO_CANDIDATES : SpeechAssessment.STATUS_ERROR_CANDIDATES;
        return new SpeechAssessment(type, status, holdReasons, SpeechAssessment.BASIS, targetPhonemes,
                phonemes.targetPositions(target, targetPhonemes), candidates, timing, repetition(recognized, candidates, previous));
    }

    static SpeechTiming timing(SpeechRecognitionResult recognition, int recognizedSyllables) {
        long audioMs = recognition.audioDurationMs();
        List<SpeechSegment> segments = recognition.speechSegments();
        if (segments == null || segments.isEmpty())
            return new SpeechTiming("UNAVAILABLE", audioMs, null, null, null, null, null, null);
        long speechMs = 0, longestPause = 0;
        int pauses = 0;
        for (int i = 0; i < segments.size(); i++) {
            SpeechSegment segment = segments.get(i);
            speechMs += segment.endMs() - segment.startMs();
            if (i > 0) {
                long gap = segment.startMs() - segments.get(i - 1).endMs();
                if (gap > 0) { pauses++; longestPause = Math.max(longestPause, gap); }
            }
        }
        BigDecimal rate = speechMs > 0 && recognizedSyllables > 0
                ? BigDecimal.valueOf(recognizedSyllables * 1000.0 / speechMs).setScale(2, RoundingMode.HALF_UP) : null;
        return new SpeechTiming("VAD", audioMs, speechMs, segments.get(0).startMs(),
                audioMs - segments.get(segments.size() - 1).endMs(), pauses, longestPause, rate);
    }

    static List<String> holdReasons(SpeechRecognitionResult recognition, SpeechTiming timing, String recognized,
                                    int targetSyllables, int recognizedSyllables) {
        List<String> reasons = new ArrayList<>();
        if (timing.speechMs() != null) {
            if (timing.trailingSilenceMs() <= CUT_OFF_END_MS) reasons.add("SPEECH_CUT_OFF_END");
            if (timing.leadingSilenceMs() <= CUT_OFF_START_MS) reasons.add("SPEECH_CUT_OFF_START");
            if (timing.speechMs() < MIN_SPEECH_MS) reasons.add("SPEECH_TOO_SHORT");
        }
        if (recognition.clippedRatio() > CLIPPING_RATIO) reasons.add("CLIPPING");
        if (recognition.peakAmplitude() > 0 && recognition.peakAmplitude() < LOW_VOLUME_PEAK) reasons.add("LOW_VOLUME");
        if (recognized.codePoints().anyMatch(cp -> Character.isLetterOrDigit(cp) && !HangulPhonemeAnalyzer.isHangulSyllable(cp)))
            reasons.add("NON_HANGUL_TRANSCRIPT");
        // 목표보다 훨씬 길거나 짧게 인식되면 반복 발화·다른 말·일부만 녹음된 경우일 수 있다.
        boolean tooLong = recognizedSyllables > targetSyllables * 2 + 1;
        boolean tooShort = targetSyllables >= 2 && recognizedSyllables * 2 < targetSyllables;
        if (targetSyllables > 0 && (tooLong || tooShort)) reasons.add("LENGTH_MISMATCH");
        return reasons;
    }

    static Repetition repetition(String recognized, List<PhonemeCandidate> candidates, List<PreviousAnalysis> previous) {
        if (previous == null || previous.isEmpty()) return new Repetition(0, 0, List.of(), List.of());
        Set<String> current = new LinkedHashSet<>();
        candidates.forEach(c -> current.add(HangulPhonemeAnalyzer.signature(c)));
        Set<String> recurring = new LinkedHashSet<>();
        int same = 0;
        List<PreviousAttempt> recent = new ArrayList<>();
        for (PreviousAnalysis p : previous) {
            if (recognized.equals(TranscriptComparator.normalize(p.transcript()))) same++;
            for (String signature : p.candidateSignatures()) if (current.contains(signature)) recurring.add(signature);
            recent.add(new PreviousAttempt(p.createdAt(), p.transcript(), p.textMatchRate(), p.assessmentStatus()));
        }
        return new Repetition(previous.size(), same, List.copyOf(recurring), recent);
    }
}
