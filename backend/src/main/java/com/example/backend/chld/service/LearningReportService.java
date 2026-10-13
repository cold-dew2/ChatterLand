package com.example.backend.chld.service;

import java.util.Map;

/**
 * 누적 학습 리포트. 학생(본인)·선생님(담당 학생)·보호자(연결된 자녀) 화면이 같은 집계를 쓴다.
 * 접근 권한은 호출하는 쪽 서비스에서 먼저 확인한 뒤 학생 ID를 넘긴다.
 */
public interface LearningReportService {
    /** period: 7d(오늘 포함 7일)·30d(오늘 포함 30일)·all(전체). 잘못된 값이면 400 */
    Map<String,Object> report(long studentId, String period);
}
