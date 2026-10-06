package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/** AI 학습 피드백 저장(분석 결과·교사 확정 결과와 다른 테이블) */
@Mapper
public interface SpeechFeedbackMapper {
    Map<String,Object> findFeedback(@Param("analysisId") String analysisId);
    int upsertFeedback(@Param("analysisId") String analysisId, @Param("text") String text, @Param("evidenceHash") String evidenceHash,
                       @Param("modelName") String modelName, @Param("promptVersion") String promptVersion,
                       @Param("sourcesJson") String sourcesJson);
}
