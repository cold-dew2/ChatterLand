package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.TranscriptComparison;
import com.example.backend.chld.dto.response.TranscriptComparison.DiffType;
import com.example.backend.chld.dto.response.TranscriptComparison.WordDiff;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 목표 문장과 음성 인식 문장을 비교한다.
 * - 일치도: 공백·문장부호를 제외한 음절 기준 편집 거리로 계산한 (1 - 음절 오류율) 값
 * - 단어 비교: 띄어쓰기 단위로 일치/누락 가능/추가/바뀜을 표시
 * 인식 결과 비교일 뿐이며 발음 정확도 평가가 아니다.
 */
@Component
public class TranscriptComparator {

    public TranscriptComparison compare(String targetText, String recognizedText) {
        String target = normalize(targetText);
        String recognized = normalize(recognizedText);
        String targetSyllables = target.replace(" ", "");
        String recognizedSyllables = recognized.replace(" ", "");
        BigDecimal matchRate = matchRate(targetSyllables, recognizedSyllables);
        List<WordDiff> words = diffWords(split(target), split(recognized));
        return new TranscriptComparison(targetText, recognizedText, matchRate, words);
    }

    static String normalize(String value) {
        if (value == null) return "";
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        return nfc.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    static BigDecimal matchRate(String target, String recognized) {
        if (target.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int distance = editDistance(target.codePoints().toArray(), recognized.codePoints().toArray());
        int targetLength = target.codePointCount(0, target.length());
        double rate = Math.max(0d, 1d - (double) distance / targetLength) * 100d;
        return BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private static int editDistance(int[] a, int[] b) {
        int[] previous = new int[b.length + 1];
        int[] current = new int[b.length + 1];
        for (int j = 0; j <= b.length; j++) previous[j] = j;
        for (int i = 1; i <= a.length; i++) {
            current[0] = i;
            for (int j = 1; j <= b.length; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.length];
    }

    private static List<String> split(String normalized) {
        return normalized.isEmpty() ? List.of() : Arrays.asList(normalized.split(" "));
    }

    static List<WordDiff> diffWords(List<String> target, List<String> recognized) {
        int n = target.size(), m = recognized.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = target.get(i - 1).equals(recognized.get(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        List<WordDiff> result = new ArrayList<>();
        int i = n, j = m;
        // 동점이면 일치 > 누락 > 추가 > 바뀜 순으로 골라 실제로 일치한 단어가 최대한 드러나게 한다.
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && target.get(i - 1).equals(recognized.get(j - 1)) && dp[i][j] == dp[i - 1][j - 1]) {
                result.add(new WordDiff(DiffType.MATCH, target.get(i - 1), recognized.get(j - 1)));
                i--; j--;
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                result.add(new WordDiff(DiffType.MISSING, target.get(i - 1), null));
                i--;
            } else if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) {
                result.add(new WordDiff(DiffType.INSERTED, null, recognized.get(j - 1)));
                j--;
            } else {
                result.add(new WordDiff(DiffType.SUBSTITUTED, target.get(i - 1), recognized.get(j - 1)));
                i--; j--;
            }
        }
        Collections.reverse(result);
        return result;
    }
}
