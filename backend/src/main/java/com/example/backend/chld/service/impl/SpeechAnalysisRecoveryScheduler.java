package com.example.backend.chld.service.impl;

import com.example.backend.chld.service.SpeechAnalysisService;
import com.example.backend.global.exception.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 서버 재시작·비정상 종료로 PROCESSING에 남은 분석을 주기적으로 정리한다(기본: 시작 30초 후, 이후 5분마다).
 * 같은 키로 재요청이 오면 그 자리에서도 정리하므로, 이 작업은 재요청이 없는 분석과 녹음 파일을 위한 것이다.
 */
@Slf4j
@Component
public class SpeechAnalysisRecoveryScheduler {
    private final SpeechAnalysisService speechAnalysis;

    public SpeechAnalysisRecoveryScheduler(SpeechAnalysisService speechAnalysis) { this.speechAnalysis = speechAnalysis; }

    @Scheduled(initialDelayString = "${app.speech.stale-sweep-initial-delay:PT30S}", fixedDelayString = "${app.speech.stale-sweep-interval:PT5M}")
    public void recoverStaleAnalyses() {
        try {
            int recovered = speechAnalysis.recoverStaleAnalyses();
            if (recovered > 0) log.warn("Recovered {} speech analyses stuck in PROCESSING", recovered);
        } catch (RuntimeException e) {
            log.error("Stale speech analysis recovery failed: {}", LogMasking.describe(e));
        }
    }
}
