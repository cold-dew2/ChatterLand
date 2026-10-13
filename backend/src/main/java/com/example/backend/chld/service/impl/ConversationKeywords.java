package com.example.backend.chld.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 대화에서 연습 콘텐츠를 찾을 낱말 고르기(AI 없이, 실제 대화 글자만 사용).
 * 학생이 말한 한글 낱말에서 흔한 조사·어미를 떼고 2글자 이상만 쓴다. 그다음 대화 주제의 기본 낱말을 붙인다.
 */
final class ConversationKeywords {
    static final int MAX_KEYWORDS = 8;

    /** 주제별 기본 낱말(학생이 말한 낱말이 콘텐츠와 맞지 않을 때 주제로 연결) */
    static final Map<String, List<String>> TOPIC_WORDS = Map.of(
            "학교 이야기", List.of("학교", "친구", "선생님", "교실", "가방"),
            "좋아하는 음식", List.of("사과", "바나나", "과일", "음식", "우유"),
            "동물 이야기", List.of("강아지", "고양이", "토끼", "동물", "사자"),
            "오늘의 기분", List.of("기분", "행복", "웃어요", "놀이", "친구"));

    // 길이가 긴 것부터 떼어 낸다
    private static final List<String> ENDINGS = List.of("이에요", "예요", "에서", "이랑", "한테", "까지", "부터", "처럼", "보다", "에게",
            "해요", "어요", "아요", "이요", "은", "는", "이", "가", "을", "를", "에", "도", "와", "과", "랑", "요", "의", "로", "고");
    private static final Set<String> STOP_WORDS = Set.of("그리고", "그래서", "근데", "그냥", "진짜", "정말", "너무", "많이", "오늘", "어제", "내일",
            "제가", "저는", "나는", "우리", "모르겠어", "모르겠어요", "몰라요", "몰라", "어려워", "어려워요", "좋아", "좋아요", "있어", "없어", "했어", "했어요",
            "같아", "같아요", "그거", "이거", "저거", "무슨", "뜻이", "뭐예", "뭐야");

    private ConversationKeywords() { }

    static List<String> keywords(String topic, List<String> studentTexts) {
        Set<String> words = new LinkedHashSet<>();
        for (int i = studentTexts.size() - 1; i >= 0 && words.size() < MAX_KEYWORDS; i--) { // 최근에 말한 것부터
            for (String raw : studentTexts.get(i).split("[^가-힣]+")) {
                String word = stem(raw);
                if (word.length() >= 2 && !STOP_WORDS.contains(word) && !STOP_WORDS.contains(raw)) words.add(word);
                if (words.size() >= MAX_KEYWORDS) break;
            }
        }
        List<String> result = new ArrayList<>(words);
        for (String seed : TOPIC_WORDS.getOrDefault(topic, List.of())) if (!result.contains(seed)) result.add(seed);
        return result;
    }

    static String stem(String raw) {
        String word = raw;
        for (String ending : ENDINGS) {
            if (word.length() > ending.length() + 1 && word.endsWith(ending)) { word = word.substring(0, word.length() - ending.length()); break; }
        }
        return word;
    }

    static boolean fromTopic(String topic, String keyword) { return TOPIC_WORDS.getOrDefault(topic, List.of()).contains(keyword); }
}
