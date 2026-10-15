package com.example.backend.chld.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.backend.support.TestPrerequisites.requireSpeech;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 검증 도구 자체 점검: 성인 합성음 매니페스트(speech-validation/synthetic-selftest.tsv)를 실제 whisper.cpp+VAD로 측정해
 * 도구가 운영과 같은 상태(측정·해당 없음·측정 불가·분석 실패)를 만들고 보고서를 쓰는지 확인한다.
 * 아동 음성 검증이 아니며, 여기의 오차 값은 측정 정확도 근거로 쓰지 않는다.
 */
class SpeechValidationHarnessSelfTest {
    @Test
    void harnessMeasuresSyntheticFixturesLikeProductionAndWritesReports(@TempDir Path out) throws Exception {
        requireSpeech(Files.isReadable(Path.of(SpeechValidationRun.MODEL)) && Files.isReadable(Path.of(SpeechValidationRun.VAD_MODEL)), "Whisper/VAD 모델이 없어 테스트를 건너뜁니다.");
        LocalWhisperRecognitionService recognizer = SpeechValidationRun.recognizer();
        requireSpeech(recognizer.vadEnabled(), "whisper-vad-speech-segments 실행 파일이 없어 테스트를 건너뜁니다.");
        Path manifest = Path.of(getClass().getResource("/speech-validation/synthetic-selftest.tsv").toURI());
        Path audioRoot = manifest.getParent().getParent(); // 검증 녹음 폴더 = 테스트 리소스(speech/ 합성음)
        List<SpeechValidation.Recording> recordings = SpeechValidation.readManifest(manifest, audioRoot);
        SpeechValidation.checkAudio(recordings, audioRoot, SpeechValidationRun.deniedAudioRoots());
        java.util.Map<Path, String> before = new java.util.LinkedHashMap<>();
        before.put(manifest, SpeechValidation.sha256(manifest));
        for (SpeechValidation.Recording r : recordings) before.put(r.audio(), SpeechValidation.sha256(r.audio()));
        List<SpeechValidation.Row> rows = SpeechValidationRun.measure(recordings, recognizer, SpeechAssessmentEvaluator.PAUSE_THRESHOLD_MS);
        Map<String, SpeechValidation.Row> byId = rows.stream().collect(Collectors.toMap(r -> r.recording().id(), Function.identity()));

        SpeechValidation.Row pause = byId.get("syn-pause");
        assertEquals("COMPLETED", pause.analysisStatus());
        assertTrue(pause.rateMeasured() && pause.pausesMeasured());
        assertEquals(0, pause.pauseCountDiff(), "멈춤 1회를 기준과 같게 센다");
        // VAD는 무음을 실제보다 짧게 잰다(기준 740ms). 부호와 대략의 크기만 확인한다.
        assertTrue(pause.longestPauseError() < 0 && pause.longestPauseError() > -300, "longestPauseError=" + pause.longestPauseError());
        assertEquals(0, byId.get("syn-fluent").pauseCountDiff());
        assertEquals("NOT_SUPPORTED", byId.get("syn-word").rate().status());
        assertEquals("UNAVAILABLE", byId.get("syn-cut").rate().status());
        assertEquals("ASSESSMENT_HOLD", byId.get("syn-cut").rate().reason());
        assertNull(byId.get("syn-cut").rateError(true), "음절 수 기준이 없는 녹음은 오차를 만들지 않는다");
        assertEquals("FAILED", byId.get("syn-silence").analysisStatus());

        // 재현성: 같은 입력을 다시 측정하면 같은 상태·측정값·인식 결과
        List<SpeechValidation.Row> again = SpeechValidationRun.measure(recordings, recognizer, SpeechAssessmentEvaluator.PAUSE_THRESHOLD_MS);
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(rows.get(i).analysisStatus(), again.get(i).analysisStatus());
            assertEquals(rows.get(i).system(), again.get(i).system(), rows.get(i).recording().id());
            assertEquals(rows.get(i).transcript(), again.get(i).transcript(), rows.get(i).recording().id());
        }
        SpeechValidationRun.write(out, manifest, recognizer, rows, recordings, false);
        // 입력(매니페스트·녹음)은 바뀌지 않았고 결과 폴더에 복사되지 않았다
        for (java.util.Map.Entry<Path, String> entry : before.entrySet()) assertEquals(entry.getValue(), SpeechValidation.sha256(entry.getKey()));
        try (var files = Files.list(out)) { assertEquals(List.of("results.tsv", "summary.md"), files.map(f -> f.getFileName().toString()).sorted().toList()); }
        String results = Files.readString(out.resolve("results.tsv"));
        String summary = Files.readString(out.resolve("summary.md"));
        assertEquals(rows.size() + 1, results.lines().count());
        assertFalse(results.contains("transcript") || results.contains("날씨"), "인식 텍스트는 기본으로 기록하지 않는다");
        assertTrue(summary.contains("| 전체 |") && summary.contains("조건:LONG_PAUSE") && summary.contains("문항:WORD"));
        assertTrue(summary.contains("전사자 쌍: 1"), "syn-pause의 전사자 2명 → 1쌍");
        assertTrue(summary.contains("speech-metrics-v1"));
        assertTrue(summary.contains(before.get(manifest)), "매니페스트 SHA-256 기록");
        assertTrue(results.contains(before.get(recordings.get(0).audio())), "녹음 SHA-256 기록");
        assertFalse(summary.contains(audioRoot.toString()) || results.contains(audioRoot.toString()), "보고서에 녹음 폴더 절대 경로를 적지 않는다");
    }
}
