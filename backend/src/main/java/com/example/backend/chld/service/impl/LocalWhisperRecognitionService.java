package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import com.example.backend.chld.dto.response.SpeechRecognitionResult.SpeechSegment;
import com.example.backend.chld.service.SpeechRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * whisper.cpp(whisper-cli)와 공개 Whisper 가중치(ggml)로 서버 로컬에서 한국어 음성을 인식한다.
 * 외부 API로 음성을 전송하지 않는다.
 * VAD 모델(Silero, whisper-vad-speech-segments)이 있으면 인식 전에 말소리가 있는지 먼저 확인한다.
 * 말소리가 없는 녹음(배경 소음)에서 Whisper가 "감사합니다" 같은 문장을 만들어내는 것을 막기 위함이며,
 * 인식 자체는 VAD로 자르지 않은 전체 녹음으로 수행한다(짧은 낱말의 첫소리가 잘리지 않도록).
 */
@Slf4j
@Service
public class LocalWhisperRecognitionService implements SpeechRecognitionService {
    static final String ENGINE_NAME = "whisper.cpp";
    private static final long PADDING_MS = 1000;
    private static final int SILENCE_PEAK = 300;
    private static final Pattern VAD_SEGMENTS = Pattern.compile("Detected (\\d+) speech segments");
    private static final Pattern VAD_SEGMENT = Pattern.compile("Speech segment \\d+: start = ([0-9.]+), end = ([0-9.]+)");

    private final String cliPath;
    private final Path modelPath;
    private final String language;
    private final int threads;
    private final Duration timeout;
    private final long minDurationMs;
    private final long maxDurationMs;
    private final Semaphore permits;
    private final String vadCliPath;
    private final Path vadModelPath;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public LocalWhisperRecognitionService(
            @Value("${app.speech.local.cli-path:whisper-cli}") String cliPath,
            @Value("${app.speech.local.model-path:}") String modelPath,
            @Value("${app.speech.local.language:ko}") String language,
            @Value("${app.speech.local.threads:4}") int threads,
            @Value("${app.speech.local.timeout:60s}") Duration timeout,
            @Value("${app.speech.local.min-duration-ms:300}") long minDurationMs,
            @Value("${app.speech.local.max-duration-ms:30000}") long maxDurationMs,
            @Value("${app.speech.local.max-concurrency:1}") int maxConcurrency,
            @Value("${app.speech.local.vad-cli-path:whisper-vad-speech-segments}") String vadCliPath,
            @Value("${app.speech.local.vad-model-path:}") String vadModelPath) {
        this.cliPath = cliPath;
        this.modelPath = modelPath == null || modelPath.isBlank() ? null : Path.of(modelPath).toAbsolutePath().normalize();
        this.language = language; this.threads = Math.max(1, threads); this.timeout = timeout;
        this.minDurationMs = minDurationMs; this.maxDurationMs = maxDurationMs;
        this.permits = new Semaphore(Math.max(1, maxConcurrency));
        this.vadCliPath = vadCliPath;
        this.vadModelPath = vadModelPath == null || vadModelPath.isBlank() ? null : Path.of(vadModelPath).toAbsolutePath().normalize();
    }

