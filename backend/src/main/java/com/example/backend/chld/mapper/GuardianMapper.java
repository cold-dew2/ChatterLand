package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 보호자↔자녀(학생) 계정 연결. 조회 권한은 항상 (보호자 ID, 학생 ID, ACTIVE) 조건으로 확인한다. */
@Mapper
public interface GuardianMapper {
    // 연결 코드(자녀 계정에서 발급)
    int expireOpenCodes(@Param("studentId") long studentId);
    int insertLinkCode(@Param("studentId") long studentId, @Param("codeHash") String codeHash, @Param("minutes") int minutes);
    /** 방금 만든 코드의 만료 시각(DB 시계 기준) */
    String findCodeExpiry(@Param("codeHash") String codeHash);
    /** 아직 쓰지 않았고 만료되지 않은 코드의 학생 ID(행 잠금). 없으면 null */
    Long findStudentByOpenCode(@Param("codeHash") String codeHash);
    int markCodeUsed(@Param("codeHash") String codeHash, @Param("guardianId") long guardianId);

    // 잘못된 코드 입력 제한
    int insertLinkFailure(@Param("guardianId") long guardianId);
    long countRecentLinkFailures(@Param("guardianId") long guardianId, @Param("minutes") int minutes);

    // 연결
    /** 새로 연결하거나, 해제했던 연결을 다시 ACTIVE로 */
    int upsertLink(@Param("guardianId") long guardianId, @Param("studentId") long studentId);
    int unlink(@Param("guardianId") long guardianId, @Param("studentId") long studentId, @Param("by") String by);
    long countActiveChildren(@Param("guardianId") long guardianId);
    /** 연결된(ACTIVE) 자녀 한 명. 연결이 없거나 해제됐으면 null */
    Map<String,Object> findActiveChild(@Param("guardianId") long guardianId, @Param("studentId") long studentId);
    List<Map<String,Object>> findActiveChildren(@Param("guardianId") long guardianId);
    /** 자녀 계정 기준: 연결된 보호자 목록(이름·연결일만) */
    List<Map<String,Object>> findActiveGuardians(@Param("studentId") long studentId);
    /** 자녀 계정의 동의 상태(종류·동의 여부·시각만. 보호자 이름 등은 내보내지 않는다) */
    List<Map<String,Object>> findChildConsents(@Param("studentId") long studentId);
}
