package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment;
import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 말하기 측정값 검증 실행(일반 테스트에 포함하지 않음): sh gradlew speechValidation (SPEECH_VALIDATION_MANIFEST=매니페스트.tsv 필수)
 * - 운영과 같은 단계로 측정한다: whisper.cpp 인식 → 빈/환각 인식은 분석 실패 → SpeechAssessmentEvaluator → 기준 정답과 비교.
 * - 모두 서버 로컬에서 실행한다(외부 전송 없음). DB를 쓰지 않는다.
 * - 녹음은 SPEECH_VALIDATION_AUDIO_ROOT(없으면 매니페스트 폴더) 안의 파일만 쓴다. 서비스 녹음 저장소(storage/audio,
 *   AUDIO_STORAGE_PATH)와 E2E 녹음 저장소(build/test-db/audio)의 파일은 거부한다. 입력 오류가 하나라도 있으면 측정하지 않는다.
 * - 결과는 build/speech-validation/<시각>/ 에 results.tsv·summary.md로만 쓴다(이미 있으면 새 폴더, 덮어쓰지 않음).
 *   녹음·매니페스트는 읽기만 하고 복사·수정하지 않으며, 내용 대신 SHA-256만 기록한다.
 *   인식 텍스트는 SPEECH_VALIDATION_INCLUDE_TRANSCRIPT=1 일 때만 기록한다(아동 발화 내용 최소 보관).
 * 실제 아동 음성은 사용 권한·보호자 동의·보관/파기 절차가 확인된 데이터만 매니페스트에 넣는다(docs/design/speech-metrics.md 8장).
 */
class SpeechValidationRun {
    static final String MODEL = System.getenv().getOrDefault("WHISPER_MODEL_PATH",
            System.getProperty("user.home") + "/.cache/chatterland/whisper/ggml-large-v3-turbo-q5_0.bin");
    static final String VAD_MODEL = System.getenv().getOrDefault("WHISPER_VAD_MODEL_PATH",
            System.getProperty("user.home") + "/.cache/chatterland/whisper/ggml-silero-v5.1.2.bin");

    @Test
    void runValidation() throws Exception {
        String manifest = System.getenv("SPEECH_VALIDATION_MANIFEST");
        assertTrue(manifest != null && !manifest.isBlank(), "SPEECH_VALIDATION_MANIFEST(매니페스트 TSV 경로)를 지정해야 합니다.");
        LocalWhisperRecognitionService recognizer = recognizer();
        assertTrue(recognizer.vadEnabled(), "VAD 모델·실행 파일이 없어 운영과 같은 조건으로 측정할 수 없습니다.");
        Path manifestPath = Path.of(manifest).toAbsolutePath().normalize();
        String rootSetting = System.getenv("SPEECH_VALIDATION_AUDIO_ROOT");
        Path audioRoot = rootSetting == null || rootSetting.isBlank() ? manifestPath.getParent() : Path.of(rootSetting);
        List<SpeechValidation.Recording> recordings = SpeechValidation.readManifest(manifestPath, audioRoot);
        SpeechValidation.checkAudio(recordings, audioRoot, deniedAudioRoots());
        List<SpeechValidation.Row> rows = measure(recordings, recognizer, SpeechAssessmentEvaluator.PAUSE_THRESHOLD_MS);
        Path out = newOutputDirectory(Path.of("build", "speech-validation"));
        write(out, manifestPath, recognizer, rows, recordings, "1".equals(System.getenv("SPEECH_VALIDATION_INCLUDE_TRANSCRIPT")));
        System.out.println("[speech-validation] " + rows.size() + "건 → " + out.toAbsolutePath());
    }

    /** 검증에 쓰면 안 되는 녹음 위치: 서비스 녹음 저장소(출처·동의 기록 없음), E2E 테스트 녹음 */
    static List<Path> deniedAudioRoots() {
        List<Path> denied = new ArrayList<>(List.of(Path.of("storage", "audio"), Path.of("build", "test-db", "audio")));
        String configured = System.getenv("AUDIO_STORAGE_PATH");
        if (configured != null && !configured.isBlank()) denied.add(Path.of(configured));
        return denied;
    }

