package com.example.backend.chld.service;

/**
 * 아동 음성 파일 보관 정책(기본 6개월).
 * 분석 결과·인식 텍스트·선생님 검토 기록은 음성 파일과 분리되어 남는다.
 */
public interface AudioRetentionService {
    record PurgeResult(int deleted, int missing, int failed, int rejected, int orphansDeleted, int tempDirsDeleted) { }

    /** 보관 기간이 지난 음성 파일을 삭제한다. 중복 실행해도 안전하다. */
    PurgeResult purgeExpired();

    /** 음성 동의 철회 등으로 한 학생의 모든 보관 음성을 즉시 삭제한다. */
    PurgeResult deleteAllForStudent(long studentId);

    int retentionMonths();
}
