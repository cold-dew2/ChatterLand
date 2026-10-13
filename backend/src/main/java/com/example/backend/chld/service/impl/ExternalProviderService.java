package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechProviderResponse;
import com.example.backend.chld.exception.AiProviderException;
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

    /** AI 대화 응답. 제공자 형식(Gemini·OpenAI 호환)과 오류 처리는 AiTextClient가 맡는다. 외부 AI에는 주제와 대화 내용만 보낸다. */
    public String generateReply(String topic, List<Map<String,Object>> history) {
        aiText.requireConfigured();
        return aiText.generate(replyInstruction(topic), turns(history), 0.4, 1024);
    }

    /** 대화 종료 요약. 실패하면 예외(가짜 요약을 만들지 않는다). */
    public String summarizeConversation(String topic, List<Map<String,Object>> history) {
        aiText.requireConfigured();
        List<AiTextClient.Turn> turns = new ArrayList<>(turns(history));
        turns.add(new AiTextClient.Turn("user", "지금까지의 대화를 규칙대로 요약해 주세요."));
        String summary = aiText.generate(summaryInstruction(topic), turns, 0.2, 512);
        if (summary == null || summary.isBlank()) throw new AiProviderException(AiProviderException.BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "AI 서비스의 응답이 올바르지 않아요. 잠시 뒤 다시 시도해 주세요.");
        return summary.trim();
    }

    /**
     * 대화 규칙: 학생 말에 반응한 뒤 같은 주제로 이어지는 후속 질문 하나, 어려워하면 더 쉽게 다시 설명.
     * 칭찬은 참여·노력만(음향 분석으로 확인하지 않은 발음 정확도·명료성은 말하지 않음), 진단·공식 규칙 단정 금지, 개인정보 묻지 않기.
     */
    static String replyInstruction(String topic) {
        return "You are a warm, child-safe Korean conversation practice partner for a young child. Topic: " + topic + ". Rules: "
                + "(1) Reply in Korean in 2-3 short, age-appropriate sentences. "
                + "(2) First respond to what the child actually said, then ask exactly ONE follow-up question that continues the same topic and invites a longer answer "
                + "(prefer what/why/how questions over yes/no; do not repeat a question you already asked; move the topic forward a little, e.g. from what to why or when). "
                + "(3) If the child says they do not know, that it is hard, or seems confused (e.g. '몰라', '모르겠어', '어려워', '무슨 뜻'), re-explain more simply with one concrete everyday example and offer two easy choices to answer. "
                + "(4) Praise only effort and participation (e.g. '이야기해 줘서 고마워'). Never comment on, score or praise pronunciation accuracy, clarity, fluency or speech quality — you cannot hear the audio and it was not acoustically analyzed. Never diagnose. "
                + "(5) Do not present pronunciation rules or facts as official or verified; keep explanations general. "
                + "(6) Never ask for personal information such as full name, address, school name or phone number.";
    }

    static String summaryInstruction(String topic) {
        return "Summarize a Korean practice conversation with a young child. Topic: " + topic + ". Write in Korean, 2-3 short sentences addressed to the child. "
                + "Use ONLY what appears in the conversation: which things the child talked about and which words or ideas came up. "
                + "Thank the child for participating. Do not evaluate, score or praise pronunciation, clarity or fluency, do not diagnose, and do not add facts that were not in the conversation. "
                + "Plain text only, no lists or markdown.";
    }

    private static List<AiTextClient.Turn> turns(List<Map<String,Object>> history) {
        List<AiTextClient.Turn> turns = new ArrayList<>();
        for (Map<String,Object> item : history) {
            String speaker = String.valueOf(item.get("speaker"));
            String content = String.valueOf(item.getOrDefault("content", ""));
            if (!content.isBlank()) turns.add(new AiTextClient.Turn("ASSISTANT".equals(speaker) ? "assistant" : "user", content));
        }
        return turns;
    }

    private void requireProvider(String url, String key, String configName) {
        if (url == null || url.isBlank() || key == null || key.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, configName + " 환경변수 설정이 필요합니다.");
    }

    private boolean validScore(java.math.BigDecimal score) {
        return score.compareTo(java.math.BigDecimal.ZERO) >= 0 && score.compareTo(java.math.BigDecimal.valueOf(100)) <= 0;
    }
}
