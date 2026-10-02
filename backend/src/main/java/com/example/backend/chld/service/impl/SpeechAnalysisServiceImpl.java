package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechProviderResponse;
import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import com.example.backend.chld.dto.response.TranscriptComparison;
import com.example.backend.chld.mapper.StudentMapper;
import com.example.backend.chld.service.AudioRetentionService;
import com.example.backend.chld.service.SpeechAnalysisService;
import com.example.backend.chld.service.SpeechRecognitionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 연습 녹음 분석.
 * - 일반 아동(SENTENCE_MATCH): 로컬 음성 인식 결과와 목표 문장의 음절 일치도를 계산한다. 발음 점수는 만들지 않는다.
 * - 언어재활 아동(PRONUNCIATION_REVIEW): 검증된 발음 평가 방법이 없으므로 발음 평가는 미평가로 두고 선생님 검토 대기로 저장한다.
 */
@Service
public class SpeechAnalysisServiceImpl implements SpeechAnalysisService {
    static final String MODE_SENTENCE_MATCH = "SENTENCE_MATCH";
    static final String MODE_PRONUNCIATION_REVIEW = "PRONUNCIATION_REVIEW";
    static final String PRONUNCIATION_NOT_EVALUATED = "NOT_EVALUATED";
    static final String REVIEW_NOT_REQUIRED = "NOT_REQUIRED";
    static final String REVIEW_PENDING = "PENDING";

    /** 짧은/빈 녹음에서 Whisper 계열 모델이 만들어내는 대표적인 환각 문장 */
    private static final List<String> HALLUCINATION_PHRASES = List.of(
            "구독과 좋아요", "구독 좋아요", "시청해 주셔서", "시청해주셔서", "끝까지 시청", "자막 제공", "MBC 뉴스", "도움이 되었습니다", "-끝-");

