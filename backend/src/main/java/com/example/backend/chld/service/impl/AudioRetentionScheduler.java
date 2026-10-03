package com.example.backend.chld.service.impl;

import com.example.backend.chld.service.AudioRetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 정해진 시각(기본 03:30)에 보관 기간이 지난 음성 파일을 삭제한다. */
@Slf4j
@Component
public class AudioRetentionScheduler {
    private final AudioRetentionService retention;

    public AudioRetentionScheduler(AudioRetentionService retention) { this.retention = retention; }

    @Scheduled(cron = "${app.audio.retention-cron:0 30 3 * * *}", zone = "${app.audio.retention-zone:Asia/Seoul}")
    public void purgeExpiredAudio() {
        try {
            retention.purgeExpired();
        } catch (RuntimeException e) {
            // 실패한 항목은 삭제 시도 횟수와 함께 남아 다음 실행에서 다시 처리된다.
            log.error("Audio retention purge failed: {}", com.example.backend.global.exception.LogMasking.describe(e));
        }
    }
}
