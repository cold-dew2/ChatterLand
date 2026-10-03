package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment;
import com.example.backend.chld.dto.response.SpeechProviderResponse;
import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import com.example.backend.chld.dto.response.TranscriptComparison;
import com.example.backend.chld.mapper.StudentMapper;
import com.example.backend.chld.service.AudioRetentionService;
import com.example.backend.chld.service.SpeechAnalysisService;
import com.example.backend.chld.service.SpeechRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 연습 녹음 분석.
 * - 일반 아동(SENTENCE_MATCH): 로컬 음성 인식 결과와 목표 문장의 텍스트 일치율을 계산한다. 발음 점수는 만들지 않는다.
 * - 언어재활 아동(PRONUNCIATION_REVIEW): 검증된 발음 평가 방법이 없으므로 발음 평가는 미평가로 두고 선생님 검토 대기로 저장한다.
 * 두 경우 모두 단어(WORD)/문장(SENTENCE) 유형별 자동 분석 근거(목표 음소 위치·오류 후보·발화 시간·반복 비교)와
 * 판정 보류(HOLD) 여부를 함께 저장한다. 교사 확정 결과는 별도 컬럼(teacher_confirmed_json)에 저장한다.
 */
@Slf4j
@Service
public class SpeechAnalysisServiceImpl implements SpeechAnalysisService {
    static final String MODE_SENTENCE_MATCH = "SENTENCE_MATCH";
    static final String MODE_PRONUNCIATION_REVIEW = "PRONUNCIATION_REVIEW";
    static final String PRONUNCIATION_NOT_EVALUATED = "NOT_EVALUATED";
    static final String REVIEW_NOT_REQUIRED = "NOT_REQUIRED";
    static final String REVIEW_PENDING = "PENDING";
    static final String STALE_ERROR_CODE = "STALE_PROCESSING";

    /** 짧은/빈 녹음에서 Whisper 계열 모델이 만들어내는 대표적인 환각 문장 */
    private static final List<String> HALLUCINATION_PHRASES = List.of(
            "구독과 좋아요", "구독 좋아요", "시청해 주셔서", "시청해주셔서", "끝까지 시청", "자막 제공", "MBC 뉴스", "도움이 되었습니다", "-끝-",
            "한글자막", "자막 by", "광고를 포함", "다음 영상에서", "뉴스 스토리였습니다", "이 시각 세계였습니다");

    private final StudentMapper mapper;
    private final SpeechRecognitionService recognizer;
    private final TranscriptComparator comparator;
    private final AudioStorageService audioStorage;
    private final ExternalProviderService externalProvider;
    private final AudioRetentionService retention;
    private final SpeechAssessmentEvaluator evaluator;
    private final String engine;
    /** 이 시간보다 오래 PROCESSING이면 끝나지 못한 분석으로 본다(정상 처리 최대 시간보다 길게 보정). */
    private final long staleAfterSeconds;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public SpeechAnalysisServiceImpl(StudentMapper mapper, SpeechRecognitionService recognizer, TranscriptComparator comparator,
                                     AudioStorageService audioStorage, ExternalProviderService externalProvider,
                                     AudioRetentionService retention, SpeechAssessmentEvaluator evaluator,
                                     @Value("${app.speech.engine:local}") String engine,
                                     @Value("${app.speech.stale-processing-after:PT5M}") java.time.Duration staleProcessingAfter,
                                     @Value("${app.speech.local.timeout:60s}") java.time.Duration recognitionTimeout) {
        this.mapper = mapper; this.recognizer = recognizer; this.comparator = comparator;
        this.audioStorage = audioStorage; this.externalProvider = externalProvider; this.retention = retention; this.evaluator = evaluator;
        this.engine = engine == null ? "local" : engine.trim().toLowerCase(Locale.ROOT);
        this.staleAfterSeconds = staleThresholdSeconds(staleProcessingAfter, recognitionTimeout);
    }

    /**
     * 정상 분석의 최대 소요 시간: 분석 슬롯 대기·VAD·Whisper가 각각 최대 recognitionTimeout → 3배.
     * 설정값이 그보다 짧으면 정상 처리 중인 분석을 끝난 것으로 오판하므로 3배 + 60초 이상으로 보정한다.
     */
    static long staleThresholdSeconds(java.time.Duration configured, java.time.Duration recognitionTimeout) {
        long minimum = recognitionTimeout.toSeconds() * 3 + 60;
        return Math.max(configured.toSeconds(), minimum);
    }

