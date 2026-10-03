package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment.PhonemeCandidate;
import com.example.backend.chld.dto.response.SpeechAssessment.PhonemePosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HangulPhonemeAnalyzerTest {
    private final HangulPhonemeAnalyzer analyzer = new HangulPhonemeAnalyzer();

    @Test
    void findsTargetPhonemeOnsetAndCodaPositions() {
        List<PhonemePosition> radio = analyzer.targetPositions("라디오", List.of("ㄹ"));
        assertEquals(1, radio.size());
        assertEquals("ONSET", radio.get(0).slot());
        assertEquals("INITIAL", radio.get(0).wordPosition());

        List<PhonemePosition> fire = analyzer.targetPositions("불", List.of("ㄹ", "ㅂ"));
        assertEquals(List.of("ㅂ:ONSET:INITIAL", "ㄹ:CODA:FINAL"),
                fire.stream().map(p -> p.phoneme() + ":" + p.slot() + ":" + p.wordPosition()).toList());

        // 문장에서는 어절 번호와 어절 안 위치를 기록한다. 겹받침(ㄺ)의 ㄹ도 찾는다.
        List<PhonemePosition> sentence = analyzer.targetPositions("닭이 걸어가요", List.of("ㄹ"));
        assertEquals(List.of("1:1:CODA", "2:1:CODA"), sentence.stream().map(p -> p.wordIndex() + ":" + p.syllableIndex() + ":" + p.slot()).toList());
        // 소리 없는 초성 ㅇ은 목표 음소 위치로 보지 않는다.
        assertTrue(analyzer.targetPositions("오이", List.of("ㅇ")).isEmpty());
    }

    @Test
    void recordsSubstitutionOmissionAndAdditionCandidates() {
        List<PhonemeCandidate> onset = analyzer.candidates("라디오", "다디오", List.of("ㄹ"));
        assertEquals(1, onset.size());
        assertEquals("SUBSTITUTION", onset.get(0).type());
        assertEquals("ㄹ", onset.get(0).expected());
        assertEquals("ㄷ", onset.get(0).produced());
        assertTrue(onset.get(0).targetPhoneme());

        PhonemeCandidate coda = analyzer.candidates("불", "부", List.of("ㄹ")).get(0);
        assertEquals("OMISSION", coda.type());
        assertEquals("CODA", coda.slot());
        assertTrue(coda.targetPhoneme());

        PhonemeCandidate onsetDrop = analyzer.candidates("로봇", "오봇", List.of("ㄹ")).get(0);
        assertEquals("OMISSION", onsetDrop.type());
        assertEquals("ONSET", onsetDrop.slot());

        List<PhonemeCandidate> syllables = analyzer.candidates("바나나", "바나", List.of("ㅂ", "ㅍ"));
        assertEquals("SYLLABLE_OMISSION", syllables.get(0).type());
        assertEquals("SYLLABLE_ADDITION", analyzer.candidates("사과", "사과요", List.of()).get(0).type());
        assertTrue(analyzer.candidates("오늘은 날씨가 좋아요", "오늘은 날씨가 좋아요", List.of()).isEmpty());
    }

    @Test
    void ignoresNonHangulCharactersInAlignment() {
        assertEquals(4, analyzer.syllableCount("라디오 3개")); // 숫자 3은 빼고 라·디·오·개만 센다
        assertEquals(List.of("ㅂ", "ㅍ"), HangulPhonemeAnalyzer.parseTargetPhonemes("ㅂ, ㅍ"));
    }
}
