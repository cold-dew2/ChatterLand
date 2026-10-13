package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 누적 학습 리포트 집계(학생·선생님·보호자 화면이 같은 쿼리를 쓴다).
 * 기간은 created_at 기준 [from 00:00, to 00:00) 이다. from이 null이면 처음부터(전체 기간).
 * 연습 기록(practice_attempts)은 완료된 음성 분석 하나당 한 행이라(analysis_id 고유) 같은 녹음이 두 번 세어지지 않고,
 * 녹음하지 않고 넘긴 문항은 행이 없어 세지 않는다.
 */
@Mapper
public interface LearningReportMapper {
    Map<String,Object> findTotals(@Param("studentId") long studentId, @Param("from") LocalDate from, @Param("to") LocalDate to);
    List<Map<String,Object>> findByCategory(@Param("studentId") long studentId, @Param("from") LocalDate from, @Param("to") LocalDate to);
    List<Map<String,Object>> findDaily(@Param("studentId") long studentId, @Param("from") LocalDate from, @Param("to") LocalDate to);
    List<Map<String,Object>> findRepeated(@Param("studentId") long studentId, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("limit") int limit);
    List<Map<String,Object>> findRecent(@Param("studentId") long studentId, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("limit") int limit);
    long countActiveExercises();
}