    @Override
    public int recoverStaleAnalyses() {
        int recovered = 0;
        for (Map<String,Object> row : mapper.findStaleProcessingAnalyses(staleAfterSeconds, 100))
            if (failStale(String.valueOf(row.get("analysisId")), (String) row.get("audioPath"))) recovered++;
        return recovered;
    }

    /** 조건부로 실패 처리하고, 성공했을 때만 그 분석의 녹음을 지운다(다른 요청이 먼저 처리했으면 아무것도 하지 않는다). */
    private boolean failStale(String analysisId, String audioPath) {
        try {
            if (mapper.failStaleAnalysis(analysisId, staleAfterSeconds) != 1) return false;
        } catch (PessimisticLockingFailureException lockLost) {
            // 같은 키의 동시 재시도와 잠금이 엇갈려 이 문장이 되돌려졌다: 다른 요청이 정리한 것으로 보고 다시 조회한다.
            return false;
        }
        if (audioPath != null) {
            audioStorage.deleteStoredPath(audioPath);
            mapper.markAudioDeleted(analysisId);
        }
        return true;
    }

    @Override
    public Map<String,Object> analyze(long studentId, String learnerType, long exerciseId, long itemId, String targetText,
                                      String targetPhonemes, MultipartFile audio, String requestKey) {
        String key = normalizeRequestKey(requestKey);
        String hash = key == null ? null : requestHash(audio, exerciseId, itemId);
        // 같은 녹음의 재시도(응답 유실·다시 분석하기)는 새로 분석하지 않고 이전 분석을 돌려준다.
        Map<String,Object> reused = existingForKey(studentId, key, hash);
        if (reused != null) return reused;
        if ("external".equals(engine)) return analyzeWithExternalProvider(studentId, exerciseId, itemId, audio, key, hash);
        recognizer.requireAvailable();
        String mode = LEARNER_THERAPY.equals(learnerType) ? MODE_PRONUNCIATION_REVIEW : MODE_SENTENCE_MATCH;
        AudioStorageService.StoredAudio stored = audioStorage.store(audio);
        String analysisId = UUID.randomUUID().toString();
        Map<String,Object> concurrent = insertOrReuse(stored, studentId, key, hash, () -> mapper.insertAnalysis(analysisId, studentId, exerciseId,
                Long.toString(itemId), stored.path(), stored.mimeType(), mode, targetText, LocalWhisperRecognitionService.ENGINE_NAME, null,
                retention.retentionMonths(), key, hash));
        if (concurrent != null) return concurrent;
        // 분석 행(PROCESSING) 생성부터 최종 상태까지의 시간. 고착 판정 기준(staleAfterSeconds)과 비교하는 운영 지표로 쓴다.
        long started = System.nanoTime();
        String outcome = "FAILED";
        try {
            SpeechRecognitionResult result = recognizer.recognize(Path.of(stored.path()));
            String transcript = result.transcript();
            if (transcript.isBlank() || isHallucination(transcript, targetText))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "목소리를 알아듣지 못했어요. 조금 더 크고 또렷하게 다시 말해 주세요.");
            TranscriptComparison comparison = comparator.compare(targetText, transcript);
            SpeechAssessment assessment = evaluator.evaluate(targetText, transcript, result, targetPhonemes,
                    previousAnalyses(studentId, exerciseId, Long.toString(itemId), analysisId));
            boolean therapy = MODE_PRONUNCIATION_REVIEW.equals(mode);
            // 언어재활 모드에서는 문장 일치도를 평가 수치로 쓰지 않도록 저장하지 않는다(단어 비교는 선생님 참고용으로만 보관).
            int updated = mapper.completeLocalAnalysis(analysisId, transcript, result.confidence(),
                    therapy ? null : comparison.matchRate(), jsonMapper.writeValueAsString(comparison.words()),
                    PRONUNCIATION_NOT_EVALUATED, therapy ? REVIEW_PENDING : REVIEW_NOT_REQUIRED,
                    result.engineName(), result.modelName(), assessment.analysisType(), assessment.assessmentStatus(),
                    jsonMapper.writeValueAsString(assessment), SpeechAssessmentEvaluator.VERSION);
            if (updated != 1) {
                // 처리가 기준 시간보다 오래 걸려 이미 STALE_PROCESSING으로 정리된 경우(정상 작업의 오탐 실패)를 따로 집계한다.
                if (STALE_ERROR_CODE.equals(mapper.findAnalysisErrorCode(analysisId))) outcome = "LATE_AFTER_STALE";
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "음성 분석 결과를 저장하지 못했습니다.");
            }
            // 일반 연습 녹음은 선생님 검토 대상이 아니므로 인식 직후 삭제한다(아동 음성 최소 보관).
            if (!therapy) discardAudio(analysisId, stored);
            outcome = "COMPLETED";
            return Map.of("analysisId", analysisId, "status", "COMPLETED");
        } catch (RuntimeException error) {
            String code = error instanceof ResponseStatusException status ? status.getStatusCode().toString() : "ANALYSIS_ERROR";
            mapper.failAnalysis(analysisId, code);
            discardAudio(analysisId, stored);
            if ("FAILED".equals(outcome)) outcome = "FAILED:" + code.split(" ")[0];
            throw error;
        } finally {
            long totalMs = (System.nanoTime() - started) / 1_000_000;
            if ("LATE_AFTER_STALE".equals(outcome))
                log.warn("speech.timing stage=analysis outcome={} totalMs={} staleAfterMs={}", outcome, totalMs, staleAfterSeconds * 1000);
            else
                log.info("speech.timing stage=analysis outcome={} totalMs={} staleAfterMs={}", outcome, totalMs, staleAfterSeconds * 1000);
        }
    }

    /** 기존 외부 음성 분석 제공자 연동(SPEECH_ENGINE=external). 제공자가 반환한 점수만 저장한다. */
    private Map<String,Object> analyzeWithExternalProvider(long studentId, long exerciseId, long itemId, MultipartFile audio, String key, String hash) {
        externalProvider.requireSpeechProvider();
        AudioStorageService.StoredAudio stored = audioStorage.store(audio);
        String analysisId = UUID.randomUUID().toString();
        String item = Long.toString(itemId);
        Map<String,Object> concurrent = insertOrReuse(stored, studentId, key, hash, () -> mapper.insertAnalysis(analysisId, studentId, exerciseId,
                item, stored.path(), stored.mimeType(), "EXTERNAL_PROVIDER", null, "external", null, retention.retentionMonths(), key, hash));
        if (concurrent != null) return concurrent;
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
        // 텍스트 일치율(음성 인식 텍스트와 목표 텍스트의 음절 일치 정도). 발음 점수가 아니다. matchRate는 기존 계약 호환용이다.
        response.put("textMatchRate", response.get("matchRate"));
        Map<?,?> assessment = readJson(response.remove("assessmentJson"), Map.class);
        response.put("holdReasons", assessment == null ? List.of() : assessment.get("holdReasons"));
        response.put("assessmentBasis", assessment == null ? null : assessment.get("basis"));
        response.put("targetPhonemes", assessment == null ? List.of() : assessment.get("targetPhonemes"));
        response.put("targetPositions", assessment == null ? List.of() : assessment.get("targetPositions"));
        response.put("phonemeCandidates", assessment == null ? List.of() : assessment.get("candidates"));
        response.put("speechTiming", assessment == null ? null : assessment.get("timing"));
        response.put("repetition", assessment == null ? null : assessment.get("repetition"));
        response.put("teacherConfirmedErrors", readJson(response.remove("teacherConfirmedJson"), List.class));
        if (response.get("hasAudio") instanceof Number flag) response.put("hasAudio", flag.intValue() != 0);
        response.remove("audioPath");
        return response;
    }

    static String normalizeRequestKey(String requestKey) {
        return IdempotencyKeys.normalize(requestKey);
    }

    /** 같은 키로 다른 녹음·문항이 오면 거절하기 위한 요청 내용 지문(녹음 바이트 + 문항) */
    static String requestHash(MultipartFile audio, long exerciseId, long itemId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(audio.getBytes());
            digest.update(("|" + exerciseId + "|" + itemId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "녹음 파일을 읽지 못했습니다.");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 같은 학생·같은 키의 분석(처리 중 또는 완료)이 있으면 그 분석을 돌려준다. 실패한 분석은 키가 비워져 있어 다시 분석된다. */
    private Map<String,Object> existingForKey(long studentId, String key, String hash) {
        if (key == null) return null;
        Map<String,Object> existing = mapper.findAnalysisByRequestKey(studentId, key);
        if (existing == null) return null;
        if ("PROCESSING".equals(existing.get("status")) && existing.get("ageSeconds") instanceof Number age && age.longValue() >= staleAfterSeconds) {
            // 끝나지 못한 분석(서버 종료 등): 실패로 정리하고 새로 분석한다. 그 사이 다른 요청이 처리했으면 다시 조회한다.
            if (failStale(String.valueOf(existing.get("analysisId")), (String) existing.get("audioPath"))) return null;
            existing = mapper.findAnalysisByRequestKey(studentId, key);
            if (existing == null) return null;
        }
        if (!Objects.equals(existing.get("requestHash"), hash))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 요청 키로 다른 녹음이 전송되었습니다. 새로 녹음한 뒤 다시 분석해 주세요.");
        return Map.of("analysisId", existing.get("analysisId"), "status", existing.get("status"), "reused", true);
    }

    /**
     * 분석 행을 만든다. 같은 키의 요청이 동시에 들어와 (student_id, request_key) 유니크 제약에 걸리면
     * 먼저 만들어진 분석을 돌려주고, 이 요청이 저장한 녹음은 지운다(추론을 두 번 하지 않는다).
     */
    private Map<String,Object> insertOrReuse(AudioStorageService.StoredAudio stored, long studentId, String key, String hash, Runnable insert) {
        for (int attempt = 1; ; attempt++) {
            try {
                insert.run();
                return null;
            } catch (DuplicateKeyException duplicate) {
                audioStorage.discard(stored);
                Map<String,Object> existing = existingForKey(studentId, key, hash);
                if (existing == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 녹음을 이미 분석하고 있어요. 잠시 뒤 결과를 확인해 주세요.");
                return existing;
            } catch (PessimisticLockingFailureException lockLost) {
                // 고착 분석 정리(키 비우기 UPDATE)와 같은 키의 INSERT가 동시에 같은 유니크 인덱스 범위를 잠그면
                // InnoDB가 교착 상태로 한 문장을 되돌린다(DEF-11). 그 사이 만들어진 분석이 있으면 돌려주고, 없으면 다시 시도한다.
                Map<String,Object> existing = key == null ? null : existingForKey(studentId, key, hash);
                if (existing != null || attempt >= 3) {
                    audioStorage.discard(stored);
                    if (existing != null) return existing;
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "분석 요청이 겹쳤어요. 잠시 뒤 다시 시도해 주세요.");
                }
            } catch (RuntimeException error) {
                audioStorage.discard(stored);
                throw error;
            }
        }
    }

    @Override
    public String toJson(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    private <T> T readJson(Object json, Class<T> type) {
        if (!(json instanceof String text) || text.isBlank()) return null;
        try { return jsonMapper.readValue(text, type); }
        catch (RuntimeException ignored) { return null; }
    }

    /** 같은 문항의 최근 완료 분석 5건(반복 발화 비교용) */
    private List<SpeechAssessmentEvaluator.PreviousAnalysis> previousAnalyses(long studentId, long exerciseId, String itemId, String currentId) {
        List<SpeechAssessmentEvaluator.PreviousAnalysis> previous = new ArrayList<>();
        for (Map<String,Object> row : mapper.findRecentItemAnalyses(studentId, exerciseId, itemId, currentId, 5)) {
            List<String> signatures = new ArrayList<>();
            Map<?,?> assessment = readJson(row.get("assessmentJson"), Map.class);
            if (assessment != null && assessment.get("candidates") instanceof List<?> candidates) {
                for (Object candidate : candidates) {
                    if (candidate instanceof Map<?,?> c)
                        signatures.add(c.get("type") + ":" + c.get("slot") + ":" + (c.get("expected") == null ? "-" : c.get("expected"))
                                + ">" + (c.get("produced") == null ? "-" : c.get("produced")));
                }
            }
            previous.add(new SpeechAssessmentEvaluator.PreviousAnalysis(String.valueOf(row.get("createdAt")), (String) row.get("transcript"),
                    (BigDecimal) row.get("matchRate"), (String) row.get("assessmentStatus"), signatures));
        }
        return previous;
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
