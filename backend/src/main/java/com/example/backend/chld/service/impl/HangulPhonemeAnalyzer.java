package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment.PhonemeCandidate;
import com.example.backend.chld.dto.response.SpeechAssessment.PhonemePosition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 한글 음절을 초성·중성·종성 자모로 나눠 목표 음소 위치와 오류 후보를 찾는다.
 * 표기(맞춤법) 기준이며 실제 발음 규칙(연음·경음화·비음화 등)은 반영하지 않는다.
 * 비교 대상은 음성 인식 모델이 적은 텍스트이므로 결과는 "후보"일 뿐 발음 판정이 아니다.
 */
@Component
public class HangulPhonemeAnalyzer {
    static final int MAX_CANDIDATES = 30;
    private static final String[] ONSETS = {"ㄱ","ㄲ","ㄴ","ㄷ","ㄸ","ㄹ","ㅁ","ㅂ","ㅃ","ㅅ","ㅆ","ㅇ","ㅈ","ㅉ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ"};
    private static final String[] NUCLEI = {"ㅏ","ㅐ","ㅑ","ㅒ","ㅓ","ㅔ","ㅕ","ㅖ","ㅗ","ㅘ","ㅙ","ㅚ","ㅛ","ㅜ","ㅝ","ㅞ","ㅟ","ㅠ","ㅡ","ㅢ","ㅣ"};
    private static final String[] CODAS = {"","ㄱ","ㄲ","ㄳ","ㄴ","ㄵ","ㄶ","ㄷ","ㄹ","ㄺ","ㄻ","ㄼ","ㄽ","ㄾ","ㄿ","ㅀ","ㅁ","ㅂ","ㅄ","ㅅ","ㅆ","ㅇ","ㅈ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ"};
    /** 겹받침을 구성 자음으로 나눈다(목표 음소 ㄹ은 ㄺ·ㄻ 등에도 들어 있다). */
    private static final String[][] CODA_PARTS = {{},{"ㄱ"},{"ㄲ"},{"ㄱ","ㅅ"},{"ㄴ"},{"ㄴ","ㅈ"},{"ㄴ","ㅎ"},{"ㄷ"},{"ㄹ"},{"ㄹ","ㄱ"},{"ㄹ","ㅁ"},
            {"ㄹ","ㅂ"},{"ㄹ","ㅅ"},{"ㄹ","ㅌ"},{"ㄹ","ㅍ"},{"ㄹ","ㅎ"},{"ㅁ"},{"ㅂ"},{"ㅂ","ㅅ"},{"ㅅ"},{"ㅆ"},{"ㅇ"},{"ㅈ"},{"ㅊ"},{"ㅋ"},{"ㅌ"},{"ㅍ"},{"ㅎ"}};

    /** 목표 텍스트의 한 음절과 그 위치 */
    record Syllable(char value, String onset, String nucleus, String coda, int codaIndex, int wordIndex, String word,
                    int syllableIndex, int syllableCount) {
        String wordPosition(String slot) {
            if ("ONSET".equals(slot) && syllableIndex == 1) return "INITIAL";
            if (("CODA".equals(slot) || "NUCLEUS".equals(slot)) && syllableIndex == syllableCount) return "FINAL";
            if ("SYLLABLE".equals(slot)) return syllableIndex == 1 ? "INITIAL" : syllableIndex == syllableCount ? "FINAL" : "MEDIAL";
            return "MEDIAL";
        }
    }

    static boolean isHangulSyllable(int codePoint) { return codePoint >= 0xAC00 && codePoint <= 0xD7A3; }

