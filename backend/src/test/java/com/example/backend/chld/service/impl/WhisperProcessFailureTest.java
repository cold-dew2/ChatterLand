package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * whisper-cli 프로세스 실패 처리. 실제 모델 대신 동작을 정한 대체 실행 파일(셸 스크립트)을 사용해
 * 실행 실패·시간 초과·결과 파일 없음/손상을 재현한다. 실패 시 가짜 인식 결과를 만들지 않는지 확인한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class WhisperProcessFailureTest {
    @TempDir Path dir;
    private Path model;
    private Path audio;

    @BeforeEach
    void setUp() throws Exception {
        model = Files.writeString(dir.resolve("fake-model.bin"), "not a real model");
        audio = Path.of(getClass().getResource("/speech/word-radio-recorded.wav").toURI());
    }

    private String script(String name, String body) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        return file.toString();
    }

    /** "-of <경로>" 인자를 찾아 <경로>.json을 쓰는 스크립트 본문 */
    private static String writeJson(String json) {
        return "while [ $# -gt 0 ]; do if [ \"$1\" = \"-of\" ]; then out=\"$2\"; fi; shift; done\nprintf '%s' '" + json + "' > \"$out.json\"";
    }

    private LocalWhisperRecognitionService service(String cli, Duration timeout) {
        return new LocalWhisperRecognitionService(cli, model.toString(), "ko", 1, timeout, 300, 30000, 1, "whisper-vad-speech-segments", "");
    }

    private HttpStatus failure(LocalWhisperRecognitionService service) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.recognize(audio));
        return HttpStatus.valueOf(error.getStatusCode().value());
    }

    @Test
    void nonZeroExitIsReportedAsBadGateway() throws Exception {
        assertEquals(HttpStatus.BAD_GATEWAY, failure(service(script("fail.sh", "exit 3"), Duration.ofSeconds(10))));
    }

    @Test
    void hangingProcessTimesOutAsGatewayTimeout() throws Exception {
        long started = System.nanoTime();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, failure(service(script("hang.sh", "sleep 30"), Duration.ofMillis(800))));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toSeconds() < 10, "시간 초과 후 프로세스를 기다리지 않아야 한다");
    }

    @Test
    void missingOrCorruptResultFileIsNotTreatedAsRecognizedSpeech() throws Exception {
        assertEquals(HttpStatus.BAD_GATEWAY, failure(service(script("silent.sh", "exit 0"), Duration.ofSeconds(10))));
        assertEquals(HttpStatus.BAD_GATEWAY, failure(service(script("corrupt.sh", writeJson("{not json")), Duration.ofSeconds(10))));
        assertEquals(HttpStatus.BAD_GATEWAY, failure(service(script("shape.sh", writeJson("{\"other\":1}")), Duration.ofSeconds(10))));
    }

    @Test
    void missingExecutableOrModelIsServiceUnavailable() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure(service(dir.resolve("no-such-cli").toString(), Duration.ofSeconds(5))));
        LocalWhisperRecognitionService noModel = new LocalWhisperRecognitionService("whisper-cli", dir.resolve("missing.bin").toString(),
                "ko", 1, Duration.ofSeconds(5), 300, 30000, 1, "whisper-vad-speech-segments", "");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure(noModel));
    }

    @Test
    void hangingVadProcessDoesNotBlockRecognitionForever() throws Exception {
        // VAD 실행 파일이 멈춰도 시간 제한 안에 포기하고(VAD 없이) 인식을 계속해야 한다. 막히면 분석 슬롯을 영구히 점유한다.
        Path vadModel = Files.writeString(dir.resolve("fake-vad.bin"), "not a real model");
        String vad = script("hang-vad.sh", "sleep 30");
        String json = "{\"transcription\":[{\"text\":\" 라디오\",\"tokens\":[{\"text\":\"라\",\"p\":0.9}]}]}";
        LocalWhisperRecognitionService service = new LocalWhisperRecognitionService(script("ok.sh", writeJson(json)), model.toString(), "ko", 1,
                Duration.ofMillis(800), 300, 30000, 1, vad, vadModel.toString());
        assertTrue(service.vadEnabled());
        long started = System.nanoTime();
        var result = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> service.recognize(audio));
        assertEquals("라디오", result.transcript());
        assertNull(result.speechSegments(), "VAD 시간 초과 시 말소리 구간을 지어내지 않는다");
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toSeconds() < 10);
    }

    @Test
    void validResultIsParsedWithoutVadTiming() throws Exception {
        String json = "{\"transcription\":[{\"text\":\" 라디오\",\"tokens\":[{\"text\":\"라\",\"p\":0.9},{\"text\":\"디오\",\"p\":0.7},{\"text\":\"[_TT_1]\",\"p\":0.1}]}]}";
        SpeechRecognitionResult result = service(script("ok.sh", writeJson(json)), Duration.ofSeconds(10)).recognize(audio);
        assertEquals("라디오", result.transcript());
        assertEquals(0.8, result.confidence().doubleValue(), 0.0001);
        assertNull(result.speechSegments(), "VAD를 쓰지 않으면 발화 구간을 지어내지 않는다");
        assertTrue(result.peakAmplitude() > 0);
    }

    @Test
    void tooShortTooLongAndSilentRecordingsAreRejectedBeforeInference() throws Exception {
        String neverRun = script("never.sh", "echo should-not-run > \"" + dir.resolve("ran.txt") + "\"; exit 0");
        LocalWhisperRecognitionService shortLimit = new LocalWhisperRecognitionService(neverRun, model.toString(), "ko", 1,
                Duration.ofSeconds(5), 5000, 30000, 1, "whisper-vad-speech-segments", "");
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, HttpStatus.valueOf(assertThrows(ResponseStatusException.class, () -> shortLimit.recognize(audio)).getStatusCode().value()));
        LocalWhisperRecognitionService longLimit = new LocalWhisperRecognitionService(neverRun, model.toString(), "ko", 1,
                Duration.ofSeconds(5), 100, 500, 1, "whisper-vad-speech-segments", "");
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, HttpStatus.valueOf(assertThrows(ResponseStatusException.class, () -> longLimit.recognize(audio)).getStatusCode().value()));
        Path silence = Path.of(getClass().getResource("/speech/silence.wav").toURI());
        assertThrows(ResponseStatusException.class, () -> service(neverRun, Duration.ofSeconds(5)).recognize(silence));
        assertFalse(Files.exists(dir.resolve("ran.txt")), "검사에서 걸린 녹음은 추론을 실행하지 않는다");
    }

    // ── 처리 시간 측정 로그(운영 임계값 검증용) ─────────────────

    private static List<Long> values(CapturedOutput output, String outcome, String field) {
        List<Long> found = new ArrayList<>();
        Matcher line = Pattern.compile("speech\\.timing stage=recognize outcome=" + outcome + " .*").matcher(output.getOut());
        while (line.find()) {
            Matcher value = Pattern.compile(field + "=(\\d+)").matcher(line.group());
            if (value.find()) found.add(Long.parseLong(value.group(1)));
        }
        return found;
    }

    @Test
    void timingLogShowsWhichStageTookTheTimeWithoutSpeechContent(CapturedOutput output) throws Exception {
        Path vadModel = Files.writeString(dir.resolve("fake-vad.bin"), "not a real model");
        String json = "{\"transcription\":[{\"text\":\" 라디오\",\"tokens\":[{\"text\":\"라\",\"p\":0.9}]}]}";
        new LocalWhisperRecognitionService(script("ok.sh", writeJson(json)), model.toString(), "ko", 1, Duration.ofMillis(800), 300, 30000, 1,
                script("hang-vad.sh", "sleep 30"), vadModel.toString()).recognize(audio);
        assertTrue(output.getOut().contains("vad=UNAVAILABLE"), output.getOut());
        assertTrue(values(output, "OK", "vadMs").get(0) >= 700, "멈춘 VAD가 쓴 시간이 기록되어야 한다");

        failure(service(script("hang.sh", "sleep 30"), Duration.ofMillis(800)));
        assertTrue(values(output, "504", "whisperMs").get(0) >= 700, "Whisper 시간 초과는 504와 걸린 시간으로 기록된다");
        assertTrue(output.getOut().contains("timeoutMs=800"));

        assertFalse(output.getOut().contains("라디오"), "인식 내용은 로그에 남기지 않는다");
        assertFalse(output.getOut().contains(dir.toString()), "파일 경로는 로그에 남기지 않는다");
        assertFalse(output.getOut().contains(audio.getFileName().toString()));
    }

    @Test
    void timingLogSeparatesWaitingForAFreeSlotFromInference(CapturedOutput output) throws Exception {
        String json = "{\"transcription\":[{\"text\":\" 라디오\",\"tokens\":[{\"text\":\"라\",\"p\":0.9}]}]}";
        // 동시 처리 1개: 두 번째 요청은 첫 요청의 추론(약 1초)이 끝날 때까지 기다린다.
        LocalWhisperRecognitionService service = service(script("slow.sh", "sleep 1\n" + writeJson(json)), Duration.ofSeconds(5));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<SpeechRecognitionResult>> results = List.of(pool.submit(() -> service.recognize(audio)), pool.submit(() -> service.recognize(audio)));
            for (Future<SpeechRecognitionResult> result : results) assertEquals("라디오", result.get().transcript());
        } finally {
            pool.shutdownNow();
        }
        List<Long> queue = values(output, "OK", "queueMs");
        assertEquals(2, queue.size());
        assertTrue(queue.stream().mapToLong(Long::longValue).max().orElseThrow() >= 700, "슬롯 대기 시간이 따로 기록되어야 한다: " + queue);
        assertTrue(values(output, "OK", "whisperMs").stream().allMatch(ms -> ms >= 900 && ms < 4000));
    }
}
