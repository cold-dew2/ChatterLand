package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.TranscriptComparison;
import com.example.backend.chld.dto.response.TranscriptComparison.DiffType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptComparatorTest {
    private final TranscriptComparator comparator = new TranscriptComparator();

    @Test
    void identicalSentenceIgnoringPunctuationAndSpacingMatchesFully() {
        TranscriptComparison result = comparator.compare("오늘은 날씨가 좋아요.", " 오늘은 날씨가 좋아요");
        assertEquals(0, new BigDecimal("100.00").compareTo(result.matchRate()));
        assertTrue(result.words().stream().allMatch(word -> word.type() == DiffType.MATCH));
    }

    @Test
    void reportsSubstitutedMissingAndInsertedWords() {
        TranscriptComparison result = comparator.compare("거북이가 친구를 만났어요", "거부기가 만났어요 정말");
        assertEquals(DiffType.SUBSTITUTED, result.words().get(0).type());
        assertEquals("거북이가", result.words().get(0).expected());
        assertEquals("거부기가", result.words().get(0).recognized());
        assertTrue(result.words().stream().anyMatch(word -> word.type() == DiffType.MISSING && "친구를".equals(word.expected())));
        assertTrue(result.words().stream().anyMatch(word -> word.type() == DiffType.INSERTED && "정말".equals(word.recognized())));
    }

    @Test
    void matchRateIsSyllableBasedAndNeverNegative() {
        // 목표 3음절 중 1음절이 다르면 1 - 1/3
        assertEquals(0, new BigDecimal("66.67").compareTo(comparator.compare("라디오", "라디우").matchRate()));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(comparator.compare("사과", "전혀 다른 아주 긴 문장입니다").matchRate()));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(comparator.compare("사과", "").matchRate()));
    }
}
