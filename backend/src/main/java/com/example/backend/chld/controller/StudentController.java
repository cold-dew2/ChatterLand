package com.example.backend.chld.controller;

import com.example.backend.chld.dto.request.AttemptRequest;
import com.example.backend.chld.dto.request.ConversationRequest;
import com.example.backend.chld.dto.request.MessageRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.service.StudentService;
import com.example.backend.global.jwt.TokenPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1")
public class StudentController {
    private final StudentService students;
    public StudentController(StudentService students) { this.students=students; }

    @GetMapping("/students/me") public Map<String,Object> me(@AuthenticationPrincipal TokenPrincipal principal) { return students.profile(principal); }
    @GetMapping("/students/me/sessions") public PageResponse<Map<String,Object>> sessions(@AuthenticationPrincipal TokenPrincipal principal,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status) { return students.sessions(principal,page,size,status); }
    @GetMapping("/students/me/homeworks") public PageResponse<Map<String,Object>> homeworks(@AuthenticationPrincipal TokenPrincipal principal,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return students.homeworks(principal,page,size); }
    @PatchMapping("/students/me/homeworks/{homeworkId}/complete") public Map<String,Object> completeHomework(@AuthenticationPrincipal TokenPrincipal principal,
            @PathVariable long homeworkId) { return students.completeHomework(principal,homeworkId); }
    @GetMapping("/sessions/{sessionId}") public Map<String,Object> session(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable long sessionId) { return students.session(principal,sessionId); }
    @GetMapping("/practice/categories") public Object categories(@AuthenticationPrincipal TokenPrincipal principal) { return students.categories(principal); }
    @GetMapping("/practice/{categoryId}/exercises") public PageResponse<Map<String,Object>> exercises(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable String categoryId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return students.exercises(principal,categoryId,page,size); }
    @PostMapping("/practice/attempts") public Map<String,Object> attempt(@AuthenticationPrincipal TokenPrincipal principal,@Valid @RequestBody AttemptRequest request) { return students.saveAttempt(principal,request); }
    @PostMapping(value="/speech/analyze",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> analyze(@AuthenticationPrincipal TokenPrincipal principal,@RequestPart("audio") MultipartFile audio,
            @RequestPart("exerciseId") @NotBlank String exerciseId,@RequestPart("itemId") @NotBlank String itemId) { return students.analyzeSpeech(principal,audio,exerciseId,itemId); }
    @GetMapping("/speech/analyses/{analysisId}") public Map<String,Object> analysis(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable String analysisId) { return students.speechAnalysis(principal,analysisId); }
    @PostMapping("/ai/conversations") public Map<String,Object> createConversation(@AuthenticationPrincipal TokenPrincipal principal,@Valid @RequestBody ConversationRequest request) { return students.createConversation(principal,request.topic()); }
    @PostMapping(value="/ai/conversations/{conversationId}/messages",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> sendMessage(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable String conversationId,@Valid @RequestBody MessageRequest request) { return students.sendMessage(principal,conversationId,request); }
    @PostMapping(value="/ai/conversations/{conversationId}/messages",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> sendAudio(@AuthenticationPrincipal TokenPrincipal principal,@PathVariable String conversationId,@RequestPart("audio") MultipartFile audio) { return students.sendAudio(principal,conversationId,audio); }
    @GetMapping("/ai/conversations") public PageResponse<Map<String,Object>> conversations(@AuthenticationPrincipal TokenPrincipal principal,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return students.conversations(principal,page,size); }
    @GetMapping("/students/me/history") public PageResponse<Map<String,Object>> history(@AuthenticationPrincipal TokenPrincipal principal,@RequestParam(required=false) String type,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) { return students.history(principal,type,page,size); }
}
