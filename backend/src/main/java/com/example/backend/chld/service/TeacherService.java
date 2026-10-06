package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.HomeworkCreateRequest;
import com.example.backend.chld.dto.request.HomeworkUpdateRequest;
import com.example.backend.chld.dto.request.SpeechReviewRequest;
import com.example.backend.chld.dto.request.StudentCreateRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.global.jwt.TokenPrincipal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

public interface TeacherService {
    PageResponse<Map<String,Object>> students(TokenPrincipal principal,String keyword,String status,int page,int size);
    List<Map<String,Object>> availableStudents(TokenPrincipal principal);
    Map<String,Object> addStudent(TokenPrincipal principal,StudentCreateRequest request);
    Map<String,Object> updateStudent(TokenPrincipal principal,long studentId,StudentCreateRequest request);
    void deleteStudent(TokenPrincipal principal,long studentId);
    Map<String,Object> student(TokenPrincipal principal,long studentId);
    PageResponse<Map<String,Object>> sessions(TokenPrincipal principal,long studentId,int page,int size);
    PageResponse<Map<String,Object>> homeworks(TokenPrincipal principal,Long studentId,String status,int page,int size);
    /** idempotencyKey: 화면이 등록 의도마다 만든 키(없으면 null). 같은 키·같은 내용은 처음 만든 숙제를 돌려준다. */
    Map<String,Object> addHomework(TokenPrincipal principal,HomeworkCreateRequest request,String idempotencyKey);
    Map<String,Object> updateHomework(TokenPrincipal principal,long homeworkId,HomeworkUpdateRequest request);
    /** version: 화면이 조회한 숙제 버전. 그 사이 바뀌었으면 409(VERSION_CONFLICT)로 지우지 않는다. */
    /** 담당 학생의 연습 기록(type: SELF 자율·LESSON 수업·HOMEWORK 숙제·PRACTICE 유형 구분 전 기록, 없으면 전체) */
    PageResponse<Map<String,Object>> studentAttempts(TokenPrincipal principal,long studentId,String type,int page,int size);
    /** 담당 학생의 연습 횟수 요약(전체·자율·숙제·연습한 세트 수·최근 연습 시각) */
    Map<String,Object> studentAttemptSummary(TokenPrincipal principal,long studentId);
    /** 담당 학생 분석의 AI 학습 피드백(학생과 같은 저장 결과, AI 호출 없음). 담당이 아니면 404. */
    Map<String,Object> speechFeedback(TokenPrincipal principal,String analysisId);
    void deleteHomework(TokenPrincipal principal,long homeworkId,int version);
    Map<String,Object> analytics(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate);
    Map<String,Object> report(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate);
    PageResponse<Map<String,Object>> speechAnalyses(TokenPrincipal principal,long studentId,String reviewStatus,int page,int size);
    SpeechAudio speechAnalysisAudio(TokenPrincipal principal,String analysisId);
    Map<String,Object> reviewSpeechAnalysis(TokenPrincipal principal,String analysisId,SpeechReviewRequest request);
    byte[] reportPdf(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate);

    record SpeechAudio(byte[] content, String mimeType) { }
}
