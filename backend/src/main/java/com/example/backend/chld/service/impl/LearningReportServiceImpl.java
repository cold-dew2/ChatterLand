package com.example.backend.chld.service.impl;

import com.example.backend.chld.mapper.LearningReportMapper;
import com.example.backend.chld.service.LearningReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 저장된 연습 기록만 센다. 점수·진단·발음 정확도는 만들지 않는다(텍스트 일치율도 리포트에 넣지 않는다).
 * 이전 기간과의 비교는 이전 기간에 기록이 있을 때만 준다(없으면 comparison=null, 0으로 간주하지 않음).
 */
@Service
public class LearningReportServiceImpl implements LearningReportService {
    static final int REPEATED_LIMIT = 10;
    static final int RECENT_LIMIT = 10;

    private final LearningReportMapper reports;
    private final Clock clock;

    @Autowired
    public LearningReportServiceImpl(LearningReportMapper reports) { this(reports, Clock.systemDefaultZone()); }
    LearningReportServiceImpl(LearningReportMapper reports, Clock clock) { this.reports = reports; this.clock = clock; }

    /** 조회 기간. from이 null이면 전체 기간, to는 포함하지 않는 끝(내일 00:00) */
    record Range(String period, LocalDate from, LocalDate to) {
        /** 같은 길이의 바로 앞 기간(겹치거나 비는 날 없음). 전체 기간은 비교 대상이 없다 */
        Range previous() { return from == null ? null : new Range(period, from.minusDays(to.toEpochDay() - from.toEpochDay()), from); }
        LocalDate endDate() { return to.minusDays(1); }
    }

    static Range range(String period, LocalDate today) {
        String value = period == null || period.isBlank() ? "all" : period.trim().toLowerCase(Locale.ROOT);
        LocalDate to = today.plusDays(1);
        return switch (value) {
            case "7d" -> new Range(value, today.minusDays(6), to);
            case "30d" -> new Range(value, today.minusDays(29), to);
            case "all" -> new Range(value, null, to);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기간은 7d·30d·all 중 하나예요.");
        };
    }

    @Override
    public Map<String,Object> report(long studentId, String period) {
        Range range = range(period, LocalDate.now(clock));
        Map<String,Object> totals = totals(studentId, range);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("period", range.period());
        result.put("startDate", range.from() == null ? totals.get("firstPracticedOn") : range.from().toString());
        result.put("endDate", range.endDate().toString());
        result.putAll(totals);
        result.put("activeExercises", reports.countActiveExercises());
        result.put("categories", reports.findByCategory(studentId, range.from(), range.to()));
        result.put("daily", reports.findDaily(studentId, range.from(), range.to()));
        result.put("repeated", reports.findRepeated(studentId, range.from(), range.to(), REPEATED_LIMIT));
        result.put("recentAttempts", reports.findRecent(studentId, range.from(), range.to(), RECENT_LIMIT));
        result.put("comparison", comparison(studentId, range, totals));
        return result;
    }

    private Map<String,Object> totals(long studentId, Range range) {
        Map<String,Object> row = reports.findTotals(studentId, range.from(), range.to());
        return row == null ? new LinkedHashMap<>() : new LinkedHashMap<>(row);
    }

    private Map<String,Object> comparison(long studentId, Range range, Map<String,Object> current) {
        Range previous = range.previous();
        if (previous == null) return null;
        Map<String,Object> before = totals(studentId, previous);
        if (number(before.get("totalAttempts")) == 0) return null;
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("startDate", previous.from().toString());
        result.put("endDate", previous.endDate().toString());
        for (String key : new String[]{"totalAttempts", "practiceDays", "practicedExercises"}) {
            long now = number(current.get(key)), then = number(before.get(key));
            result.put(key, then);
            result.put(key + "Change", now - then);
        }
        return result;
    }

    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
}
