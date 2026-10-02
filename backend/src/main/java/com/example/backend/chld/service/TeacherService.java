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
    Map<String,Object> addHomework(TokenPrincipal principal,HomeworkCreateRequest request);
    Map<String,Object> updateHomework(TokenPrincipal principal,long homeworkId,HomeworkUpdateRequest request);
    void deleteHomework(TokenPrincipal principal,long homeworkId);
    Map<String,Object> analytics(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate);
    Map<String,Object> report(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate);
    PageResponse<Map<String,Object>> speechAnalyses(TokenPrincipal principal,long studentId,String reviewStatus,int page,int size);
    SpeechAudio speechAnalysisAudio(TokenPrincipal principal,String analysisId);
    Map<String,Object> reviewSpeechAnalysis(TokenPrincipal principal,String analysisId,SpeechReviewRequest request);
    byte[] reportPdf(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate);

    record SpeechAudio(byte[] content, String mimeType) { }
}