    /** 실행마다 새 결과 폴더(같은 초에 다시 실행해도 기존 결과를 덮어쓰지 않음) */
    static Path newOutputDirectory(Path parent) throws java.io.IOException {
        Files.createDirectories(parent);
        String base = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        for (int attempt = 0; ; attempt++) {
            Path candidate = parent.resolve(attempt == 0 ? base : base + "-" + attempt);
            try { return Files.createDirectory(candidate); }
            catch (java.nio.file.FileAlreadyExistsException exists) { /* 다음 이름 */ }
        }
    }

    static LocalWhisperRecognitionService recognizer() {
        return new LocalWhisperRecognitionService("whisper-cli", MODEL, "ko", 4, Duration.ofSeconds(120), 300, 30000, 1,
                "whisper-vad-speech-segments", VAD_MODEL);
    }

    /** 녹음마다 운영과 같은 단계로 측정하고 기준 정답(annotator=FINAL)과 비교한다 */
    static List<SpeechValidation.Row> measure(List<SpeechValidation.Recording> recordings, LocalWhisperRecognitionService recognizer, long pauseThresholdMs) {
        SpeechAssessmentEvaluator evaluator = new SpeechAssessmentEvaluator(new HangulPhonemeAnalyzer());
        HangulPhonemeAnalyzer phonemes = new HangulPhonemeAnalyzer();
        List<SpeechValidation.Row> rows = new ArrayList<>();
        for (SpeechValidation.Recording recording : recordings) {
            SpeechValidation.ReferenceMetrics reference = SpeechValidation.reference(recording.reference(), pauseThresholdMs);
            try {
                SpeechRecognitionResult result = recognizer.recognize(recording.audio());
                String transcript = result.transcript();
                if (transcript.isBlank() || SpeechAnalysisServiceImpl.isHallucination(transcript, recording.targetText())) {
                    rows.add(new SpeechValidation.Row(recording, "FAILED", "422 UNRECOGNIZED", transcript, null, null, null, reference));
                    continue;
                }
                SpeechAssessment assessment = evaluator.evaluate(recording.targetText(), transcript, result, null, List.of());
                rows.add(new SpeechValidation.Row(recording, "COMPLETED", null, transcript,
                        phonemes.syllableCount(TranscriptComparator.normalize(transcript)), assessment.metrics(), assessment.assessmentStatus(), reference));
            } catch (ResponseStatusException error) {
                rows.add(new SpeechValidation.Row(recording, "FAILED", error.getStatusCode().toString(), null, null, null, null, reference));
            }
        }
        return rows;
    }

    static void write(Path out, Path manifest, LocalWhisperRecognitionService recognizer, List<SpeechValidation.Row> rows,
                      List<SpeechValidation.Recording> recordings, boolean includeTranscript) throws Exception {
        Files.createDirectories(out);
        Map<String, String> audioHashes = new LinkedHashMap<>();
        for (SpeechValidation.Recording recording : recordings) audioHashes.put(recording.id(), SpeechValidation.sha256(recording.audio()));
        List<SpeechValidation.Agreement> agreements = new ArrayList<>();
        recordings.forEach(recording -> agreements.addAll(SpeechValidation.agreements(recording, SpeechAssessmentEvaluator.PAUSE_THRESHOLD_MS)));
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("매니페스트", manifest.getFileName() + " (SHA-256 " + SpeechValidation.sha256(manifest) + ")");
        settings.put("측정 버전", SpeechAssessmentEvaluator.METRICS_VERSION + " (멈춤 기준 " + SpeechAssessmentEvaluator.PAUSE_THRESHOLD_MS + "ms, 속도 최소 "
                + SpeechAssessmentEvaluator.MIN_RATE_SYLLABLES + "음절·" + SpeechAssessmentEvaluator.MIN_RATE_UTTERANCE_MS + "ms)");
        settings.put("인식 모델", recognizer.modelName() + " / VAD " + Path.of(VAD_MODEL).getFileName());
        settings.put("기준 정답 멈춤 정의", "전사자가 표시한 말소리 구간 사이 무음 ≥ " + SpeechAssessmentEvaluator.PAUSE_THRESHOLD_MS + "ms(시스템과 같은 정의)");
        Files.writeString(out.resolve("results.tsv"), SpeechValidation.resultsTsv(rows, includeTranscript, audioHashes), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("summary.md"), SpeechValidation.summaryMarkdown("말하기 측정값 검증 결과", rows, agreements, settings), StandardCharsets.UTF_8);
    }
}
