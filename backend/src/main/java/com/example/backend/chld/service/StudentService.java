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
    /** 다시 연습: 이 영역에서 연습한 적 있는 세트(최근 연습 순, 세트당 한 번) */
    PageResponse<Map<String,Object>> practicedExercises(TokenPrincipal principal, String categoryId, int page, int size);
    Map<String,Object> saveAttempt(TokenPrincipal principal, AttemptRequest request);
    Map<String,Object> analyzeSpeech(TokenPrincipal principal, MultipartFile audio, String exerciseId, String itemId, String requestKey);
    Map<String,Object> speechAnalysis(TokenPrincipal principal, String analysisId);
    /** 내 분석의 AI 학습 피드백 상태(AI 호출 없음) */
    Map<String,Object> speechFeedback(TokenPrincipal principal, String analysisId);
    /** 내 분석의 AI 학습 피드백 만들기(근거 부족이면 NOT_EVALUABLE, AI 실패는 오류 응답이며 분석 결과는 그대로) */
    Map<String,Object> generateSpeechFeedback(TokenPrincipal principal, String analysisId);
    Map<String,Object> createConversation(TokenPrincipal principal, String topic);
    Map<String,Object> sendMessage(TokenPrincipal principal, String conversationId, MessageRequest request);
    Map<String,Object> sendAudio(TokenPrincipal principal, String conversationId, MultipartFile audio);
    PageResponse<Map<String,Object>> conversations(TokenPrincipal principal, int page, int size);
    PageResponse<Map<String,Object>> history(TokenPrincipal principal, String type, int page, int size);
    /** 학습 상태: 연습 횟수·연습한 콘텐츠 수·영역별 학습량·다시 연습한 콘텐츠·최근 14일 추이(저장된 기록만) */
    /** period: 7d·30d·all(없으면 all) */
    Map<String,Object> learningStatus(TokenPrincipal principal,String period);
    /** 저장된 연습 기록 기반 규칙형 추천(이유 포함) */
    Map<String,Object> recommendations(TokenPrincipal principal);
    /** 대화에 나온 낱말·주제와 연결되는 활성 연습 콘텐츠 */
    Map<String,Object> conversationContents(TokenPrincipal principal,String conversationId);
    /** 대화 끝내기: 실제 대화로 만든 요약·관련 연습·다음 활동 */
    Map<String,Object> endConversation(TokenPrincipal principal,String conversationId);
}
