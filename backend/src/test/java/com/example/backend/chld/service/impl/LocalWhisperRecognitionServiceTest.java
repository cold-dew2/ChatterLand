package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechRecognitionResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 whisper.cpp + 공개 Whisper 모델로 한국어 합성 음성을 추론한다.
 * 모델이나 실행 파일이 없는 환경에서는 건너뛴다(성공으로 위장하지 않도록 assume 사용).
 */
class LocalWhisperRecognitionServiceTest {
    static final String MODEL = System.getenv().getOrDefault("WHISPER_MODEL_PATH",
            System.getProperty("user.home") + "/.cache/chatterland/whisper/ggml-large-v3-turbo-q5_0.bin");

    static LocalWhisperRecognitionService service(String model) {
        return new LocalWhisperRecognitionService("whisper-cli", model, "ko", 4, Duration.ofSeconds(120), 300, 30000, 1);
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
        assumeTrue(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 실제 추론 테스트를 건너뜁니다.");
        LocalWhisperRecognitionService service = service(MODEL);
        SpeechRecognitionResult result = service.recognize(fixture("sentence-weather.wav"));
        assertEquals("오늘은 날씨가 좋아요", TranscriptComparator.normalize(result.transcript()));
        assertEquals("whisper.cpp", result.engineName());
        assertNotNull(result.confidence());
        assertTrue(result.processingMs() > 0);
    }

    @Test
    void recognizesShortWordWithoutHallucination() throws Exception {
        assumeTrue(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 실제 추론 테스트를 건너뜁니다.");
        SpeechRecognitionResult result = service(MODEL).recognize(fixture("word-radio.wav"));
        assertEquals("라디오", TranscriptComparator.normalize(result.transcript()));
    }

    @Test
    void rejectsSilentRecordingBeforeInference() throws Exception {
        assumeTrue(Files.isReadable(Path.of(MODEL)), "로컬 Whisper 모델이 없어 테스트를 건너뜁니다.");
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service(MODEL).recognize(fixture("silence.wav")));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, error.getStatusCode());
    }

    @Test
    void hallucinationPhrasesAreNotTreatedAsRecognizedSpeech() {
        assertTrue(SpeechAnalysisServiceImpl.isHallucination("구독과 좋아요를 눌러주세요.", "로봇"));
        assertFalse(SpeechAnalysisServiceImpl.isHallucination("로봇", "로봇"));
    }
}
