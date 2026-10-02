package com.example.backend;

import com.example.backend.chld.service.AudioRetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 음성 파일 6개월 보관 정책: 만료 삭제, 미만료 보존, 파일 없음, 삭제 실패와 재시도, 경로 조작 방지, 중복 실행 */
@SpringBootTest(properties = {
        "jwt.secret=test-only-signing-key-must-be-at-least-32-bytes",
        "app.audio.storage-path=${java.io.tmpdir}/chatterland-retention-test",
        "app.scheduling.enabled=false"
})
@Transactional
class AudioRetentionIntegrationTest {
    @Autowired AudioRetentionService retention;
    @Autowired JdbcTemplate jdbc;
    private Path storage;
    private long studentId;

    @BeforeEach
    void setUp() throws Exception {
        storage = Path.of(System.getProperty("java.io.tmpdir"), "chatterland-retention-test");
        Files.createDirectories(storage);
        jdbc.update("INSERT INTO student_profiles(center_id,name,age) VALUES(1,'보관 테스트',8)");
        studentId = jdbc.queryForObject("SELECT MAX(student_id) FROM student_profiles", Long.class);
    }

    private String insert(String path, String expiresSql) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO speech_analyses(analysis_id,student_id,exercise_id,item_id,audio_path,audio_mime,status,transcript,teacher_note,review_status,created_at,audio_expires_at) "
                + "VALUES(?,?,1,'1',?,'audio/wav','COMPLETED','라디오','검토 메모','REVIEWED',DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 MONTH)," + expiresSql + ")", id, studentId, path);
        return id;
    }

    private String audioPath(String id) { return jdbc.queryForObject("SELECT audio_path FROM speech_analyses WHERE analysis_id=?", String.class, id); }

    @Test
    void deletesExpiredFilesKeepsUnexpiredAndKeepsAnalysisRecords() throws Exception {
        assertEquals(6, retention.retentionMonths());
        Path expiredFile = Files.writeString(storage.resolve(UUID.randomUUID() + ".wav"), "expired");
        Path keptFile = Files.writeString(storage.resolve(UUID.randomUUID() + ".wav"), "kept");
        String expired = insert(expiredFile.toString(), "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)");
        String kept = insert(keptFile.toString(), "DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY)");
        String missing = insert(storage.resolve(UUID.randomUUID() + ".wav").toString(), "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)");

        AudioRetentionService.PurgeResult result = retention.purgeExpired();
        assertTrue(result.deleted() >= 1 && result.missing() >= 1);
        assertFalse(Files.exists(expiredFile));
        assertNull(audioPath(expired));
        assertNull(audioPath(missing));
        assertTrue(Files.exists(keptFile));
        assertEquals(keptFile.toString(), audioPath(kept));
        // 음성 파일만 삭제되고 인식 결과·선생님 검토 기록은 남는다.
        assertEquals("검토 메모", jdbc.queryForObject("SELECT teacher_note FROM speech_analyses WHERE analysis_id=?", String.class, expired));
        assertNotNull(jdbc.queryForObject("SELECT audio_deleted_at FROM speech_analyses WHERE analysis_id=?", Object.class, expired));

        // 다시 실행해도 안전하다(이미 처리된 항목은 대상이 아니다).
        retention.purgeExpired();
        assertTrue(Files.exists(keptFile));
        Files.deleteIfExists(keptFile);
    }

    @Test
    void failedDeletionIsRecordedAndRetriedAndPathsOutsideStorageAreNeverDeleted() throws Exception {
        // 비어 있지 않은 디렉터리는 삭제에 실패한다 → 실패 기록 후 다음 실행에서 재시도
        Path stubborn = Files.createDirectories(storage.resolve("stubborn-" + UUID.randomUUID()));
        Path inner = Files.writeString(stubborn.resolve("inner.txt"), "x");
        String failing = insert(stubborn.toString(), "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)");
        Path outside = Files.createTempFile("outside-storage", ".wav");
        String rejected = insert(outside.toString(), "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)");
        String traversal = insert(storage.resolve("../escape-" + UUID.randomUUID() + ".wav").toString(), "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)");

        AudioRetentionService.PurgeResult first = retention.purgeExpired();
        assertTrue(first.failed() >= 1 && first.rejected() >= 2);
        assertTrue(Files.exists(outside), "저장소 밖 파일은 삭제하지 않는다");
        assertEquals("DELETE_FAILED", jdbc.queryForObject("SELECT audio_delete_error FROM speech_analyses WHERE analysis_id=?", String.class, failing));
        assertTrue(jdbc.queryForObject("SELECT audio_delete_attempts FROM speech_analyses WHERE analysis_id=?", Integer.class, failing) >= 1);
        assertEquals("INVALID_PATH", jdbc.queryForObject("SELECT audio_delete_error FROM speech_analyses WHERE analysis_id=?", String.class, rejected));
        assertNotNull(audioPath(traversal));

        Files.delete(inner);
        retention.purgeExpired();
        assertFalse(Files.exists(stubborn));
        assertNull(audioPath(failing));
        Files.deleteIfExists(outside);
    }

    @Test
    void deleteAllForStudentRemovesEveryStoredRecording() throws Exception {
        Path a = Files.writeString(storage.resolve(UUID.randomUUID() + ".wav"), "a");
        String id = insert(a.toString(), "DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 MONTH)");
        retention.deleteAllForStudent(studentId);
        assertFalse(Files.exists(a));
        assertNull(audioPath(id));
    }
}
