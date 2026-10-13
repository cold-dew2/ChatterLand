package com.example.backend.chld.service.impl;

import com.example.backend.chld.mapper.LearningReportMapper;
import com.example.backend.chld.mapper.RecommendationMapper;
import com.example.backend.chld.service.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천 규칙(순서대로, 같은 콘텐츠는 한 번만, 최대 {@value #LIMIT}개)
 * 1. 기록이 없으면: 영역마다 첫 기본 콘텐츠(STARTER). personalized=false — 개인화 분석 결과가 아니다.
 * 2. 최근 {@value #RECENT_DAYS}일 동안 반복 연습한 콘텐츠 최대 {@value #MAX_REVIEW}개(REPEATED, 다시 연습).
 * 3. 가장 최근에 연습한 영역에서 그다음 순서의 아직 연습하지 않은 콘텐츠(NEXT_IN_AREA).
 * 4. 연습 기록이 적은 영역 순으로 그 영역의 첫 미연습 콘텐츠 최대 2개(UNTRIED_AREA: 기록 0회 / FEW_ATTEMPTS_AREA).
 * 5. 남는 자리는 다른 영역의 미연습 콘텐츠(NEW_CONTENT).
 * 진행 중 숙제의 콘텐츠는 자율 연습 추천에서 빼고(숙제 기록과 섞이지 않게) 숙제 개수만 따로 알린다.
 */
@Service
public class RecommendationServiceImpl implements RecommendationService {
    static final int LIMIT = 5;
    static final int MAX_REVIEW = 2;
    static final int RECENT_DAYS = 14;

    private final RecommendationMapper recommendations;
    private final LearningReportMapper reports;
    private final Clock clock;

    @Autowired
    public RecommendationServiceImpl(RecommendationMapper recommendations, LearningReportMapper reports) { this(recommendations, reports, Clock.systemDefaultZone()); }
    RecommendationServiceImpl(RecommendationMapper recommendations, LearningReportMapper reports, Clock clock) {
        this.recommendations = recommendations; this.reports = reports; this.clock = clock;
    }

    record Area(String categoryId, String name, String color, long attempts) { }
    record Candidate(long exerciseId, String title, String categoryId) { }
    /** 규칙에 필요한 실제 기록 요약. firstUnpracticed: 영역별 아직 연습하지 않은 첫 콘텐츠(숙제 콘텐츠 제외) */
    record Snapshot(long totalAttempts, List<Area> areas, List<Candidate> repeated, String lastCategoryId, Candidate next,
                    Map<String,Candidate> firstUnpracticed, Set<Long> homeworkExerciseIds) { }
    record Recommendation(Candidate content, String reasonCode, String reason, String kind) { }

    @Override
    public Map<String,Object> recommend(long studentId) {
        Snapshot snapshot = snapshot(studentId);
        Map<String,Area> areas = new LinkedHashMap<>();
        snapshot.areas().forEach(area -> areas.put(area.categoryId(), area));
        List<Map<String,Object>> items = new ArrayList<>();
        for (Recommendation pick : pick(snapshot)) {
            Area area = areas.get(pick.content().categoryId());
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("exerciseId", pick.content().exerciseId());
            row.put("title", pick.content().title());
            row.put("categoryId", pick.content().categoryId());
            row.put("categoryName", area == null ? null : area.name());
            row.put("color", area == null ? null : area.color());
            row.put("kind", pick.kind());
            row.put("reasonCode", pick.reasonCode());
            row.put("reason", pick.reason());
            items.add(row);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("personalized", snapshot.totalAttempts() > 0);
        result.put("basis", snapshot.totalAttempts() > 0
                ? "저장된 연습 기록 " + snapshot.totalAttempts() + "회와 연습 영역을 바탕으로 골랐어요."
                : "아직 연습 기록이 없어 영역별 기본 콘텐츠를 보여 드려요. 연습하면 기록에 맞춰 추천이 바뀌어요.");
        result.put("items", items);
        result.put("pendingHomeworks", recommendations.countPendingHomeworks(studentId));
        return result;
    }

    private Snapshot snapshot(long studentId) {
        List<Area> areas = recommendations.findAreas(studentId).stream()
                .map(row -> new Area(String.valueOf(row.get("categoryId")), String.valueOf(row.get("name")),
                        row.get("color") == null ? null : String.valueOf(row.get("color")), number(row.get("attempts"))))
                .toList();
        long total = areas.stream().mapToLong(Area::attempts).sum();
        Set<Long> homework = new HashSet<>(recommendations.findPendingHomeworkExerciseIds(studentId));
        LocalDate today = LocalDate.now(clock);
        List<Candidate> repeated = reports.findRepeated(studentId, today.minusDays(RECENT_DAYS - 1), today.plusDays(1), LIMIT).stream()
                .map(RecommendationServiceImpl::candidate).toList();
        Map<String,Object> last = recommendations.findLastPracticed(studentId);
        String lastCategory = last == null ? null : String.valueOf(last.get("categoryId"));
        Candidate next = last == null ? null : candidate(recommendations.findFirstUnpracticed(studentId, lastCategory,
                (int) number(last.get("sortOrder")), number(last.get("exerciseId")), homework));
        Map<String,Candidate> first = new LinkedHashMap<>();
        for (Area area : areas) {
            Candidate candidate = candidate(recommendations.findFirstUnpracticed(studentId, area.categoryId(), null, null, homework));
            if (candidate != null) first.put(area.categoryId(), candidate);
        }
        return new Snapshot(total, areas, repeated, lastCategory, next, first, homework);
    }

    /** 추천 규칙(DB 없이 검증 가능한 순수 함수) */
    static List<Recommendation> pick(Snapshot s) {
        List<Recommendation> picks = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        Map<String,String> names = new LinkedHashMap<>();
        s.areas().forEach(area -> names.put(area.categoryId(), area.name()));
        if (s.totalAttempts() == 0) {
            for (Area area : s.areas()) {
                add(picks, used, s, s.firstUnpracticed().get(area.categoryId()), "STARTER", "처음 시작하기 좋은 " + area.name() + " 기본 콘텐츠예요", "NEW");
            }
            return picks;
        }
        int review = 0;
        for (Candidate candidate : s.repeated()) {
            if (review >= MAX_REVIEW) break;
            if (add(picks, used, s, candidate, "REPEATED", "최근 여러 번 연습한 콘텐츠예요", "REVIEW")) review++;
        }
        if (s.next() != null) {
            add(picks, used, s, s.next(), "NEXT_IN_AREA", "최근 연습한 " + names.getOrDefault(s.lastCategoryId(), "영역") + "의 다음 콘텐츠예요", "NEW");
        }
        List<Area> fewest = s.areas().stream().sorted(Comparator.comparingLong(Area::attempts)).toList(); // 같은 횟수는 영역 순서 유지
        int areaPicks = 0;
        for (Area area : fewest) {
            if (areaPicks >= 2) break;
            if (area.categoryId().equals(s.lastCategoryId())) continue;
            boolean untried = area.attempts() == 0;
            if (add(picks, used, s, s.firstUnpracticed().get(area.categoryId()), untried ? "UNTRIED_AREA" : "FEW_ATTEMPTS_AREA",
                    untried ? "아직 연습하지 않은 영역이에요" : "연습 기록이 적은 영역이에요", "NEW")) areaPicks++;
        }
        for (Area area : s.areas()) {
            add(picks, used, s, s.firstUnpracticed().get(area.categoryId()), "NEW_CONTENT", "아직 연습하지 않은 콘텐츠예요", "NEW");
        }
        return picks;
    }

    private static boolean add(List<Recommendation> picks, Set<Long> used, Snapshot s, Candidate candidate, String code, String reason, String kind) {
        if (candidate == null || picks.size() >= LIMIT || used.contains(candidate.exerciseId()) || s.homeworkExerciseIds().contains(candidate.exerciseId())) return false;
        used.add(candidate.exerciseId());
        picks.add(new Recommendation(candidate, code, reason, kind));
        return true;
    }

    private static Candidate candidate(Map<String,Object> row) {
        if (row == null) return null;
        return new Candidate(number(row.get("exerciseId")), String.valueOf(row.get("title")), String.valueOf(row.get("categoryId")));
    }

    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
}
