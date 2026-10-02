package com.example.backend.chld.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 16-bit PCM WAV 파일의 형식 확인, 길이·음량 계산, 앞뒤 무음 패딩을 담당한다.
 * 짧은 단어 녹음은 Whisper 계열 모델에서 환각(없는 문장 생성)이 잦아 앞뒤에 무음을 붙여 추론한다.
 */
final class WavAudio {
    private final int channels;
    private final int sampleRate;
    private final byte[] pcm;

    private WavAudio(int channels, int sampleRate, byte[] pcm) {
        this.channels = channels; this.sampleRate = sampleRate; this.pcm = pcm;
    }

    static WavAudio read(Path path) {
        byte[] bytes;
        try { bytes = Files.readAllBytes(path); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "녹음 파일을 읽지 못했습니다."); }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 12 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) throw unsupported();
        int offset = 12, channels = 0, sampleRate = 0, bits = 0, format = 0;
        byte[] pcm = null;
        while (offset + 8 <= bytes.length) {
            int size = buffer.getInt(offset + 4);
            if (size < 0 || offset + 8L + size > bytes.length) size = bytes.length - offset - 8;
            if (ascii(bytes, offset, "fmt ") && size >= 16) {
                format = buffer.getShort(offset + 8) & 0xFFFF;
                channels = buffer.getShort(offset + 10) & 0xFFFF;
                sampleRate = buffer.getInt(offset + 12);
                bits = buffer.getShort(offset + 22) & 0xFFFF;
            } else if (ascii(bytes, offset, "data")) {
                pcm = new byte[size - (size % 2)];
                System.arraycopy(bytes, offset + 8, pcm, 0, pcm.length);
            }
            offset += 8 + size + (size % 2);
        }
        if (format != 1 || bits != 16 || channels < 1 || channels > 2 || sampleRate < 8000 || sampleRate > 48000 || pcm == null) throw unsupported();
        return new WavAudio(channels, sampleRate, pcm);
    }

    long durationMs() {
        return pcm.length * 1000L / ((long) sampleRate * channels * 2);
    }

    /** 0~32767 범위의 최대 진폭. 무음 녹음을 걸러내는 데 사용한다. */
    int peakAmplitude() {
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int peak = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) peak = Math.max(peak, Math.abs((int) buffer.getShort(i)));
        return Math.min(peak, 32767);
    }

    void writePadded(Path destination, long paddingMs) throws IOException {
        int padBytes = (int) (paddingMs * sampleRate / 1000L) * channels * 2;
        int dataSize = pcm.length + padBytes * 2;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(sampleRate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16)
                .put("data".getBytes()).putInt(dataSize);
        byte[] out = new byte[44 + dataSize];
        System.arraycopy(header.array(), 0, out, 0, 44);
        System.arraycopy(pcm, 0, out, 44 + padBytes, pcm.length);
        Files.write(destination, out);
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != (byte) expected.charAt(i)) return false;
        return true;
    }

    private static ResponseStatusException unsupported() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "로컬 음성 인식은 16-bit PCM WAV 녹음만 지원합니다.");
    }
}
