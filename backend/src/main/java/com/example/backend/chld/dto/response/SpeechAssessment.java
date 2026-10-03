package com.example.backend.chld.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 자동 분석 결과(교사 확정 전). 발음 점수가 아니라 다음 근거만 담는다.
 * - 목표 음소의 표기상 위치(한글 자모 기준)
 * - 음성 인식 텍스트와 목표 텍스트를 음절 단위로 맞춰 찾은 오류 "후보"
 * - VAD 말소리 구간으로 측정한 발화 시간·무음
 * - 같은 문항의 이전 발화와의 비교
 * 음성 인식 모델은 틀린 발음을 맞는 말로 고쳐 적거나 맞는 발음을 다르게 적을 수 있으므로, 후보는 확정 판정이 아니다.
 */
public record SpeechAssessment(
        String analysisType,
        String assessmentStatus,
        List<String> holdReasons,
        String basis,
        List<String> targetPhonemes,
        List<PhonemePosition> targetPositions,
        List<PhonemeCandidate> candidates,
        SpeechTiming timing,
        Repetition repetition
) {
    public static final String TYPE_WORD = "WORD";
    public static final String TYPE_SENTENCE = "SENTENCE";
    /** 오류 후보 없음(텍스트 기준). 발음이 정확하다는 뜻은 아니다. */
    public static final String STATUS_NO_CANDIDATES = "NO_CANDIDATES";
    public static final String STATUS_ERROR_CANDIDATES = "ERROR_CANDIDATES";
    /** 음성 품질·인식 결과가 불확실해 자동 판단을 보류한다. */
    public static final String STATUS_HOLD = "HOLD";
    public static final String BASIS = "ORTHOGRAPHIC_JAMO_FROM_ASR_TEXT";

    /** 목표 텍스트에서 목표 음소가 나타나는 위치. slot: ONSET(초성)·CODA(종성), wordPosition: INITIAL(어두)·MEDIAL(어중)·FINAL(어말). */
    public record PhonemePosition(String phoneme, int wordIndex, String word, int syllableIndex, String syllable,
                                  String slot, String wordPosition) { }

    /**
     * 오류 후보. type: SUBSTITUTION(대치)·OMISSION(생략)·ADDITION(첨가)·SYLLABLE_OMISSION·SYLLABLE_ADDITION.
     * slot: ONSET·NUCLEUS(중성)·CODA·SYLLABLE. targetPhoneme은 문항의 목표 음소와 관련된 후보인지 여부다.
     */
    public record PhonemeCandidate(String type, String slot, String expected, String produced, Integer wordIndex, String word,
                                   Integer syllableIndex, String targetSyllable, String recognizedSyllable,
                                   String wordPosition, boolean targetPhoneme) { }

    /** VAD 말소리 구간 기반 측정값(ms). source가 UNAVAILABLE이면 녹음 길이만 있다. */
    public record SpeechTiming(String source, long audioMs, Long speechMs, Long leadingSilenceMs, Long trailingSilenceMs,
                               Integer pauseCount, Long longestPauseMs, BigDecimal syllablesPerSecond) { }

    /** 같은 학생·같은 문항의 이전 완료 분석(최근 5건)과의 비교. recurringCandidates는 이번과 이전에 모두 나온 후보다. */
    public record Repetition(int previousAttempts, int sameTranscriptCount, List<String> recurringCandidates, List<PreviousAttempt> recent) { }

    public record PreviousAttempt(String createdAt, String transcript, BigDecimal textMatchRate, String assessmentStatus) { }
}
