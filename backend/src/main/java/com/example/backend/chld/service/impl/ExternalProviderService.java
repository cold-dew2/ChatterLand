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
import java.util.List;
import java.util.Map;

@Service
public class ExternalProviderService {
    private final RestClient restClient;
    private final AiTextClient aiText;
    private final String speechUrl, speechKey;

    public ExternalProviderService(RestClient.Builder builder, AiTextClient aiText,
                                   @Value("${app.speech.endpoint:}") String speechUrl,
                                   @Value("${app.speech.api-key:}") String speechKey) {
        this.restClient = builder.build(); this.aiText = aiText;
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
    public void requireAiProvider() { aiText.requireConfigured(); }

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

    /** AI 대화 응답. 제공자 형식(Gemini·OpenAI 호환)과 오류 처리는 AiTextClient가 맡는다. */
    public String generateReply(String topic, List<Map<String,Object>> history) {
        aiText.requireConfigured();
        List<AiTextClient.Turn> turns = new ArrayList<>();
        for (Map<String,Object> item : history) {
            String speaker = String.valueOf(item.get("speaker"));
            String content = String.valueOf(item.getOrDefault("content", ""));
            if (!content.isBlank()) turns.add(new AiTextClient.Turn("ASSISTANT".equals(speaker) ? "assistant" : "user", content));
        }
        return aiText.generate("You are a warm, child-safe Korean speech-language practice partner. Keep responses short and age-appropriate. "
                + "Reply in Korean. Encourage clear speech without diagnosing. Topic: " + topic, turns, 0.4, 1024);
    }

    private void requireProvider(String url, String key, String configName) {
        if (url == null || url.isBlank() || key == null || key.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, configName + " 환경변수 설정이 필요합니다.");
    }

    private boolean validScore(java.math.BigDecimal score) {
        return score.compareTo(java.math.BigDecimal.ZERO) >= 0 && score.compareTo(java.math.BigDecimal.valueOf(100)) <= 0;
    }
}
