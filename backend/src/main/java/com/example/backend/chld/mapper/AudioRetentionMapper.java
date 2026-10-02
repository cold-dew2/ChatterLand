package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 아동 음성 파일 보관 기간 관리용 데이터 접근 */
@Mapper
public interface AudioRetentionMapper {
    List<Map<String,Object>> findExpiredAnalysisAudio(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);
    List<Map<String,Object>> findExpiredMessageAudio(@Param("retentionMonths") int retentionMonths, @Param("limit") int limit);
    List<Map<String,Object>> findStudentAnalysisAudio(@Param("studentId") long studentId);
    List<Map<String,Object>> findStudentMessageAudio(@Param("studentId") long studentId);
    int markAnalysisAudioDeleted(@Param("id") String id);
    int recordAnalysisDeleteFailure(@Param("id") String id, @Param("error") String error);
    int markMessageAudioDeleted(@Param("id") long id);
    long countAudioReferences(@Param("path") String path);
}
