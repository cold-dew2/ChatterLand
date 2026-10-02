package com.example.backend.chld.service.impl;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudentServiceImplTest {
    @Test
    void countsConsecutiveActivityThroughToday() {
        LocalDate today = LocalDate.now();
        assertEquals(3, StudentServiceImpl.activityStreak(List.of(today, today.minusDays(1), today.minusDays(2))));
    }

    @Test
    void countsFromYesterdayWhenThereIsNoActivityToday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertEquals(2, StudentServiceImpl.activityStreak(List.of(yesterday, yesterday.minusDays(1))));
    }

    @Test
    void breaksStreakAtFirstMissingDayAndReturnsZeroForOldActivity() {
        LocalDate today = LocalDate.now();
        assertEquals(1, StudentServiceImpl.activityStreak(List.of(today, today.minusDays(2))));
        assertEquals(0, StudentServiceImpl.activityStreak(List.of(today.minusDays(3))));
    }
}
