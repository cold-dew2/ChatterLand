package com.example.backend.chld.service.impl;

import com.example.backend.chld.mapper.AudioRetentionMapper;
import com.example.backend.chld.service.AudioRetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Service
public class AudioRetentionServiceImpl implements AudioRetentionService {
    private static final int BATCH_SIZE = 200;
    private static final int MAX_DELETE_ATTEMPTS = 10;
    private static final Duration TEMP_DIR_TTL = Duration.ofDays(1);

    private final AudioRetentionMapper mapper;
    private final AudioStorageService storage;
    private final int retentionMonths;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AudioRetentionServiceImpl(AudioRetentionMapper mapper, AudioStorageService storage,
                                     @Value("${app.audio.retention-months:6}") int retentionMonths) {
        this.mapper = mapper; this.storage = storage; this.retentionMonths = Math.max(1, retentionMonths);
    }

    @Override public int retentionMonths() { return retentionMonths; }

    @Override
    public PurgeResult purgeExpired() {
        // 같은 서버에서 겹쳐 실행되지 않게 막는다. 여러 서버에서 동시에 돌아도 DB 갱신은 audio_path IS NOT NULL 조건으로 한 번만 반영된다.
        if (!running.compareAndSet(false, true)) return new PurgeResult(0, 0, 0, 0, 0, 0);
        try {
            Counter counter = new Counter();
            List<Map<String,Object>> analyses;
            do {
                analyses = mapper.findExpiredAnalysisAudio(MAX_DELETE_ATTEMPTS, BATCH_SIZE);
                analyses.forEach(row -> deleteAnalysisAudio(row, counter));
            } while (analyses.size() == BATCH_SIZE && counter.progressed());
            mapper.findExpiredMessageAudio(retentionMonths, BATCH_SIZE * 10).forEach(row -> deleteMessageAudio(row, counter));
            counter.orphans = deleteOrphans();
            counter.tempDirs = deleteStaleTempDirs();
            PurgeResult result = counter.result();
            // 파일 경로·학생 정보는 남기지 않고 건수만 기록한다.
            log.info("Audio retention purge finished: deleted={}, missing={}, failed={}, rejected={}, orphans={}, tempDirs={}",
                    result.deleted(), result.missing(), result.failed(), result.rejected(), result.orphansDeleted(), result.tempDirsDeleted());
            return result;
        } finally {
            running.set(false);
        }
    }

    @Override
    public PurgeResult deleteAllForStudent(long studentId) {
        Counter counter = new Counter();
        mapper.findStudentAnalysisAudio(studentId).forEach(row -> deleteAnalysisAudio(row, counter));
        mapper.findStudentMessageAudio(studentId).forEach(row -> deleteMessageAudio(row, counter));
        return counter.result();
    }

    private void deleteAnalysisAudio(Map<String,Object> row, Counter counter) {
        String id = String.valueOf(row.get("id"));
        AudioStorageService.DeleteResult result = storage.deleteStoredPath((String) row.get("audioPath"));
        switch (result) {
            case DELETED, MISSING -> { mapper.markAnalysisAudioDeleted(id); counter.count(result); }
            case REJECTED -> { mapper.recordAnalysisDeleteFailure(id, "INVALID_PATH"); counter.rejected++; }
            case FAILED -> { mapper.recordAnalysisDeleteFailure(id, "DELETE_FAILED"); counter.failed++; }
        }
    }

    private void deleteMessageAudio(Map<String,Object> row, Counter counter) {
        long id = ((Number) row.get("id")).longValue();
        AudioStorageService.DeleteResult result = storage.deleteStoredPath((String) row.get("audioPath"));
        switch (result) {
            case DELETED, MISSING -> { mapper.markMessageAudioDeleted(id); counter.count(result); }
            case REJECTED -> counter.rejected++;
            case FAILED -> counter.failed++;
        }
    }

    /** DB에서 참조하지 않는 저장소 파일 중 보관 기간이 지난 것만 삭제한다(분석 도중 비정상 종료로 남은 파일 등). */
    private int deleteOrphans() {
        Path root = storage.root();
        if (!Files.isDirectory(root)) return 0;
        FileTime cutoff = FileTime.from(ZonedDateTime.now(ZoneId.systemDefault()).minusMonths(retentionMonths).toInstant());
        int deleted = 0;
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (Files.getLastModifiedTime(file).compareTo(cutoff) > 0) continue;
                if (mapper.countAudioReferences(file.toString()) > 0) continue;
                if (storage.deleteStoredPath(file.toString()) == AudioStorageService.DeleteResult.DELETED) deleted++;
            }
        } catch (IOException e) {
            log.warn("Audio retention could not scan storage directory");
        }
        return deleted;
    }

    /** 로컬 음성 인식 임시 작업 폴더가 남아 있으면 정리한다. */
    private int deleteStaleTempDirs() {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        Instant cutoff = Instant.now().minus(TEMP_DIR_TTL);
        int deleted = 0;
        try (Stream<Path> dirs = Files.list(tmp)) {
            for (Path dir : dirs.filter(path -> path.getFileName().toString().startsWith("chatterland-asr") && Files.isDirectory(path)).toList()) {
                if (Files.getLastModifiedTime(dir).toInstant().isAfter(cutoff)) continue;
                try (Stream<Path> walk = Files.walk(dir)) {
                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                    deleted++;
                } catch (IOException ignored) { /* 다음 실행에서 다시 시도한다 */ }
            }
        } catch (IOException ignored) { /* 임시 폴더를 읽을 수 없으면 건너뛴다 */ }
        return deleted;
    }

    private static final class Counter {
        int deleted, missing, failed, rejected, orphans, tempDirs;
        private int lastTotal = -1;
        void count(AudioStorageService.DeleteResult result) { if (result == AudioStorageService.DeleteResult.DELETED) deleted++; else missing++; }
        /** 실패만 반복되는 배치에서 무한 반복하지 않도록 진행 여부를 확인한다. */
        boolean progressed() { int total = deleted + missing; boolean progressed = total != lastTotal; lastTotal = total; return progressed; }
        PurgeResult result() { return new PurgeResult(deleted, missing, failed, rejected, orphans, tempDirs); }
    }
}
