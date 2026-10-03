package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentMapper {
    Map<String,Object> findStudentByUserId(@Param("userId") long userId);
    List<LocalDate> findActivityDates(@Param("studentId") long studentId);
    List<Map<String,Object>> findSessions(@Param("studentId") long studentId, @Param("status") String status,
                                          @Param("limit") int limit, @Param("offset") int offset);
    long countSessions(@Param("studentId") long studentId, @Param("status") String status);
    List<Map<String,Object>> findHomeworks(@Param("studentId") long studentId, @Param("limit") int limit, @Param("offset") int offset);
    long countHomeworks(@Param("studentId") long studentId);
    int completeHomework(@Param("studentId") long studentId, @Param("homeworkId") long homeworkId);
    Map<String,Object> findHomework(@Param("studentId") long studentId, @Param("homeworkId") long homeworkId);
    Map<String,Object> findSession(@Param("studentId") long studentId, @Param("sessionId") long sessionId);
    List<Map<String,Object>> findSessionExercises(@Param("sessionId") long sessionId);
    List<Map<String,Object>> findCategories();
    List<Map<String,Object>> findExercises(@Param("categoryId") String categoryId, @Param("limit") int limit, @Param("offset") int offset);
    long countExercises(@Param("categoryId") String categoryId);
    Map<String,Object> findExercise(@Param("exerciseId") long exerciseId);
    List<Map<String,Object>> findExerciseItems(@Param("exerciseId") long exerciseId);
    Map<String,Object> findExerciseItem(@Param("exerciseId") long exerciseId, @Param("itemId") long itemId);
    int insertAttempt(@Param("studentId") long studentId, @Param("exerciseId") long exerciseId,
                      @Param("itemId") String itemId, @Param("analysisId") String analysisId,
                      @Param("score") BigDecimal score, @Param("matchRate") BigDecimal matchRate, @Param("type") String type);
    List<Map<String,Object>> findHistory(@Param("studentId") long studentId, @Param("type") String type,
                                         @Param("limit") int limit, @Param("offset") int offset);
    long countHistory(@Param("studentId") long studentId, @Param("type") String type);
    int insertAnalysis(@Param("id") String id, @Param("studentId") long studentId, @Param("exerciseId") long exerciseId,
                       @Param("itemId") String itemId, @Param("audioPath") String audioPath, @Param("mime") String mime,
                       @Param("evaluationMode") String evaluationMode, @Param("targetText") String targetText,
                       @Param("engineName") String engineName, @Param("modelName") String modelName,
                       @Param("retentionMonths") int retentionMonths, @Param("requestKey") String requestKey,
                       @Param("requestHash") String requestHash);
    Map<String,Object> findAnalysisByRequestKey(@Param("studentId") long studentId, @Param("requestKey") String requestKey);
    List<Map<String,Object>> findStaleProcessingAnalyses(@Param("staleSeconds") long staleSeconds, @Param("limit") int limit);
    int failStaleAnalysis(@Param("id") String id, @Param("staleSeconds") long staleSeconds);
    /** 분석의 오류 코드(늦게 끝난 분석이 이미 STALE_PROCESSING으로 정리됐는지 확인용) */
    String findAnalysisErrorCode(@Param("analysisId") String analysisId);
    int completeAnalysis(@Param("id") String id, @Param("pronunciation") BigDecimal pronunciation,
                         @Param("rate") BigDecimal rate, @Param("fluency") BigDecimal fluency,
                         @Param("overall") BigDecimal overall, @Param("transcript") String transcript,
                         @Param("feedback") String feedback);
    int completeLocalAnalysis(@Param("id") String id, @Param("transcript") String transcript,
                              @Param("confidence") BigDecimal confidence, @Param("matchRate") BigDecimal matchRate,
                              @Param("comparisonJson") String comparisonJson, @Param("pronunciationStatus") String pronunciationStatus,
                              @Param("reviewStatus") String reviewStatus, @Param("engineName") String engineName,
                              @Param("modelName") String modelName, @Param("analysisType") String analysisType,
                              @Param("assessmentStatus") String assessmentStatus, @Param("assessmentJson") String assessmentJson,
                              @Param("analysisVersion") String analysisVersion);
    List<Map<String,Object>> findRecentItemAnalyses(@Param("studentId") long studentId, @Param("exerciseId") long exerciseId,
                                                    @Param("itemId") String itemId, @Param("excludeId") String excludeId,
                                                    @Param("limit") int limit);
    int markAudioDeleted(@Param("id") String id);
    int failAnalysis(@Param("id") String id, @Param("code") String code);
    Map<String,Object> findAnalysis(@Param("studentId") long studentId, @Param("id") String id);
    Map<String,Object> findAttemptByAnalysis(@Param("studentId") long studentId, @Param("analysisId") String analysisId);
    int createConversation(@Param("id") String id, @Param("studentId") long studentId, @Param("topic") String topic);
    Map<String,Object> findConversation(@Param("id") String id, @Param("studentId") long studentId);
    List<Map<String,Object>> findConversations(@Param("studentId") long studentId, @Param("limit") int limit, @Param("offset") int offset);
    long countConversations(@Param("studentId") long studentId);
    int insertMessage(@Param("conversationId") String conversationId, @Param("speaker") String speaker,
                      @Param("content") String content, @Param("feedback") String feedback, @Param("audioPath") String audioPath);
    List<Map<String,Object>> findRecentMessages(@Param("conversationId") String conversationId, @Param("limit") int limit);
    int closeConversation(@Param("id") String id, @Param("studentId") long studentId);
    int touchConversation(@Param("id") String id);
}
