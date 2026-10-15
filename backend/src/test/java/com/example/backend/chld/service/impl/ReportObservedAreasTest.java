package com.example.backend.chld.service.impl;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 리포트 '기록상 점수가 높은 영역 / 함께 살펴볼 영역': 기록이 부족하면 판단을 보류하고, 진단 표현을 쓰지 않는다. */
class ReportObservedAreasTest {
    private static Map<String,Object> area(String label, Integer score, long attempts) {
        Map<String,Object> row = new HashMap<>();
        row.put("label", label); row.put("score", score); row.put("attempts", attempts);
        return row;
    }

    @Test
    void withholdsJudgementWhenRecordsAreMissingOrTooFew() {
        String insufficient = TeacherServiceImpl.NOT_ENOUGH_RECORDS;
        assertEquals("기록이 부족해 아직 판단하기 어려워요", insufficient);
        assertArrayEquals(new String[]{insufficient, insufficient}, TeacherServiceImpl.observedAreas(List.of()));
        // 점수가 없는 영역(로컬 인식은 점수 없음)은 비교하지 않는다
        assertArrayEquals(new String[]{insufficient, insufficient},
                TeacherServiceImpl.observedAreas(List.of(area("발음", null, 30), area("어휘력", null, 30))));
        // 점수가 있어도 영역마다 5회 미만이면 비교하지 않는다(1~2건으로 영역을 단정하지 않음)
        assertArrayEquals(new String[]{insufficient, insufficient},
                TeacherServiceImpl.observedAreas(List.of(area("발음", 90, 4), area("어휘력", 40, 2), area("유창성", 70, 30))));
    }

    @Test
    void comparesOnlyAreasWithEnoughScoredRecords() {
        String[] observed = TeacherServiceImpl.observedAreas(List.of(
                area("발음", 82, 6), area("어휘력", 61, 12), area("유창성", 99, 1), area("표현력", null, 20)));
        assertArrayEquals(new String[]{"발음", "어휘력"}, observed, "기록이 1회뿐인 유창성은 비교에서 뺀다");
        assertArrayEquals(new String[]{TeacherServiceImpl.NO_CLEAR_DIFFERENCE, TeacherServiceImpl.NO_CLEAR_DIFFERENCE},
                TeacherServiceImpl.observedAreas(List.of(area("발음", 70, 5), area("어휘력", 70, 9))));
        for (String text : new String[]{TeacherServiceImpl.NOT_ENOUGH_RECORDS, TeacherServiceImpl.NO_CLEAR_DIFFERENCE})
            assertFalse(text.matches(".*(부족한 영역|취약|결함|문제|진단).*"), "진단·결함 표현을 쓰지 않는다: " + text);
    }
}
