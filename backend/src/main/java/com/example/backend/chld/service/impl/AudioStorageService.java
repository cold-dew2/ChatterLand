package com.example.backend.chld.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class AudioStorageService {
    private static final Set<String> ALLOWED_MIME = Set.of("audio/webm", "audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4", "audio/ogg", "audio/aac", "audio/3gpp");
    private final Path root;

    public AudioStorageService(@Value("${app.audio.storage-path:./storage/audio}") String storagePath) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public StoredAudio store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "녹음 파일이 비어 있습니다.");
        String mime = file.getContentType() == null ? "" : file.getContentType().split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_MIME.contains(mime)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 음성 파일 형식입니다.");
        if (file.getSize() > 10L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "음성 파일은 10MB 이하여야 합니다.");
        String extension = switch (mime) {
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "audio/mpeg" -> ".mp3";
            case "audio/mp4" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            case "audio/aac" -> ".aac";
            default -> ".webm";
        };
        String filename = UUID.randomUUID() + extension;
        try {
            if (!hasExpectedSignature(file.getInputStream(), mime))
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "파일 내용이 음성 파일 형식과 일치하지 않습니다.");
            Files.createDirectories(root);
            Path destination = root.resolve(filename).normalize();
            if (!destination.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 파일 이름입니다.");
            file.transferTo(destination);
            return new StoredAudio(destination.toString(), mime, filename);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "음성 파일 저장에 실패했습니다.");
        }
    }

    private boolean hasExpectedSignature(InputStream input, String mime) throws IOException {
        byte[] header;
        try (input) { header = input.readNBytes(12); }
        return switch (mime) {
            case "audio/webm" -> startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
            case "audio/wav", "audio/x-wav" -> startsWithAscii(header, 0, "RIFF") && startsWithAscii(header, 8, "WAVE");
            case "audio/mpeg" -> startsWithAscii(header, 0, "ID3") || (header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0);
            case "audio/mp4", "audio/3gpp" -> startsWithAscii(header, 4, "ftyp");
            case "audio/ogg" -> startsWithAscii(header, 0, "OggS");
            case "audio/aac" -> header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xF6) == 0xF0;
            default -> false;
        };
    }

    private boolean startsWith(byte[] value, int... expected) {
        if (value.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((value[i] & 0xFF) != expected[i]) return false;
        return true;
    }

    private boolean startsWithAscii(byte[] value, int offset, String expected) {
        if (value.length < offset + expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) if (value[offset + i] != (byte) expected.charAt(i)) return false;
        return true;
    }

    public record StoredAudio(String path, String mimeType, String fileName) { }

    /** 저장 경로가 저장소 루트 안에 있을 때만 파일을 읽는다. */
    public byte[] read(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) throw new ResponseStatusException(HttpStatus.GONE, "보관 기간이 지나 녹음 파일이 삭제되었습니다.");
        Path candidate = Path.of(storedPath).toAbsolutePath().normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) throw new ResponseStatusException(HttpStatus.GONE, "녹음 파일을 찾을 수 없습니다.");
        try { return Files.readAllBytes(candidate); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "녹음 파일을 읽지 못했습니다."); }
    }

    public enum DeleteResult { DELETED, MISSING, REJECTED, FAILED }

    /**
     * DB에 기록된 경로의 파일을 삭제한다. 저장소 루트 밖의 경로는 삭제하지 않는다(REJECTED).
     * 파일이 이미 없으면 MISSING으로 보고해 정리 작업이 멈추지 않게 한다.
     */
    public DeleteResult deleteStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return DeleteResult.MISSING;
        Path candidate = Path.of(storedPath).toAbsolutePath().normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) return DeleteResult.REJECTED;
        try { return Files.deleteIfExists(candidate) ? DeleteResult.DELETED : DeleteResult.MISSING; }
        catch (IOException e) { return DeleteResult.FAILED; }
    }

    public Path root() { return root; }

    public void discard(StoredAudio stored) {
        if (stored == null || stored.path() == null) return;
        Path candidate = Path.of(stored.path()).toAbsolutePath().normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) return;
        try { Files.deleteIfExists(candidate); } catch (IOException ignored) { /* best-effort cleanup; do not mask the request failure */ }
    }
}
