package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import com.example.backend.chld.service.SpeechRecognitionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * whisper.cpp(whisper-cli)와 공개 Whisper 가중치(ggml)로 서버 로컬에서 한국어 음성을 인식한다.
 * 외부 API로 음성을 전송하지 않는다.
 */
@Service
public class LocalWhisperRecognitionService implements SpeechRecognitionService {
    static final String ENGINE_NAME = "whisper.cpp";
    private static final long PADDING_MS = 1000;
    private static final int SILENCE_PEAK = 300;

    private final String cliPath;
    private final Path modelPath;
    private final String language;
    private final int threads;
    private final Duration timeout;
    private final long minDurationMs;
    private final long maxDurationMs;
    private final Semaphore permits;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public LocalWhisperRecognitionService(
            @Value("${app.speech.local.cli-path:whisper-cli}") String cliPath,
            @Value("${app.speech.local.model-path:}") String modelPath,
            @Value("${app.speech.local.language:ko}") String language,
            @Value("${app.speech.local.threads:4}") int threads,
            @Value("${app.speech.local.timeout:60s}") Duration timeout,
            @Value("${app.speech.local.min-duration-ms:300}") long minDurationMs,
            @Value("${app.speech.local.max-duration-ms:30000}") long maxDurationMs,
            @Value("${app.speech.local.max-concurrency:1}") int maxConcurrency) {
        this.cliPath = cliPath;
        this.modelPath = modelPath == null || modelPath.isBlank() ? null : Path.of(modelPath).toAbsolutePath().normalize();
        this.language = language; this.threads = Math.max(1, threads); this.timeout = timeout;
        this.minDurationMs = minDurationMs; this.maxDurationMs = maxDurationMs;
        this.permits = new Semaphore(Math.max(1, maxConcurrency));
    }

    @Override
    public void requireAvailable() {
        if (modelPath == null || !Files.isReadable(modelPath))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "로컬 음성 인식 모델(WHISPER_MODEL_PATH) 설정이 필요합니다.");
        if (resolveCli() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "로컬 음성 인식 실행 파일(WHISPER_CLI_PATH) 설정이 필요합니다.");
    }

    String modelName() {
        return modelPath == null ? "" : modelPath.getFileName().toString();
    }

    @Override
    public SpeechRecognitionResult recognize(Path audioPath) {
        requireAvailable();
        WavAudio audio = WavAudio.read(audioPath);
        long durationMs = audio.durationMs();
        if (durationMs < minDurationMs)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "녹음이 너무 짧아요. 버튼을 누르고 조금 더 길게 말해 주세요.");
        if (durationMs > maxDurationMs)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "녹음은 " + (maxDurationMs / 1000) + "초 이하로 해 주세요.");
        if (audio.peakAmplitude() < SILENCE_PEAK)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "목소리가 녹음되지 않았어요. 마이크를 확인하고 다시 말해 주세요.");

        boolean acquired = false;
        Path workDir = null;
        try {
            acquired = permits.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "분석 요청이 많아요. 잠시 뒤 다시 시도해 주세요.");
            workDir = Files.createTempDirectory("chatterland-asr");
            Path input = workDir.resolve("input.wav");
            audio.writePadded(input, PADDING_MS);
            Path outputBase = workDir.resolve("result");
            long started = System.nanoTime();
            runWhisper(input, outputBase);
            long processingMs = (System.nanoTime() - started) / 1_000_000;
            return parseResult(workDir.resolve("result.json"), durationMs, processingMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "음성 인식이 중단되었습니다.");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "음성 인식 작업 파일을 준비하지 못했습니다.");
        } finally {
            if (acquired) permits.release();
            deleteQuietly(workDir);
        }
    }

    private void runWhisper(Path input, Path outputBase) throws IOException, InterruptedException {
        List<String> command = List.of(resolveCli(), "-m", modelPath.toString(), "-f", input.toString(),
                "-l", language, "-t", Integer.toString(threads), "-np", "-nt", "-ojf", "-of", outputBase.toString());
        // 음성·인식 내용이 서버 로그에 남지 않도록 프로세스 출력은 버린다.
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "음성 인식 시간이 초과되었습니다. 잠시 뒤 다시 시도해 주세요.");
        }
        if (process.exitValue() != 0)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "로컬 음성 인식 실행에 실패했습니다.");
    }

    SpeechRecognitionResult parseResult(Path jsonPath, long durationMs, long processingMs) {
        if (!Files.isReadable(jsonPath)) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "음성 인식 결과 파일을 찾지 못했습니다.");
        JsonNode root;
        try { root = jsonMapper.readTree(jsonPath.toFile()); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "음성 인식 결과 형식이 올바르지 않습니다."); }
        JsonNode segments = root.path("transcription");
        if (!segments.isArray()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "음성 인식 결과 형식이 올바르지 않습니다.");
        StringBuilder text = new StringBuilder();
        List<Double> probabilities = new ArrayList<>();
        for (JsonNode segment : segments) {
            text.append(segment.path("text").asString(""));
            for (JsonNode token : segment.path("tokens")) {
                String tokenText = token.path("text").asString("");
                if (tokenText.startsWith("[_") || !token.has("p")) continue;
                probabilities.add(token.path("p").asDouble());
            }
        }
        BigDecimal confidence = probabilities.isEmpty() ? null
                : BigDecimal.valueOf(probabilities.stream().mapToDouble(Double::doubleValue).average().orElse(0)).setScale(4, RoundingMode.HALF_UP);
        return new SpeechRecognitionResult(text.toString().trim(), confidence, ENGINE_NAME, modelName(), durationMs, processingMs);
    }

    private String resolveCli() {
        if (cliPath == null || cliPath.isBlank()) return null;
        if (cliPath.contains(File.separator)) return Files.isExecutable(Path.of(cliPath)) ? cliPath : null;
        List<String> directories = new ArrayList<>();
        String envPath = System.getenv("PATH");
        if (envPath != null) directories.addAll(List.of(envPath.split(File.pathSeparator)));
        directories.addAll(List.of("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"));
        for (String directory : directories) {
            Path candidate = Path.of(directory, cliPath);
            if (Files.isExecutable(candidate)) return candidate.toString();
        }
        return null;
    }

    private void deleteQuietly(Path directory) {
        if (directory == null) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { /* 임시 파일 정리는 최선 노력 */ }
            });
        } catch (IOException ignored) { /* 임시 파일 정리는 최선 노력 */ }
    }
}
