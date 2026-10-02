package com.example.backend.chld.controller;

import com.example.backend.chld.dto.request.HomeworkCreateRequest;
import com.example.backend.chld.dto.request.HomeworkUpdateRequest;
import com.example.backend.chld.dto.request.SpeechReviewRequest;
import com.example.backend.chld.dto.request.StudentCreateRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.service.TeacherService;
import com.example.backend.global.jwt.TokenPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/teachers/me")
public class TeacherController {
    private final TeacherService teachers;
    public TeacherController(TeacherService teachers) { this.teachers=teachers; }

    @GetMapping("/students") public PageResponse<Map<String,Object>> students(@AuthenticationPrincipal TokenPrincipal principal,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return teachers.students(principal,keyword,status,page,size); }
    @GetMapping("/students/available") public java.util.List<Map<String,Object>> availableStudents(@AuthenticationPrincipal TokenPrincipal principal) { return teachers.availableStudents(principal); }
    @PostMapping("/students") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> addStudent(@AuthenticationPrincipal TokenPrincipal principal,@Valid @RequestBody StudentCreateRequest request) { return teachers.addStudent(principal,request); }
    @PutMapping("/students/{studentId}")
    public Map<String,Object> updateStudent(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long studentId,@Valid @RequestBody StudentCreateRequest request) { return teachers.updateStudent(principal,studentId,request); }
    @DeleteMapping("/students/{studentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStudent(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long studentId) { teachers.deleteStudent(principal,studentId); }
    @GetMapping("/students/{studentId}") public Map<String,Object> student(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long studentId) { return teachers.student(principal,studentId); }
    @GetMapping("/students/{studentId}/sessions") public PageResponse<Map<String,Object>> sessions(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long studentId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return teachers.sessions(principal,studentId,page,size); }
    @GetMapping("/homeworks") public PageResponse<Map<String,Object>> homeworks(@AuthenticationPrincipal TokenPrincipal principal,@RequestParam(required=false) Long studentId,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return teachers.homeworks(principal,studentId,status,page,size); }
    @PostMapping("/homeworks") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> addHomework(@AuthenticationPrincipal TokenPrincipal principal,@Valid @RequestBody HomeworkCreateRequest request) { return teachers.addHomework(principal,request); }
    @PatchMapping("/homeworks/{homeworkId}") public Map<String,Object> updateHomework(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long homeworkId,@Valid @RequestBody HomeworkUpdateRequest request) { return teachers.updateHomework(principal,homeworkId,request); }
    @DeleteMapping("/homeworks/{homeworkId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHomework(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long homeworkId) { teachers.deleteHomework(principal,homeworkId); }
    @GetMapping("/students/{studentId}/analytics") public Map<String,Object> analytics(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long studentId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) { return teachers.analytics(principal,studentId,startDate,endDate); }
    @GetMapping("/students/{studentId}/report") public Map<String,Object> report(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long studentId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) { return teachers.report(principal,studentId,startDate,endDate); }
    @GetMapping("/students/{studentId}/speech-analyses") public PageResponse<Map<String,Object>> speechAnalyses(@AuthenticationPrincipal TokenPrincipal principal,
            @PathVariable long studentId,@RequestParam(required=false) String reviewStatus,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return teachers.speechAnalyses(principal,studentId,reviewStatus,page,size); }
    @GetMapping("/speech-analyses/{analysisId}/audio")
    public ResponseEntity<byte[]> speechAnalysisAudio(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable String analysisId) {
        TeacherService.SpeechAudio audio=teachers.speechAnalysisAudio(principal,analysisId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(audio.mimeType()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(audio.content());
    }
    @PatchMapping("/speech-analyses/{analysisId}/review") public Map<String,Object> reviewSpeechAnalysis(@AuthenticationPrincipal TokenPrincipal principal,
            @PathVariable String analysisId,@Valid @RequestBody SpeechReviewRequest request) { return teachers.reviewSpeechAnalysis(principal,analysisId,request); }
    @GetMapping(value="/students/{studentId}/report/download",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReport(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long studentId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) {
        byte[] pdf=teachers.reportPdf(principal,studentId,startDate,endDate);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=chatterland-report.pdf").body(pdf);
    }
}
