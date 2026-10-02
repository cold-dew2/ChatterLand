package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechProviderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExternalProviderService {
    private final RestClient restClient;
    private final String aiUrl, aiKey, aiModel, speechUrl, speechKey;

    public ExternalProviderService(RestClient.Builder builder,
                                   @Value("${app.ai.endpoint:}") String aiUrl,
                                   @Value("${app.ai.api-key:}") String aiKey,
                                   @Value("${app.ai.model:gpt-4o-mini}") String aiModel,
                                   @Value("${app.speech.endpoint:}") String speechUrl,
                                   @Value("${app.speech.api-key:}") String speechKey) {
        this.restClient = builder.build(); this.aiUrl=aiUrl; this.aiKey=aiKey; this.aiModel=aiModel;
        this.speechUrl=speechUrl; this.speechKey=speechKey;
    }

    public SpeechProviderResponse analyzeAudio(String audioPath, String mime, String exerciseId, String itemId) {
        requireProvider(speechUrl, speechKey, "SPEECH_API_URL 및 SPEECH_API_KEY");
        MultiValueMap<String,Object> parts = new LinkedMultiValueMap<>();
        parts.add("audio", new FileSystemResource(Path.of(audioPath)));
        parts.add("mimeType", mime); parts.add("exerciseId", exerciseId); parts.add("itemId", itemId);
        try {
            SpeechProviderResponse result = restClient.post().uri(speechUrl).headers(h -> h.setBearerAuth(speechKey))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(SpeechProviderResponse.class);
            if (result == null || result.overallScore() == null || result.pronunciationScore() == null
                    || result.speechRateScore() == null || result.fluencyScore() == null
                    || result.transcript() == null || result.transcript().isBlank() || result.feedback() == null || result.feedback().isBlank()
                    || !validScore(result.pronunciationScore()) || !validScore(result.speechRateScore())
                    || !validScore(result.fluencyScore()) || !validScore(result.overallScore()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "음성 분석 제공자의 응답이 올바르지 않습니다.");
            return result;
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "음성 분석 제공자 응답 시간이 초과되었습니다.");
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "음성 분석 제공자 요청에 실패했습니다.");
        }
    }

    public void requireSpeechProvider() { requireProvider(speechUrl, speechKey, "SPEECH_API_URL 및 SPEECH_API_KEY"); }
    public void requireAiProvider() { requireProvider(aiUrl, aiKey, "AI_API_URL 및 AI_API_KEY"); }

    public String transcribe(String audioPath, String mime) {
        requireProvider(speechUrl, speechKey, "SPEECH_API_URL 및 SPEECH_API_KEY");
        MultiValueMap<String,Object> parts = new LinkedMultiValueMap<>();
        parts.add("audio", new FileSystemResource(Path.of(audioPath))); parts.add("mimeType", mime);
        try {
            Map<?,?> result = restClient.post().uri(speechUrl).headers(h -> h.setBearerAuth(speechKey))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(Map.class);
            Object transcript = result == null ? null : result.get("transcript");
            if (!(transcript instanceof String text) || text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "음성 인식 결과가 비어 있습니다.");
            return text;
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "음성 인식 제공자 응답 시간이 초과되었습니다.");
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "음성 인식 제공자 요청에 실패했습니다.");
        }
    }

    public String generateReply(String topic, List<Map<String,Object>> history) {
        requireProvider(aiUrl, aiKey, "AI_API_URL 및 AI_API_KEY");
        List<Map<String,String>> messages = new ArrayList<>();
        messages.add(Map.of("role","system","content","You are a warm, child-safe Korean speech-language practice partner. Keep responses short and age-appropriate. Encourage clear speech without diagnosing. Topic: "+topic));
        for (Map<String,Object> item : history) {
            String speaker = String.valueOf(item.get("speaker"));
            String content = String.valueOf(item.getOrDefault("content", ""));
            if (!content.isBlank()) messages.add(Map.of("role", "ASSISTANT".equals(speaker) ? "assistant" : "user", "content", content));
        }
        Map<String,Object> payload = new LinkedHashMap<>(); payload.put("model",aiModel); payload.put("messages",messages); payload.put("temperature",0.4);
        try {
            Map<?,?> response = restClient.post().uri(aiUrl).headers(h->h.setBearerAuth(aiKey)).contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(Map.class);
            if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI 제공자의 응답이 올바르지 않습니다.");
            Object message = ((Map<?,?>) choices.get(0)).get("message");
            Object content = message instanceof Map<?,?> m ? m.get("content") : null;
            if (!(content instanceof String text) || text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI 응답이 비어 있습니다.");
            return text;
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"AI 제공자 응답 시간이 초과되었습니다.");
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI 대화 제공자 요청에 실패했습니다.");
        }
    }

    private void requireProvider(String url, String key, String configName) {
        if (url == null || url.isBlank() || key == null || key.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, configName + " 환경변수 설정이 필요합니다.");
    }

    private boolean validScore(java.math.BigDecimal score) {
        return score.compareTo(java.math.BigDecimal.ZERO) >= 0 && score.compareTo(java.math.BigDecimal.valueOf(100)) <= 0;
    }
}
