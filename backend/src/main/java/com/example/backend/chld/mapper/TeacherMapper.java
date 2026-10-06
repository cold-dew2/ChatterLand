package com.example.backend.chld.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface TeacherMapper {
    List<Map<String,Object>> findStudents(@Param("teacherId") long teacherId, @Param("keyword") String keyword,
                                          @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);
    long countStudents(@Param("teacherId") long teacherId, @Param("keyword") String keyword, @Param("status") String status);
    List<Map<String,Object>> findAvailableStudents(@Param("teacherId") long teacherId, @Param("centerId") long centerId);
    Map<String,Object> findStudent(@Param("teacherId") long teacherId, @Param("studentId") long studentId);
    Map<String,Object> findProfileById(@Param("studentId") long studentId);
    int insertStudentProfile(@Param("profile") StudentProfileCommand profile);
    int linkStudent(@Param("teacherId") long teacherId, @Param("studentId") long studentId, @Param("memo") String memo);
    int updateStudent(@Param("teacherId") long teacherId, @Param("studentId") long studentId,
                      @Param("name") String name, @Param("age") Integer age,
                      @Param("phone") String phone, @Param("memo") String memo,
                      @Param("focusAreas") String focusAreas, @Param("sessionsLimit") Integer sessionsLimit,
                      @Param("status") String status, @Param("learnerType") String learnerType);
    int unlinkStudent(@Param("teacherId") long teacherId, @Param("studentId") long studentId);
    List<Map<String,Object>> findStudentSessions(@Param("studentId") long studentId, @Param("limit") int limit, @Param("offset") int offset);
    long countStudentSessions(@Param("studentId") long studentId);
    List<Map<String,Object>> findHomeworks(@Param("teacherId") long teacherId, @Param("studentId") Long studentId,
                                           @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);
    long countHomeworks(@Param("teacherId") long teacherId, @Param("studentId") Long studentId, @Param("status") String status);
    int insertHomework(@Param("teacherId") long teacherId, @Param("studentId") long studentId, @Param("title") String title,
                       @Param("type") String type, @Param("description") String description,
                       @Param("targetMinutes") int targetMinutes, @Param("dueDate") LocalDate dueDate,
                       @Param("requestKey") String requestKey, @Param("requestHash") String requestHash, @Param("exerciseId") Long exerciseId);
    /** 담당 학생의 연습 기록(자율 PRACTICE·숙제 HOMEWORK). type이 null이면 둘 다 */
    List<Map<String,Object>> findStudentAttempts(@Param("studentId") long studentId, @Param("type") String type,
                                                 @Param("limit") int limit, @Param("offset") int offset);
    long countStudentAttempts(@Param("studentId") long studentId, @Param("type") String type);
    Map<String,Object> attemptSummary(@Param("studentId") long studentId);
    /** 같은 선생님이 같은 Idempotency-Key로 만든 숙제(요청 지문 requestHash 포함) */
    Map<String,Object> findHomeworkByRequestKey(@Param("teacherId") long teacherId, @Param("requestKey") String requestKey);
    Map<String,Object> findLatestHomework(@Param("teacherId") long teacherId,@Param("studentId") long studentId,@Param("title") String title);
    Map<String,Object> findHomework(@Param("teacherId") long teacherId, @Param("homeworkId") long homeworkId);
    int updateHomework(@Param("teacherId") long teacherId, @Param("homeworkId") long homeworkId,
                       @Param("title") String title, @Param("type") String type, @Param("description") String description,
                       @Param("targetMinutes") Integer targetMinutes, @Param("dueDate") LocalDate dueDate,
                       @Param("status") String status, @Param("done") Boolean done,
                       @Param("version") Integer version);
    int deleteHomework(@Param("teacherId") long teacherId, @Param("homeworkId") long homeworkId, @Param("version") int version);
    Map<String,Object> analytics(@Param("studentId") long studentId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<Map<String,Object>> areaAnalytics(@Param("studentId") long studentId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<Map<String,Object>> findStudentAnalyses(@Param("studentId") long studentId, @Param("reviewStatus") String reviewStatus,
                                                 @Param("limit") int limit, @Param("offset") int offset);
    List<Map<String,Object>> findAnalysesForReport(@Param("studentId") long studentId, @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate, @Param("limit") int limit);
    long countStudentAnalyses(@Param("studentId") long studentId, @Param("reviewStatus") String reviewStatus);
    Map<String,Object> findAnalysisForTeacher(@Param("teacherId") long teacherId, @Param("analysisId") String analysisId);
    int reviewAnalysis(@Param("teacherId") long teacherId, @Param("analysisId") String analysisId,
                       @Param("judgement") String judgement, @Param("note") String note,
                       @Param("confirmedJson") String confirmedJson);
    List<Map<String,Object>> scoreTrend(@Param("studentId") long studentId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
