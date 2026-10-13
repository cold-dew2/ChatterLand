package com.example.backend.chld.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationKeywordsTest {
    @Test
    void takesWordsTheChildActuallySaidFirstWithoutParticlesThenTopicWords() {
        List<String> words = ConversationKeywords.keywords("좋아하는 음식", List.of("저는 바나나를 좋아해요", "사과가 제일 맛있어요!"));
        assertEquals("사과", words.get(0), "최근에 한 말부터");
        assertTrue(words.contains("바나나"));
        assertFalse(words.contains("저는"));
        assertTrue(words.containsAll(List.of("과일", "음식")), "주제 기본 낱말은 뒤에 붙는다");
        assertTrue(words.indexOf("바나나") < words.indexOf("과일"));
    }

    @Test
    void ignoresFillerAndDifficultyWordsAndNonHangul() {
        List<String> words = ConversationKeywords.keywords("동물 이야기", List.of("몰라요 ㅠㅠ 어려워요 abc 123"));
        assertEquals(ConversationKeywords.TOPIC_WORDS.get("동물 이야기"), words);
        assertTrue(ConversationKeywords.fromTopic("동물 이야기", "강아지"));
    }

    @Test
    void stemsCommonEndingsButKeepsShortWords() {
        assertEquals("강아지", ConversationKeywords.stem("강아지랑"));
        assertEquals("학교", ConversationKeywords.stem("학교에서"));
        assertEquals("눈", ConversationKeywords.stem("눈"));
    }
}
