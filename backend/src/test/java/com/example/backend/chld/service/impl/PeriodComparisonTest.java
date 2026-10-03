package com.example.backend.chld.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 이전 기간 비교값 계산: 기록 없는 기간·숙제 0건(0으로 나누기)·반올림 처리 */
class PeriodComparisonTest {
    private static Map<String,Object> period(Object rate, long attempts, long homeworkTotal, long homeworkCompleted) {
        Map<String,Object> map = new HashMap<>();
        map.put("averageMatchRate", rate); map.put("totalAttempts", attempts);
        map.put("homeworkTotal", homeworkTotal); map.put("homeworkCompleted", homeworkCompleted);
        return map;
    }

    @Test
    void computesChangesWhenBothPeriodsHaveData() {
        Map<String,Object> result = TeacherServiceImpl.comparePeriods(period(new BigDecimal("83"), 4, 3, 2), period(new BigDecimal("70"), 1, 4, 1));
        assertEquals(13, result.get("averageMatchRateChange"));
        assertEquals(3L, result.get("totalAttemptsChange"));
        assertEquals(67, result.get("homeworkCompletionRate"));
        assertEquals(25, result.get("previousHomeworkCompletionRate"));
        assertEquals(42, result.get("homeworkCompletionRateChange"));
    }

    @Test
    void emptyPeriodsGiveNullInsteadOfZeroOrDivisionByZero() {
        Map<String,Object> result = TeacherServiceImpl.comparePeriods(period(new BigDecimal("90"), 2, 0, 0), period(null, 0, 0, 0));
        assertNull(result.get("averageMatchRateChange"), "이전 기간에 기록이 없으면 변화량을 만들지 않는다");
        assertEquals(2L, result.get("totalAttemptsChange"));
        assertNull(result.get("homeworkCompletionRate"));
        assertNull(result.get("previousHomeworkCompletionRate"));
        assertNull(result.get("homeworkCompletionRateChange"));
        Map<String,Object> bothEmpty = TeacherServiceImpl.comparePeriods(period(null, 0, 0, 0), period(null, 0, 0, 0));
        assertNull(bothEmpty.get("averageMatchRateChange"));
        assertEquals(0L, bothEmpty.get("totalAttemptsChange"));
    }

    @Test
    void zeroCompletedIsAZeroPercentRateNotMissing() {
        Map<String,Object> result = TeacherServiceImpl.comparePeriods(period(null, 0, 2, 0), period(null, 0, 1, 1));
        assertEquals(0, result.get("homeworkCompletionRate"));
        assertEquals(-100, result.get("homeworkCompletionRateChange"));
    }
}
