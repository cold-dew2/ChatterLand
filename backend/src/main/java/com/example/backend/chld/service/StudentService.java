package com.example.backend.chld.service;

import com.example.backend.chld.dto.request.AttemptRequest;
import com.example.backend.chld.dto.request.MessageRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.global.jwt.TokenPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface StudentService {
    Map<String,Object> profile(TokenPrincipal principal);
    PageResponse<Map<String,Object>> sessions(TokenPrincipal principal, int page, int size, String status);
    PageResponse<Map<String,Object>> homeworks(TokenPrincipal principal, int page, int size);
    Map<String,Object> completeHomework(TokenPrincipal principal, long homeworkId);
    Map<String,Object> session(TokenPrincipal principal, long sessionId);
    Object categories(TokenPrincipal principal);
    PageResponse<Map<String,Object>> exercises(TokenPrincipal principal, String categoryId, int page, int size);
    Map<String,Object> saveAttempt(TokenPrincipal principal, AttemptRequest request);
    Map<String,Object> analyzeSpeech(TokenPrincipal principal, MultipartFile audio, String exerciseId, String itemId);
    Map<String,Object> speechAnalysis(TokenPrincipal principal, String analysisId);
    Map<String,Object> createConversation(TokenPrincipal principal, String topic);
    Map<String,Object> sendMessage(TokenPrincipal principal, String conversationId, MessageRequest request);
    Map<String,Object> sendAudio(TokenPrincipal principal, String conversationId, MultipartFile audio);
    PageResponse<Map<String,Object>> conversations(TokenPrincipal principal, int page, int size);
    PageResponse<Map<String,Object>> history(TokenPrincipal principal, String type, int page, int size);
}
