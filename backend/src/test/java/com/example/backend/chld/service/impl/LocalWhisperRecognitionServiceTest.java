package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.backend.support.TestPrerequisites.requireSpeech;

/**
 * 실제 whisper.cpp + 공개 Whisper 모델로 한국어 합성 음성을 추론한다.
 * 모델이나 실행 파일이 없는 환경에서는 건너뛴다(성공으로 위장하지 않도록 assume 사용).
 */
class LocalWhisperRecognitionServiceTest {
    static final String MODEL = System.getenv().getOrDefault("WHISPER_MODEL_PATH",
            System.getProperty("user.home") + "/.cache/chatterland/whisper/ggml-large-v3-turbo-q5_0.bin");

    static final String VAD_MODEL = System.getenv().getOrDefault("WHISPER_VAD_MODEL_PATH",
            System.getProperty("user.home") + "/.cache/chatterland/whisper/ggml-silero-v5.1.2.bin");

    static LocalWhisperRecognitionService service(String model) {
        return service(model, "");
    }

    static LocalWhisperRecognitionService service(String model, String vadModel) {
        return new LocalWhisperRecognitionService("whisper-cli", model, "ko", 4, Duration.ofSeconds(120), 300, 30000, 1,
                "whisper-vad-speech-segments", vadModel);
    }

    private Path fixture(String name) throws Exception {
        return Path.of(getClass().getResource("/speech/" + name).toURI());
    }

    @Test
    void reportsUnavailableModelInsteadOfFakingResult() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service("/nonexistent/model.bin").requireAvailable());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void recognizesKoreanSentenceWithLocalModel() throws Exception {
        requireSpeech(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 실제 추론 테스트를 건너뜁니다.");
        LocalWhisperRecognitionService service = service(MODEL);
        SpeechRecognitionResult result = service.recognize(fixture("sentence-weather.wav"));
        assertEquals("오늘은 날씨가 좋아요", TranscriptComparator.normalize(result.transcript()));
        assertEquals("whisper.cpp", result.engineName());
        assertNotNull(result.confidence());
        assertTrue(result.processingMs() > 0);
    }

    @Test
    void recognizesShortWordWithoutHallucination() throws Exception {
        requireSpeech(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 실제 추론 테스트를 건너뜁니다.");
        SpeechRecognitionResult result = service(MODEL).recognize(fixture("word-radio.wav"));
        assertEquals("라디오", TranscriptComparator.normalize(result.transcript()));
    }

    @Test
    void rejectsSilentRecordingBeforeInference() throws Exception {
        requireSpeech(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 테스트를 건너뜁니다.");
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service(MODEL).recognize(fixture("silence.wav")));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, error.getStatusCode());
    }

    @Test
    void rejectsBackgroundNoiseWithoutSpeechUsingVad() throws Exception {
        requireSpeech(Files.isReadable(Path.of(MODEL)) && Files.isReadable(Path.of(VAD_MODEL)), "Whisper/VAD 모델이 없어 테스트를 건너뜁니다.");
        LocalWhisperRecognitionService service = service(MODEL, VAD_MODEL);
        requireSpeech(service.vadEnabled(), "whisper-vad-speech-segments 실행 파일이 없어 테스트를 건너뜁니다.");
        // 실제 녹음의 배경 소음(말소리 없음). VAD 없이 인식하면 Whisper가 "감사합니다."를 만들어낸다.
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.recognize(fixture("room-noise.wav")));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, error.getStatusCode());
    }

    @Test
    void vadKeepsShortWordRecognition() throws Exception {
        requireSpeech(Files.isReadable(Path.of(MODEL)) && Files.isReadable(Path.of(VAD_MODEL)), "Whisper/VAD 모델이 없어 테스트를 건너뜁니다.");
        LocalWhisperRecognitionService service = service(MODEL, VAD_MODEL);
        requireSpeech(service.vadEnabled(), "whisper-vad-speech-segments 실행 파일이 없어 테스트를 건너뜁니다.");
        assertEquals("라디오", TranscriptComparator.normalize(service.recognize(fixture("word-radio.wav")).transcript()));
    }

    @Test
    void parsesVadSegmentsRelativeToOriginalAudio() {
        // whisper-vad-speech-segments 출력(센티초, 앞쪽 1초 패딩 포함)을 원본 녹음 기준 ms로 바꾼다.
        var segments = LocalWhisperRecognitionService.parseSpeechSegments(
                "Detected 2 speech segments:\nSpeech segment 0: start = 150.00, end = 210.00\nSpeech segment 1: start = 240.00, end = 400.00\n", 2500);
        assertEquals(2, segments.size());
        assertEquals(500, segments.get(0).startMs());
        assertEquals(1100, segments.get(0).endMs());
        assertEquals(2500, segments.get(1).endMs());
        assertTrue(LocalWhisperRecognitionService.parseSpeechSegments("Detected 0 speech segments:\n", 1000).isEmpty());
        assertNull(LocalWhisperRecognitionService.parseSpeechSegments("unexpected", 1000));
    }

    @Test
    void recognitionResultCarriesVadSegmentsAndAudioQuality() throws Exception {
        requireSpeech(Files.isReadable(Path.of(MODEL)) && Files.isReadable(Path.of(VAD_MODEL)), "Whisper/VAD 모델이 없어 테스트를 건너뜁니다.");
        LocalWhisperRecognitionService service = service(MODEL, VAD_MODEL);
        requireSpeech(service.vadEnabled(), "whisper-vad-speech-segments 실행 파일이 없어 테스트를 건너뜁니다.");
        SpeechRecognitionResult result = service.recognize(fixture("sentence-weather-recorded.wav"));
        assertEquals("오늘은 날씨가 좋아요", TranscriptComparator.normalize(result.transcript()));
        assertNotNull(result.speechSegments());
        assertFalse(result.speechSegments().isEmpty());
        // 앞에 0.5초 배경 소음을 붙인 녹음이므로 말소리는 0.3초 이후에 시작한다.
        assertTrue(result.speechSegments().get(0).startMs() >= 300, String.valueOf(result.speechSegments()));
        assertTrue(result.peakAmplitude() > 1500);
        assertEquals(0d, result.clippedRatio(), 0.001);
    }

    @Test
    void vadIsDisabledWhenModelIsMissing() {
        assertFalse(service(MODEL, "/nonexistent/vad.bin").vadEnabled());
    }

    @Test
    void hallucinationPhrasesAreNotTreatedAsRecognizedSpeech() {
        assertTrue(SpeechAnalysisServiceImpl.isHallucination("구독과 좋아요를 눌러주세요.", "로봇"));
        assertTrue(SpeechAnalysisServiceImpl.isHallucination("한글자막 제공 및 광고를 포함하고 있습니다.", "불"));
        assertFalse(SpeechAnalysisServiceImpl.isHallucination("로봇", "로봇"));
    }
}
