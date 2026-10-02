package com.example.backend.chld.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AudioStorageServiceTest {
    @TempDir Path tempDir;

    @Test
    void storesAllowedAudioWithGeneratedName() throws Exception {
        AudioStorageService storage = new AudioStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("audio", "input.webm", "audio/webm;codecs=opus", new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});

        AudioStorageService.StoredAudio stored = storage.store(file);

        assertEquals("audio/webm", stored.mimeType());
        assertTrue(stored.fileName().endsWith(".webm"));
        assertTrue(Files.exists(Path.of(stored.path())));
        assertNotEquals("input.webm", stored.fileName());
    }

    @Test
    void rejectsUnsupportedMimeType() {
        AudioStorageService storage = new AudioStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("audio", "bad.txt", "text/plain", new byte[]{1});
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> storage.store(file));
        assertEquals(415, error.getStatusCode().value());
    }

    @Test
    void rejectsEmptyAudio() {
        AudioStorageService storage = new AudioStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("audio", "empty.webm", "audio/webm", new byte[0]);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> storage.store(file));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsFileWhoseBytesDoNotMatchDeclaredAudioType() {
        AudioStorageService storage = new AudioStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("audio", "fake.webm", "audio/webm", "not audio".getBytes());
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> storage.store(file));
        assertEquals(415, error.getStatusCode().value());
    }
}