    private final StudentMapper mapper;
    private final SpeechRecognitionService recognizer;
    private final TranscriptComparator comparator;
    private final AudioStorageService audioStorage;
    private final ExternalProviderService externalProvider;
    private final AudioRetentionService retention;
    private final String engine;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public SpeechAnalysisServiceImpl(StudentMapper mapper, SpeechRecognitionService recognizer, TranscriptComparator comparator,
                                     AudioStorageService audioStorage, ExternalProviderService externalProvider,
                                     AudioRetentionService retention, @Value("${app.speech.engine:local}") String engine) {
        this.mapper = mapper; this.recognizer = recognizer; this.comparator = comparator;
        this.audioStorage = audioStorage; this.externalProvider = externalProvider; this.retention = retention;
        this.engine = engine == null ? "local" : engine.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public Map<String,Object> analyze(long studentId, String learnerType, long exerciseId, long itemId, String targetText, MultipartFile audio) {
        if ("external".equals(engine)) return analyzeWithExternalProvider(studentId, exerciseId, itemId, audio);
        recognizer.requireAvailable();
        String mode = LEARNER_THERAPY.equals(learnerType) ? MODE_PRONUNCIATION_REVIEW : MODE_SENTENCE_MATCH;
        AudioStorageService.StoredAudio stored = audioStorage.store(audio);
        String analysisId = UUID.randomUUID().toString();
        try {
            mapper.insertAnalysis(analysisId, studentId, exerciseId, Long.toString(itemId), stored.path(), stored.mimeType(),
                    mode, targetText, LocalWhisperRecognitionService.ENGINE_NAME, null, retention.retentionMonths());
            SpeechRecognitionResult result = recognizer.recognize(Path.of(stored.path()));
            String transcript = result.transcript();
            if (transcript.isBlank() || isHallucination(transcript, targetText))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "목소리를 알아듣지 못했어요. 조금 더 크고 또렷하게 다시 말해 주세요.");
            TranscriptComparison comparison = comparator.compare(targetText, transcript);
            boolean therapy = MODE_PRONUNCIATION_REVIEW.equals(mode);
            // 언어재활 모드에서는 문장 일치도를 평가 수치로 쓰지 않도록 저장하지 않는다(단어 비교는 선생님 참고용으로만 보관).
            int updated = mapper.completeLocalAnalysis(analysisId, transcript, result.confidence(),
                    therapy ? null : comparison.matchRate(), jsonMapper.writeValueAsString(comparison.words()),
                    PRONUNCIATION_NOT_EVALUATED, therapy ? REVIEW_PENDING : REVIEW_NOT_REQUIRED,
                    result.engineName(), result.modelName());
            if (updated != 1) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "음성 분석 결과를 저장하지 못했습니다.");
            // 일반 연습 녹음은 선생님 검토 대상이 아니므로 인식 직후 삭제한다(아동 음성 최소 보관).
            if (!therapy) discardAudio(analysisId, stored);
            return Map.of("analysisId", analysisId, "status", "COMPLETED");
        } catch (RuntimeException error) {
            mapper.failAnalysis(analysisId, error instanceof ResponseStatusException status ? status.getStatusCode().toString() : "ANALYSIS_ERROR");
            discardAudio(analysisId, stored);
            throw error;
        }
    }

    /** 기존 외부 음성 분석 제공자 연동(SPEECH_ENGINE=external). 제공자가 반환한 점수만 저장한다. */
    private Map<String,Object> analyzeWithExternalProvider(long studentId, long exerciseId, long itemId, MultipartFile audio) {
        externalProvider.requireSpeechProvider();
        AudioStorageService.StoredAudio stored = audioStorage.store(audio);
        String analysisId = UUID.randomUUID().toString();
        String item = Long.toString(itemId);
        mapper.insertAnalysis(analysisId, studentId, exerciseId, item, stored.path(), stored.mimeType(), "EXTERNAL_PROVIDER", null, "external", null, retention.retentionMonths());
        try {
            SpeechProviderResponse result = externalProvider.analyzeAudio(stored.path(), stored.mimeType(), Long.toString(exerciseId), item);
            if (mapper.completeAnalysis(analysisId, result.pronunciationScore(), result.speechRateScore(), result.fluencyScore(),
                    result.overallScore(), result.transcript(), result.feedback()) != 1)
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "음성 분석 결과를 저장하지 못했습니다.");
            return Map.of("analysisId", analysisId, "status", "COMPLETED");
        } catch (RuntimeException error) {
            mapper.failAnalysis(analysisId, error instanceof ResponseStatusException status ? status.getStatusCode().toString() : "ANALYSIS_ERROR");
            throw error;
        }
    }

    @Override
    public Map<String,Object> findAnalysis(long studentId, String analysisId) {
        Map<String,Object> analysis = mapper.findAnalysis(studentId, analysisId);
        if (analysis == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다.");
        return toResponse(analysis);
    }

    @Override
    public Map<String,Object> toResponse(Map<String,Object> row) {
        Map<String,Object> response = new LinkedHashMap<>(row);
        Object json = response.remove("comparisonJson");
        Object words = null;
        if (json instanceof String text && !text.isBlank()) {
            try { words = jsonMapper.readValue(text, List.class); }
            catch (RuntimeException ignored) { words = null; }
        }
        response.put("comparison", words == null ? null : Map.of("words", words));
        if (response.get("hasAudio") instanceof Number flag) response.put("hasAudio", flag.intValue() != 0);
        response.remove("audioPath");
        return response;
    }

    static boolean isHallucination(String transcript, String targetText) {
        String target = targetText == null ? "" : targetText;
        return HALLUCINATION_PHRASES.stream().anyMatch(phrase -> transcript.contains(phrase) && !target.contains(phrase));
    }

    private void discardAudio(String analysisId, AudioStorageService.StoredAudio stored) {
        audioStorage.discard(stored);
        mapper.markAudioDeleted(analysisId);
    }
}