    /** 공백 기준 어절별로 한글 음절만 추린다(숫자·영문은 비교에서 제외). */
    static List<Syllable> syllables(String normalizedText) {
        List<Syllable> result = new ArrayList<>();
        if (normalizedText == null || normalizedText.isBlank()) return result;
        String[] words = normalizedText.trim().split("\\s+");
        int wordIndex = 0;
        for (String word : words) {
            int[] hangul = word.codePoints().filter(HangulPhonemeAnalyzer::isHangulSyllable).toArray();
            if (hangul.length == 0) continue;
            wordIndex++;
            for (int i = 0; i < hangul.length; i++) {
                int index = hangul[i] - 0xAC00;
                int coda = index % 28;
                result.add(new Syllable((char) hangul[i], ONSETS[index / 588], NUCLEI[(index % 588) / 28], CODAS[coda], coda,
                        wordIndex, word, i + 1, hangul.length));
            }
        }
        return result;
    }

    /** "ㄹ", "ㅂ,ㅍ" 형식의 목표 음소 설정을 자모 목록으로 바꾼다. */
    static List<String> parseTargetPhonemes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,\\s/]+")).map(String::trim).filter(p -> !p.isEmpty()).distinct().toList();
    }

    /** 목표 음소가 목표 텍스트의 어느 음절·자리(초성/종성)에 나오는지 찾는다. 소리 없는 초성 ㅇ은 제외한다. */
    public List<PhonemePosition> targetPositions(String normalizedTarget, Collection<String> targetPhonemes) {
        if (targetPhonemes.isEmpty()) return List.of();
        List<PhonemePosition> positions = new ArrayList<>();
        for (Syllable s : syllables(normalizedTarget)) {
            if (!"ㅇ".equals(s.onset()) && targetPhonemes.contains(s.onset()))
                positions.add(new PhonemePosition(s.onset(), s.wordIndex(), s.word(), s.syllableIndex(), String.valueOf(s.value()), "ONSET", s.wordPosition("ONSET")));
            if (targetPhonemes.contains(s.nucleus()))
                positions.add(new PhonemePosition(s.nucleus(), s.wordIndex(), s.word(), s.syllableIndex(), String.valueOf(s.value()), "NUCLEUS", s.wordPosition("NUCLEUS")));
            for (String part : CODA_PARTS[s.codaIndex()]) {
                if (targetPhonemes.contains(part))
                    positions.add(new PhonemePosition(part, s.wordIndex(), s.word(), s.syllableIndex(), String.valueOf(s.value()), "CODA", s.wordPosition("CODA")));
            }
        }
        return positions;
    }

    /** 목표·인식 음절을 자모 차이를 비용으로 맞춘 뒤(편집 거리), 자리별 차이를 오류 후보로 기록한다. */
    public List<PhonemeCandidate> candidates(String normalizedTarget, String normalizedRecognized, Collection<String> targetPhonemes) {
        List<Syllable> target = syllables(normalizedTarget);
        List<Syllable> recognized = syllables(normalizedRecognized);
        int n = target.size(), m = recognized.size();
        double[][] cost = new double[n + 1][m + 1];
        for (int i = 0; i <= n; i++) cost[i][0] = i;
        for (int j = 0; j <= m; j++) cost[0][j] = j;
        for (int i = 1; i <= n; i++)
            for (int j = 1; j <= m; j++)
                cost[i][j] = Math.min(Math.min(cost[i - 1][j] + 1, cost[i][j - 1] + 1), cost[i - 1][j - 1] + substitution(target.get(i - 1), recognized.get(j - 1)));

        List<PhonemeCandidate> result = new ArrayList<>();
        int i = n, j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && Math.abs(cost[i][j] - (cost[i - 1][j - 1] + substitution(target.get(i - 1), recognized.get(j - 1)))) < 1e-9) {
                compareSyllable(target.get(i - 1), recognized.get(j - 1), targetPhonemes, result);
                i--; j--;
            } else if (i > 0 && Math.abs(cost[i][j] - (cost[i - 1][j] + 1)) < 1e-9) {
                Syllable t = target.get(i - 1);
                result.add(new PhonemeCandidate("SYLLABLE_OMISSION", "SYLLABLE", String.valueOf(t.value()), null, t.wordIndex(), t.word(),
                        t.syllableIndex(), String.valueOf(t.value()), null, t.wordPosition("SYLLABLE"), containsTarget(t, targetPhonemes)));
                i--;
            } else {
                Syllable r = recognized.get(j - 1);
                result.add(new PhonemeCandidate("SYLLABLE_ADDITION", "SYLLABLE", null, String.valueOf(r.value()), null, null,
                        null, null, String.valueOf(r.value()), null, false));
                j--;
            }
        }
        Collections.reverse(result);
        return result.size() > MAX_CANDIDATES ? new ArrayList<>(result.subList(0, MAX_CANDIDATES)) : result;
    }

    /** 한글 음절 수(숫자·영문 제외) */
    public int syllableCount(String normalizedText) { return syllables(normalizedText).size(); }

    private static double substitution(Syllable a, Syllable b) {
        if (a.value() == b.value()) return 0;
        int diff = (a.onset().equals(b.onset()) ? 0 : 1) + (a.nucleus().equals(b.nucleus()) ? 0 : 1) + (a.coda().equals(b.coda()) ? 0 : 1);
        return diff / 3.0 + 0.01;
    }

    private static void compareSyllable(Syllable t, Syllable r, Collection<String> targets, List<PhonemeCandidate> out) {
        if (t.value() == r.value()) return;
        String ts = String.valueOf(t.value()), rs = String.valueOf(r.value());
        if (!t.onset().equals(r.onset())) {
            // 초성 ㅇ은 소리가 없으므로 ㅇ↔자음 차이는 생략/첨가 후보로 기록한다.
            String type = "ㅇ".equals(r.onset()) ? "OMISSION" : "ㅇ".equals(t.onset()) ? "ADDITION" : "SUBSTITUTION";
            String expected = "ㅇ".equals(t.onset()) ? null : t.onset();
            String produced = "ㅇ".equals(r.onset()) ? null : r.onset();
            out.add(new PhonemeCandidate(type, "ONSET", expected, produced, t.wordIndex(), t.word(), t.syllableIndex(), ts, rs,
                    t.wordPosition("ONSET"), expected != null && targets.contains(expected)));
        }
        if (!t.nucleus().equals(r.nucleus()))
            out.add(new PhonemeCandidate("SUBSTITUTION", "NUCLEUS", t.nucleus(), r.nucleus(), t.wordIndex(), t.word(), t.syllableIndex(), ts, rs,
                    t.wordPosition("NUCLEUS"), targets.contains(t.nucleus())));
        if (!t.coda().equals(r.coda())) {
            String type = r.coda().isEmpty() ? "OMISSION" : t.coda().isEmpty() ? "ADDITION" : "SUBSTITUTION";
            boolean related = Arrays.stream(CODA_PARTS[t.codaIndex()]).anyMatch(targets::contains);
            out.add(new PhonemeCandidate(type, "CODA", t.coda().isEmpty() ? null : t.coda(), r.coda().isEmpty() ? null : r.coda(),
                    t.wordIndex(), t.word(), t.syllableIndex(), ts, rs, t.wordPosition("CODA"), related));
        }
    }

    private static boolean containsTarget(Syllable s, Collection<String> targets) {
        if (!"ㅇ".equals(s.onset()) && targets.contains(s.onset())) return true;
        if (targets.contains(s.nucleus())) return true;
        return Arrays.stream(CODA_PARTS[s.codaIndex()]).anyMatch(targets::contains);
    }

    /** 반복 비교에 쓰는 후보 식별 문자열(예: "SUBSTITUTION:ONSET:ㄹ>ㄷ") */
    static String signature(PhonemeCandidate c) {
        return c.type() + ":" + c.slot() + ":" + Objects.toString(c.expected(), "-") + ">" + Objects.toString(c.produced(), "-");
    }
}
