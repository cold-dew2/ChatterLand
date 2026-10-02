package com.example.backend.chld.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WavAudioTest {
    @TempDir Path temp;

    private Path fixture(String name) throws Exception {
        return Path.of(getClass().getResource("/speech/" + name).toURI());
    }

    @Test
    void readsPcmWavWithExtraChunksAndPadsSilence() throws Exception {
        WavAudio audio = WavAudio.read(fixture("word-radio.wav"));
        assertTrue(audio.durationMs() > 300 && audio.durationMs() < 2000);
        assertTrue(audio.peakAmplitude() > 1000);
        Path padded = temp.resolve("padded.wav");
        audio.writePadded(padded, 1000);
        WavAudio paddedAudio = WavAudio.read(padded);
        assertEquals(audio.durationMs() + 2000, paddedAudio.durationMs(), 2);
    }

    @Test
    void detectsSilence() throws Exception {
        assertEquals(0, WavAudio.read(fixture("silence.wav")).peakAmplitude());
    }

    @Test
    void rejectsNonWavContent() throws Exception {
        Path fake = temp.resolve("fake.wav");
        Files.writeString(fake, "not a wav file");
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> WavAudio.read(fake));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatusCode());
    }
}
