package com.example.backend.chld.service.impl;

import com.example.backend.chld.service.impl.HangulPhonemeAnalyzer.Syllable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 목표 문장의 글자(자모)만 보고 표준 발음법의 어떤 조항이 적용될 수 있는지 찾는다(RAG 검색 질의용).
 * 어절 안의 이웃한 음절만 본다. 품사·형태소는 분석하지 않으므로 '적용될 수 있음'이며, 학생의 실제 발음을 판정하지 않는다.
 */
final class PronunciationRuleDetector {
    /** 적용 가능 조항과 그 위치(음절 쌍) */
    record RuleMatch(int rule, String syllables) { }

    private static final Set<String> OBSTRUENT_CODA = Set.of("ㄱ","ㄲ","ㅋ","ㄳ","ㄺ","ㄷ","ㅅ","ㅆ","ㅈ","ㅊ","ㅌ","ㅂ","ㅍ","ㄼ","ㄿ","ㅄ");
    private static final Set<String> NEUTRALIZED_CODA = Set.of("ㄲ","ㅋ","ㅅ","ㅆ","ㅈ","ㅊ","ㅌ","ㅍ");
    private static final Set<String> TENSE_ONSET = Set.of("ㄱ","ㄷ","ㅂ","ㅅ","ㅈ");

    private PronunciationRuleDetector() { }

    static List<RuleMatch> detect(String normalizedTarget) {
        List<Syllable> syllables = HangulPhonemeAnalyzer.syllables(normalizedTarget);
        Map<String,RuleMatch> found = new LinkedHashMap<>();
        for (int i = 0; i < syllables.size(); i++) {
            Syllable s = syllables.get(i);
            String coda = s.coda();
            if (coda.isEmpty()) continue;
            Syllable next = i + 1 < syllables.size() && syllables.get(i + 1).wordIndex() == s.wordIndex() ? syllables.get(i + 1) : null;
            String pair = next == null ? String.valueOf(s.value()) : "" + s.value() + next.value();
            if (next == null || !"ㅇ".equals(next.onset())) {
                if (NEUTRALIZED_CODA.contains(coda)) add(found, 9, pair);
            }
            if (next == null) continue;
            String onset = next.onset();
            if ((coda.equals("ㄴ") && onset.equals("ㄹ")) || (coda.equals("ㄹ") && onset.equals("ㄴ"))) add(found, 20, pair);
            if ((coda.equals("ㅁ") || coda.equals("ㅇ")) && onset.equals("ㄹ")) add(found, 19, pair);
            if (OBSTRUENT_CODA.contains(coda) && (onset.equals("ㄴ") || onset.equals("ㅁ"))) add(found, 18, pair);
            if (OBSTRUENT_CODA.contains(coda) && TENSE_ONSET.contains(onset)) add(found, 23, pair);
            if ((coda.equals("ㄷ") || coda.equals("ㅌ")) && onset.equals("ㅇ") && next.nucleus().equals("ㅣ")) add(found, 17, pair);
            else if (onset.equals("ㅇ") && !coda.equals("ㅇ") && !coda.equals("ㅎ")) add(found, 13, pair);
        }
        return new ArrayList<>(found.values());
    }

    private static void add(Map<String,RuleMatch> found, int rule, String syllables) {
        found.putIfAbsent(rule + ":" + syllables, new RuleMatch(rule, syllables));
    }
}