    @Override
    public void requireAvailable() {
        if (modelPath == null || !Files.isReadable(modelPath))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "로컬 음성 인식 모델(WHISPER_MODEL_PATH) 설정이 필요합니다.");
        if (resolveExecutable(cliPath) == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "로컬 음성 인식 실행 파일(WHISPER_CLI_PATH) 설정이 필요합니다.");
    }

    /** VAD 실행 파일과 모델이 모두 있을 때만 말소리 확인을 한다. 없으면 기존처럼 바로 인식한다. */
    boolean vadEnabled() {
        return vadModelPath != null && Files.isReadable(vadModelPath) && resolveExecutable(vadCliPath) != null;
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
        // 처리 시간 측정(운영 임계값 검증용). 단계별 시간과 결과 코드만 기록하고 음성·인식 내용·경로·사용자 정보는 남기지 않는다.
        long requested = System.nanoTime();
        Long queueMs = null, vadMs = null, whisperMs = null;
        String vad = vadEnabled() ? "PENDING" : "OFF";
        String outcome = "ERROR";
        try {
            acquired = permits.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            queueMs = elapsedMs(requested);
            if (!acquired) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "분석 요청이 많아요. 잠시 뒤 다시 시도해 주세요.");
            workDir = Files.createTempDirectory("chatterland-asr");
            Path input = workDir.resolve("input.wav");
            audio.writePadded(input, PADDING_MS);
            Path outputBase = workDir.resolve("result");
            long started = System.nanoTime();
            List<SpeechSegment> segments = null;
            if (!"OFF".equals(vad)) {
                segments = detectSpeechSegments(input, durationMs);
                vadMs = elapsedMs(started);
                vad = segments == null ? "UNAVAILABLE" : "OK";
            }
            if (segments != null && segments.isEmpty())
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "목소리가 들리지 않았어요. 마이크 가까이에서 또렷하게 다시 말해 주세요.");
            long whisperStarted = System.nanoTime();
            try {
                runWhisper(input, outputBase);
            } finally {
                whisperMs = elapsedMs(whisperStarted);
            }
            long processingMs = (System.nanoTime() - started) / 1_000_000;
            SpeechRecognitionResult text = parseResult(workDir.resolve("result.json"), durationMs, processingMs);
            outcome = "OK";
            return new SpeechRecognitionResult(text.transcript(), text.confidence(), text.engineName(), text.modelName(),
                    durationMs, processingMs, segments, audio.peakAmplitude(), audio.clippedRatio());
        } catch (ResponseStatusException e) {
            outcome = Integer.toString(e.getStatusCode().value());
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "503";
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "음성 인식이 중단되었습니다.");
        } catch (IOException e) {
            outcome = "500";
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "음성 인식 작업 파일을 준비하지 못했습니다.");
        } finally {
            if (acquired) permits.release();
            deleteQuietly(workDir);
            log.info("speech.timing stage=recognize outcome={} totalMs={} queueMs={} vad={} vadMs={} whisperMs={} audioMs={} timeoutMs={}",
                    outcome, elapsedMs(requested), dash(queueMs), vad, dash(vadMs), dash(whisperMs), durationMs, timeout.toMillis());
        }
    }

    private static long elapsedMs(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000; }
    private static String dash(Long value) { return value == null ? "-" : value.toString(); }

    /**
     * Silero VAD로 말소리 구간을 찾는다(출력에는 구간 시각만 있고 인식 내용은 없다).
     * 앞뒤에 붙인 무음 패딩을 빼고 원본 녹음 기준 ms로 돌려준다. 확인할 수 없으면 null을 돌려주고 인식을 계속한다.
     */
    List<SpeechSegment> detectSpeechSegments(Path input, long audioDurationMs) throws IOException, InterruptedException {
        List<String> command = List.of(resolveExecutable(vadCliPath), "-np", "-t", Integer.toString(threads),
                "-vm", vadModelPath.toString(), "-f", input.toString());
        // 출력은 파일로 받는다. 표준출력을 직접 끝까지 읽으면 프로세스가 멈췄을 때 시간 제한을 검사하기 전에 영원히 막힌다.
        Path output = input.resolveSibling("vad-output.txt");
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(output.toFile()).start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return null;
        }
        if (process.exitValue() != 0 || !Files.isReadable(output)) return null;
        return parseSpeechSegments(Files.readString(output, StandardCharsets.UTF_8), audioDurationMs);
    }

    /** "Speech segment 0: start = 77.00, end = 157.00"(센티초) 형식을 원본 녹음 기준 ms 구간으로 바꾼다. */
    static List<SpeechSegment> parseSpeechSegments(String output, long audioDurationMs) {
        Matcher count = VAD_SEGMENTS.matcher(output);
        if (!count.find()) return null;
        List<SpeechSegment> segments = new ArrayList<>();
        Matcher segment = VAD_SEGMENT.matcher(output);
        while (segment.find()) {
            long start = Math.round(Double.parseDouble(segment.group(1)) * 10) - PADDING_MS;
            long end = Math.round(Double.parseDouble(segment.group(2)) * 10) - PADDING_MS;
            start = Math.max(0, Math.min(audioDurationMs, start));
            end = Math.max(0, Math.min(audioDurationMs, end));
            if (end > start) segments.add(new SpeechSegment(start, end));
        }
        return segments;
    }

    private void runWhisper(Path input, Path outputBase) throws IOException, InterruptedException {
        List<String> command = List.of(resolveExecutable(cliPath), "-m", modelPath.toString(), "-f", input.toString(),
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
        return new SpeechRecognitionResult(text.toString().trim(), confidence, ENGINE_NAME, modelName(), durationMs, processingMs, null, 0, 0d);
    }

    private static String resolveExecutable(String cliPath) {
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
