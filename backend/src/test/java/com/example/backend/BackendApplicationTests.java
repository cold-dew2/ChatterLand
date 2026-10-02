package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BackendApplicationTests {
    @Autowired ScheduledTaskHolder scheduledTasks;

    @Test
    void contextLoads() {
    }

    @Test
    void audioRetentionPurgeIsRegisteredAsScheduledTask() {
        // 배포 환경(기본 설정)에서 음성 보관 기간 정리 작업이 실제 스케줄러에 등록되는지 확인한다.
        assertTrue(scheduledTasks.getScheduledTasks().stream()
                .anyMatch(task -> task.toString().contains("AudioRetentionScheduler.purgeExpiredAudio")));
    }
}
