package com.example.backend.chld.service.impl;

import com.example.backend.chld.exception.AiProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 외부 AI 텍스트 생성 호출(AI 대화·학습 피드백 공통). 키는 서버에서만 쓰고 로그·응답에 남기지 않는다.
 * AI_API_URL 형식으로 제공자를 고른다(URL·모델 이름은 설정값 그대로 쓴다).
 * - Gemini 네이티브: ".../models/{모델}:generateContent" → x-goog-api-key 헤더, systemInstruction·contents 형식. 모델은 URL의 이름.
 * - 그 밖(OpenAI 호환 Chat Completions): Bearer 인증, model·messages 형식. 모델은 AI_MODEL.
 */
@Slf4j
@Service
public class AiTextClient {
    private static final Pattern GEMINI_MODEL = Pattern.compile("/models/([^/:?]+):generateContent");

    /** 대화 한 턴. role: "user" 또는 "assistant" */
    public record Turn(String role, String text) { }

    private final RestClient restClient;
    private final String url, key, configuredModel;

    @Autowired
    public AiTextClient(RestClient.Builder builder,
                        @Value("${app.ai.endpoint:}") String url,
                        @Value("${app.ai.api-key:}") String key,
                        @Value("${app.ai.model:gpt-4o-mini}") String configuredModel,
                        @Value("${app.ai.timeout:20s}") Duration timeout) {
        this(withTimeout(builder, timeout), url, key, configuredModel);
    }

    /** AI 호출 전용 시간 제한(연결 5초, 응답 timeout). 공용 RestClient 설정은 바꾸지 않는다. */
    private static RestClient withTimeout(RestClient.Builder builder, Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        return builder.clone().requestFactory(factory).build();
    }

    /** 테스트용: 요청을 가로채는 RestClient를 그대로 쓴다. */
    AiTextClient(RestClient restClient, String url, String key, String configuredModel) {
        this.restClient = restClient;
        this.url = url == null ? "" : url.trim();
        this.key = key == null ? "" : key.trim();
        this.configuredModel = configuredModel;
    }

    public boolean isConfigured() { return !url.isEmpty() && !key.isEmpty(); }

