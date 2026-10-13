package com.example.backend.chld.service.impl;

import com.example.backend.chld.service.impl.RecommendationServiceImpl.Area;
import com.example.backend.chld.service.impl.RecommendationServiceImpl.Candidate;
import com.example.backend.chld.service.impl.RecommendationServiceImpl.Recommendation;
import com.example.backend.chld.service.impl.RecommendationServiceImpl.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 규칙형 추천과 리포트 기간 계산(DB 없이 규칙만 검증) */
class RecommendationRulesTest {
    private static final List<Area> AREAS = List.of(
            new Area("articulation", "발음", "#1", 0), new Area("vocabulary", "어휘력", "#2", 0), new Area("fluency", "유창성", "#3", 0),
            new Area("expression", "표현력", "#4", 0), new Area("comprehension", "이해력", "#5", 0));

    private static Map<String,Candidate> firsts() {
        Map<String,Candidate> map = new LinkedHashMap<>();
        long id = 100;
        for (Area area : AREAS) map.put(area.categoryId(), new Candidate(id++, area.name() + " 첫 콘텐츠", area.categoryId()));
        return map;
    }

    private static List<Area> withAttempts(long... attempts) {
        return java.util.stream.IntStream.range(0, AREAS.size())
                .mapToObj(i -> new Area(AREAS.get(i).categoryId(), AREAS.get(i).name(), AREAS.get(i).color(), attempts[i])).toList();
    }

    @Test
    void newStudentGetsStarterContentPerAreaAndIsNotPresentedAsPersonalized() {
        List<Recommendation> picks = RecommendationServiceImpl.pick(new Snapshot(0, AREAS, List.of(), null, null, firsts(), Set.of()));
        assertEquals(5, picks.size());
        assertTrue(picks.stream().allMatch(p -> p.reasonCode().equals("STARTER")));
        assertEquals("처음 시작하기 좋은 발음 기본 콘텐츠예요", picks.get(0).reason());
    }

    @Test
    void mixesAtMostTwoRepeatedContentsWithNextAndLessPracticedAreas() {
        List<Candidate> repeated = List.of(new Candidate(1, "반복1", "articulation"), new Candidate(2, "반복2", "articulation"), new Candidate(3, "반복3", "articulation"));
        Candidate next = new Candidate(50, "발음 다음", "articulation");
        List<Recommendation> picks = RecommendationServiceImpl.pick(new Snapshot(30, withAttempts(30, 0, 5, 0, 0), repeated, "articulation", next, firsts(), Set.of()));
        assertEquals(List.of("REPEATED", "REPEATED", "NEXT_IN_AREA", "UNTRIED_AREA", "UNTRIED_AREA"), picks.stream().map(Recommendation::reasonCode).toList());
        assertEquals(2, picks.stream().filter(p -> p.kind().equals("REVIEW")).count(), "다시 연습은 최대 2개, 나머지는 새 콘텐츠");
        assertEquals("최근 여러 번 연습한 콘텐츠예요", picks.get(0).reason());
        assertEquals("최근 연습한 발음의 다음 콘텐츠예요", picks.get(2).reason());
        // 기록 0회 영역은 '아직 연습하지 않은 영역'으로만 안내한다(부족·취약 같은 진단 표현 없음)
        assertEquals("vocabulary", picks.get(3).content().categoryId());
        assertEquals("아직 연습하지 않은 영역이에요", picks.get(3).reason());
        assertTrue(picks.stream().noneMatch(p -> p.reason().matches(".*(부족|취약|약해|틀렸|정확도|점수).*")));
    }

    @Test
    void areasWithFewAttemptsAreDescribedAsFewNotWeak() {
        List<Recommendation> picks = RecommendationServiceImpl.pick(new Snapshot(20, withAttempts(10, 2, 3, 4, 1), List.of(), "articulation", null, firsts(), Set.of()));
        assertEquals("FEW_ATTEMPTS_AREA", picks.get(0).reasonCode());
        assertEquals("comprehension", picks.get(0).content().categoryId(), "가장 적게 연습한 영역부터");
        assertEquals("연습 기록이 적은 영역이에요", picks.get(0).reason());
    }

    @Test
    void pendingHomeworkContentsAreLeftOutAndNothingIsRepeated() {
        Candidate homeworkContent = new Candidate(1, "숙제 콘텐츠", "articulation");
        Map<String,Candidate> firsts = firsts();
        firsts.put("vocabulary", new Candidate(1, "숙제 콘텐츠", "articulation"));
        List<Recommendation> picks = RecommendationServiceImpl.pick(new Snapshot(5, withAttempts(5, 0, 0, 0, 0), List.of(homeworkContent), "articulation",
                new Candidate(100, "발음 첫 콘텐츠", "articulation"), firsts, Set.of(1L)));
        assertTrue(picks.stream().noneMatch(p -> p.content().exerciseId() == 1L), "진행 중 숙제 콘텐츠는 자율 연습 추천에서 뺀다");
        assertEquals(picks.size(), picks.stream().map(p -> p.content().exerciseId()).distinct().count(), "같은 콘텐츠는 한 번만");
        assertTrue(picks.size() <= RecommendationServiceImpl.LIMIT);
    }

    @Test
    void reportRangesCoverTodayAndComparePreviousPeriodOfSameLength() {
        LocalDate today = LocalDate.of(2026, 10, 13);
        var week = LearningReportServiceImpl.range("7d", today);
        assertEquals(LocalDate.of(2026, 10, 7), week.from());
        assertEquals(LocalDate.of(2026, 10, 14), week.to(), "오늘 하루 전체 포함(끝은 다음 날 0시, 포함 안 함)");
        assertEquals(LocalDate.of(2026, 9, 30), week.previous().from());
        assertEquals(LocalDate.of(2026, 10, 7), week.previous().to(), "이전 기간은 겹치거나 비는 날이 없다");
        var month = LearningReportServiceImpl.range("30d", today);
        assertEquals(LocalDate.of(2026, 9, 14), month.from());
        var all = LearningReportServiceImpl.range(null, today);
        assertNull(all.from());
        assertNull(all.previous(), "전체 기간은 비교 대상이 없다");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> LearningReportServiceImpl.range("90d", today));
    }
}