    public void requireConfigured() {
        if (!isConfigured())
            throw new AiProviderException(AiProviderException.NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 기능을 쓰려면 서버에 AI_API_URL 및 AI_API_KEY 환경변수 설정이 필요합니다.");
    }

    boolean isGemini() { return GEMINI_MODEL.matcher(url).find(); }

    /** 응답을 만든 모델 이름(저장·표시용). Gemini는 URL의 모델 이름, 그 밖은 AI_MODEL. */
    public String modelName() {
        Matcher gemini = GEMINI_MODEL.matcher(url);
        return gemini.find() ? gemini.group(1) : configuredModel;
    }

    public String generate(String systemInstruction, List<Turn> turns, double temperature, int maxOutputTokens) {
        requireConfigured();
        try {
            return isGemini() ? callGemini(systemInstruction, turns, temperature, maxOutputTokens)
                    : callOpenAi(systemInstruction, turns, temperature, maxOutputTokens);
        } catch (ResourceAccessException e) {
            log.warn("AI provider call failed: AI_TIMEOUT ({})", e.getClass().getSimpleName());
            throw new AiProviderException(AiProviderException.TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "AI 서비스 응답 시간이 초과되었어요. 잠시 뒤 다시 시도해 주세요.");
        } catch (RestClientResponseException e) {
            // 응답 본문에는 요청 내용이 섞일 수 있어 상태 코드만 남긴다.
            int status = e.getStatusCode().value();
            // Gemini는 잘못된 키를 400(reason API_KEY_INVALID)으로 알린다. 본문은 이유 확인에만 쓰고 기록하지 않는다.
            if (status == 400 && e.getResponseBodyAsString().contains("API_KEY_INVALID")) status = 401;
            AiProviderException mapped = switch (status) {
                case 401, 403 -> new AiProviderException(AiProviderException.AUTH_FAILED, HttpStatus.SERVICE_UNAVAILABLE,
                        "AI 서비스가 서버의 API 키를 거부했어요. 관리자가 AI_API_KEY 설정을 확인해야 해요.");
                case 429 -> new AiProviderException(AiProviderException.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                        "AI 서비스 사용량이 많아요. 잠시 뒤 다시 시도해 주세요.");
                case 400, 404 -> new AiProviderException(AiProviderException.REQUEST_REJECTED, HttpStatus.BAD_GATEWAY,
                        "AI 서비스가 요청을 거부했어요. 관리자가 AI_API_URL·모델 설정을 확인해야 해요.");
                default -> new AiProviderException(AiProviderException.PROVIDER_ERROR, HttpStatus.BAD_GATEWAY,
                        "AI 서비스에 일시적인 문제가 있어요. 잠시 뒤 다시 시도해 주세요.");
            };
            log.warn("AI provider call failed: {} (provider HTTP {})", mapped.getCode(), status);
            throw mapped;
        }
    }

    private String callGemini(String systemInstruction, List<Turn> turns, double temperature, int maxOutputTokens) {
        List<Map<String,Object>> contents = new ArrayList<>();
        for (Turn turn : turns)
            contents.add(Map.of("role", "assistant".equals(turn.role()) ? "model" : "user", "parts", List.of(Map.of("text", turn.text()))));
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        body.put("contents", contents);
        body.put("generationConfig", Map.of("temperature", temperature, "maxOutputTokens", maxOutputTokens));
        // 키는 URL 쿼리(?key=)가 아니라 헤더로 보내 접근 로그·오류 메시지에 남지 않게 한다.
        Map<?,?> response = restClient.post().uri(url).header("x-goog-api-key", key)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
        if (response == null) throw badResponse("empty body");
        if (response.get("promptFeedback") instanceof Map<?,?> feedback && feedback.get("blockReason") != null) throw blocked();
        if (!(response.get("candidates") instanceof List<?> candidates) || candidates.isEmpty()) throw badResponse("no candidates");
        Map<?,?> first = (Map<?,?>) candidates.get(0);
        StringBuilder text = new StringBuilder();
        if (first.get("content") instanceof Map<?,?> content && content.get("parts") instanceof List<?> parts) {
            for (Object part : parts) {
                if (part instanceof Map<?,?> p && !Boolean.TRUE.equals(p.get("thought")) && p.get("text") instanceof String t) text.append(t);
            }
        }
        if (text.toString().isBlank() && "SAFETY".equals(first.get("finishReason"))) throw blocked();
        if (text.toString().isBlank()) throw badResponse("empty text, finishReason=" + first.get("finishReason"));
        return text.toString().trim();
    }

    private String callOpenAi(String systemInstruction, List<Turn> turns, double temperature, int maxOutputTokens) {
        List<Map<String,String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemInstruction));
        for (Turn turn : turns) messages.add(Map.of("role", "assistant".equals(turn.role()) ? "assistant" : "user", "content", turn.text()));
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("model", configuredModel); payload.put("messages", messages); payload.put("temperature", temperature);
        payload.put("max_tokens", maxOutputTokens);
        Map<?,?> response = restClient.post().uri(url).headers(h -> h.setBearerAuth(key))
                .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(Map.class);
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) throw badResponse("no choices");
        Object message = ((Map<?,?>) choices.get(0)).get("message");
        Object content = message instanceof Map<?,?> m ? m.get("content") : null;
        if (!(content instanceof String text) || text.isBlank()) throw badResponse("empty content");
        return text.trim();
    }

    private AiProviderException badResponse(String detail) {
        log.warn("AI provider call failed: AI_BAD_RESPONSE ({})", detail);
        return new AiProviderException(AiProviderException.BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "AI 서비스의 응답이 올바르지 않아요. 잠시 뒤 다시 시도해 주세요.");
    }

    private AiProviderException blocked() {
        log.warn("AI provider call failed: AI_BLOCKED");
        return new AiProviderException(AiProviderException.BLOCKED, HttpStatus.BAD_GATEWAY, "AI 서비스가 이 요청에 답하지 않았어요. 다른 내용으로 다시 시도해 주세요.");
    }
}
