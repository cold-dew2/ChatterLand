package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment;
import com.example.backend.chld.dto.response.SpeechRecognitionResult.SpeechSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 검증 도구의 계산(기준 정답 값, 오차 부호, 집계, 전사자 일치도, 매니페스트 검사)을 손으로 계산한 값과 비교한다. */
class SpeechValidationTest {
    private static SpeechValidation.Recording recording(String id, String itemType, List<String> conditions, SpeechValidation.Annotation... annotations) {
        return new SpeechValidation.Recording(id, Path.of(id + ".wav"), "오늘은 날씨가 좋아요", "8-10", "GENERAL", itemType, conditions, List.of(annotations));
    }

    private static SpeechValidation.Annotation annotation(String annotator, Integer with, Integer without, String segments) {
        return new SpeechValidation.Annotation(annotator, with, without, SpeechValidation.segments(segments));
    }

    private static SpeechAssessment.SpeechMetrics measured(String rate, long utteranceMs, int pauses, long longest, long total) {
        return new SpeechAssessment.SpeechMetrics("speech-metrics-v1", new SpeechAssessment.PronunciationMetric("NOT_SUPPORTED", "NO_PRONUNCIATION_MODEL"),
                new SpeechAssessment.SpeakingRateMetric("MEASURED", null, 9, "ASR_TRANSCRIPT_SYLLABLES", utteranceMs, utteranceMs - total, new BigDecimal(rate), null),
                new SpeechAssessment.PauseMetric("MEASURED", null, pauses, total, longest, null, pauses + 1, 250));
    }

    @Test
    void referenceUsesTheSameDefinitionsAsTheSystem() {
        // 말소리 500~1500, 1800~2600(무음 300 = 멈춤), 2700~3500(무음 100 = 짧은 무음)
        SpeechValidation.ReferenceMetrics ref = SpeechValidation.reference(annotation("FINAL", 11, 9, "500-1500;1800-2600;2700-3500"), 250);
        assertEquals(3000L, ref.utteranceMs());
        assertEquals(2600L, ref.speechMs());
        assertEquals(1, ref.pauseCount());
        assertEquals(300L, ref.totalPauseMs());
        assertEquals(300L, ref.longestPauseMs());
        assertEquals(1, ref.shortSilenceCount());
        assertEquals(11 / 3.0, ref.rateWithDisfluency(), 1e-9); // 반복·간투어 포함 11음절
        assertEquals(3.0, ref.rateWithoutDisfluency(), 1e-9);
        assertEquals(0, SpeechValidation.reference(annotation("FINAL", 9, 9, "500-1000;1250-2000"), 250).pauseCount() - 1, "무음 250ms는 멈춤");
        assertEquals(0, SpeechValidation.reference(annotation("FINAL", 9, 9, "500-1000;1249-2000"), 250).pauseCount(), "무음 249ms는 멈춤 아님");
        assertNull(SpeechValidation.reference(annotation("FINAL", 0, 0, ""), 250).utteranceMs(), "말소리 없음");
    }

    @Test
    void errorsAreSystemMinusReferenceAndOnlyMeasuredRecordingsCount() {
        SpeechValidation.Recording rec = recording("r1", "SENTENCE", List.of("LONG_PAUSE"), annotation("FINAL", 11, 9, "500-1500;1800-2600;2700-3500"));
        SpeechValidation.ReferenceMetrics ref = SpeechValidation.reference(rec.reference(), 250);
        // VAD가 무음을 짧게 재고 반복을 지운 인식(9음절) 상황: 발화 3100ms, 멈춤 1회 최장 200ms… 를 흉내
        SpeechValidation.Row row = new SpeechValidation.Row(rec, "COMPLETED", null, null, 9, measured("2.90", 3100, 1, 200, 200), "NO_CANDIDATES", ref);
        assertEquals(2.90 - 11 / 3.0, row.rateError(true), 1e-9);
        assertEquals(2.90 - 3.0, row.rateError(false), 1e-9);
        assertEquals((2.90 - 11 / 3.0) / (11 / 3.0) * 100, row.rateRelativeErrorPct(true), 1e-9);
        assertEquals(0, row.pauseCountDiff());
        assertEquals(-100L, row.longestPauseError());
        assertEquals(-100L, row.totalPauseError());
        assertEquals(100L, row.utteranceError());
        SpeechValidation.Row failed = new SpeechValidation.Row(rec, "FAILED", "422 UNPROCESSABLE_CONTENT", null, null, null, null, ref);
        assertNull(failed.rateError(true));
        assertNull(failed.pauseCountDiff());
    }

    @Test
    void summariesReportBiasMaeMedianAndExactPauseMatchPerGroup() {
        SpeechValidation.Annotation ref = annotation("FINAL", 9, 9, "0-1000;1300-3000"); // 발화 3000ms → 3.0음절/초, 멈춤 1회 300ms
        SpeechValidation.Recording a = recording("a", "SENTENCE", List.of("QUIET"), ref), b = recording("b", "SENTENCE", List.of("NOISE"), ref),
                c = recording("c", "WORD", List.of("NOISE"), ref), d = recording("d", "SENTENCE", List.of("NOISE"), ref);
        SpeechValidation.ReferenceMetrics r = SpeechValidation.reference(ref, 250);
        List<SpeechValidation.Row> rows = List.of(
                new SpeechValidation.Row(a, "COMPLETED", null, null, 9, measured("3.30", 3000, 1, 200, 200), "NO_CANDIDATES", r),   // +0.3, 멈춤 일치
                new SpeechValidation.Row(b, "COMPLETED", null, null, 9, measured("2.70", 3000, 2, 400, 600), "NO_CANDIDATES", r),   // -0.3, 멈춤 +1
                new SpeechValidation.Row(c, "COMPLETED", null, null, 3, new SpeechAssessment.SpeechMetrics("v", null,
                        new SpeechAssessment.SpeakingRateMetric("NOT_SUPPORTED", "WORD_ITEM", null, null, null, null, null, null),
                        new SpeechAssessment.PauseMetric("NOT_SUPPORTED", "WORD_ITEM", null, null, null, null, null, 250)), "NO_CANDIDATES", r),
                new SpeechValidation.Row(d, "FAILED", "422", null, null, null, null, r));
        SpeechValidation.GroupSummary all = SpeechValidation.summarize("전체", rows);
        assertEquals(4, all.recordings());
        assertEquals(1, all.failed());
        assertEquals(2, all.rateMeasured());
        assertEquals(1, all.rateNotSupported(), "낱말 문항(해당 없음)");
        assertEquals(0, all.rateUnavailable(), "측정 불가와 해당 없음·분석 실패는 따로 센다");
        assertEquals(0, all.rateNoReference());
        assertEquals(0.0, all.rateBiasWith(), 1e-9);
        assertEquals(0.3, all.rateMaeWith(), 1e-9);
        assertEquals(10.0, all.medianAbsRelErrorPctWith(), 1e-9); // |±0.3|/3.0 = 10%
        assertEquals(2, all.pauseMeasured());
        assertEquals(50.0, all.pauseCountExactPct(), 1e-9);
        assertEquals(0.5, all.meanAbsPauseCountDiff(), 1e-9);
        assertEquals(0.0, all.longestPauseBiasMs(), 1e-9); // -100, +100
        assertEquals(100.0, all.longestPauseMaeMs(), 1e-9);
        assertEquals(200.0, all.totalPauseMaeMs(), 1e-9); // |-100|, |+300|
        List<SpeechValidation.GroupSummary> groups = SpeechValidation.summaries(rows);
        SpeechValidation.GroupSummary noise = groups.stream().filter(g -> g.group().equals("조건:NOISE")).findFirst().orElseThrow();
        assertEquals(3, noise.recordings());
        assertEquals(1, noise.rateMeasured());
        assertTrue(groups.stream().anyMatch(g -> g.group().equals("문항:WORD")));
        assertNull(SpeechValidation.summarize("빈 묶음", List.of()).rateMaeWith(), "측정값이 없으면 오차를 만들지 않는다");
    }

    @Test
    void annotatorAgreementComparesPauseCountsSyllablesAndBoundaries() {
        SpeechValidation.Recording rec = recording("r", "SENTENCE", List.of(),
                annotation("A", 10, 9, "500-930;1670-2720"), annotation("B", 10, 9, "510-940;1660-2730"),
                annotation("C", 11, 9, "500-2720"), annotation("FINAL", 10, 9, "500-930;1670-2720"));
        List<SpeechValidation.Agreement> agreements = SpeechValidation.agreements(rec, 250);
        assertEquals(3, agreements.size(), "FINAL을 뺀 전사자 3명 → 3쌍");
        SpeechValidation.Agreement ab = agreements.get(0);
        assertTrue(ab.pauseCountEqual());
        assertEquals(Boolean.TRUE, ab.syllablesEqual());
        assertEquals(10.0, ab.meanBoundaryDiffMs(), 1e-9);
        SpeechValidation.Agreement ac = agreements.get(1);
        assertFalse(ac.pauseCountEqual());
        assertEquals(Boolean.FALSE, ac.syllablesEqual());
        assertNull(ac.meanBoundaryDiffMs(), "구간 수가 다르면 경계 차이를 계산하지 않는다");
    }

    @Test
    void manifestIsParsedPerRecordingAndInvalidSegmentsAreRejected(@TempDir Path dir) throws Exception {
        Path manifest = dir.resolve("m.tsv");
        Files.writeString(manifest, "# 주석\n" + String.join("\t", SpeechValidation.HEADER) + "\n"
                + "r1\taudio/r1.wav\t오늘은 날씨가 좋아요.\t8-10\tGENERAL\tSENTENCE\tLONG_PAUSE|NOISE_TV\tA\t10\t9\t500-930;1670-2720\t\n"
                + "r1\t\t\t\t\t\t\tFINAL\t10\t9\t500-930;1670-2720\t조정\n"
                + "r2\taudio/r2.wav\t라디오\t5-7\tTHERAPY\tWORD\t\tFINAL\t3\t3\t500-950\t\n");
        List<SpeechValidation.Recording> recordings = SpeechValidation.readManifest(manifest, dir);
        assertEquals(2, recordings.size());
        assertEquals(dir.resolve("audio/r1.wav").toAbsolutePath().normalize(), recordings.get(0).audio());
        assertEquals(List.of("LONG_PAUSE", "NOISE_TV"), recordings.get(0).conditions());
        assertEquals(2, recordings.get(0).annotations().size());
        assertEquals(List.of(new SpeechSegment(500, 930), new SpeechSegment(1670, 2720)), recordings.get(0).reference().speech());
        assertEquals(List.of(), recordings.get(1).conditions());
        assertThrows(IllegalArgumentException.class, () -> SpeechValidation.segments("900-500"));
        assertThrows(IllegalArgumentException.class, () -> SpeechValidation.segments("500-1000;900-1200"));
        Path bad = dir.resolve("bad.tsv");
        Files.writeString(bad, "recording_id\taudio_path\n");
        assertThrows(SpeechValidation.InputException.class, () -> SpeechValidation.readManifest(bad, dir));
    }

    @Test
    void reportsOmitTranscriptsUnlessRequested() {
        SpeechValidation.Recording rec = recording("r", "SENTENCE", List.of(), annotation("FINAL", 9, 9, "0-3000"));
        List<SpeechValidation.Row> rows = List.of(new SpeechValidation.Row(rec, "COMPLETED", null, "오늘은 날씨가 좋아요", 9,
                measured("3.00", 3000, 0, 0, 0), "NO_CANDIDATES", SpeechValidation.reference(rec.reference(), 250)));
        String plain = SpeechValidation.resultsTsv(rows, false);
        assertFalse(plain.contains("transcript") || plain.contains("날씨"), "인식 텍스트는 기본으로 기록하지 않는다");
        assertTrue(SpeechValidation.resultsTsv(rows, true).contains("오늘은 날씨가 좋아요"));
        String summary = SpeechValidation.summaryMarkdown("t", rows, List.of(), new java.util.LinkedHashMap<>());
        assertTrue(summary.contains("합격·불합격 판정은 하지 않는다"));
        assertTrue(summary.contains("전사자 2명 이상이 표시한 녹음이 없어 계산하지 않았다"));
    }

    private static String manifestRow(String... cells) { return String.join("\t", cells) + "\n"; }

    @Test
    void manifestProblemsAreAllReportedWithLineNumbersAndNothingIsMeasured(@TempDir Path dir) throws Exception {
        Path manifest = dir.resolve("m.tsv");
        Files.writeString(manifest, String.join("\t", SpeechValidation.HEADER) + "\n"
                + manifestRow("ok", "a/ok.wav", "오늘은 날씨가 좋아요.", "8-10", "GENERAL", "SENTENCE", "", "FINAL", "9", "9", "500-1500", "")
                + manifestRow("", "a/x.wav", "x", "", "", "SENTENCE", "", "FINAL", "", "", "", "")                                  // 3줄: id 없음
                + manifestRow("neg", "a/neg.wav", "사과", "", "", "WORD", "", "FINAL", "-1", "", "500-900", "")                   // 4줄: 음수
                + manifestRow("nan", "a/nan.wav", "사과", "", "", "WORD", "", "FINAL", "두", "", "500-900", "")                   // 5줄: 정수 아님
                + manifestRow("order", "a/o.wav", "사과", "", "", "WORD", "", "FINAL", "2", "3", "500-900", "")                   // 6줄: 포함 < 제외
                + manifestRow("nospeech", "a/n.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "", "")                      // 7줄: 구간 없는데 음절
                + manifestRow("seg", "a/s.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "500-abc", "")                    // 8줄: 구간 형식
                + manifestRow("abs", dir.resolve("a/abs.wav").toString(), "사과", "", "", "WORD", "", "FINAL", "2", "2", "500-900", "") // 9줄: 절대 경로
                + manifestRow("escape", "../outside.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "500-900", "")         // 10줄: 폴더 밖
                + manifestRow("type", "a/t.wav", "사과", "", "", "PHRASE", "", "FINAL", "2", "2", "500-900", "")                // 11줄: item_type
                + manifestRow("dup", "a/d.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "500-900", "")
                + manifestRow("dup", "", "", "", "", "", "", "FINAL", "2", "2", "500-900", "")                                  // 13줄: 전사자 중복
                + manifestRow("dup", "a/other.wav", "다른 문장", "", "", "", "", "A", "2", "2", "500-900", "")                  // 14줄: 메타데이터 충돌
                + manifestRow("nofinal", "a/nf.wav", "사과", "", "", "WORD", "", "A", "2", "2", "500-900", "")                 // FINAL 없음
                + manifestRow("missing", "", "사과", "", "", "WORD", "", "FINAL", "2", "2", "500-900", "")                     // 16줄: audio_path 없음
                + String.join("\t", "extra", "a/e.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "500-900", "", "넘침") + "\n"); // 17줄: 열 초과
        SpeechValidation.InputException error = assertThrows(SpeechValidation.InputException.class, () -> SpeechValidation.readManifest(manifest, dir.resolve("a").getParent()));
        String all = String.join("\n", error.problems);
        for (String expected : List.of("3줄: recording_id가 비어", "4줄(neg): ref_syllables_with_disfluency는 0 이상", "5줄(nan): ref_syllables_with_disfluency는 정수",
                "6줄(order): 반복·간투어 포함 음절 수가 제외 음절 수보다 적습니다", "7줄(nospeech): 말소리 구간이 없는데", "8줄(seg): 말소리 구간은 0 이상의 정수",
                "9줄(abs): audio_path는 검증 녹음 폴더 기준 상대 경로", "10줄(escape): audio_path가 검증 녹음 폴더 밖", "11줄(type): item_type은 WORD 또는 SENTENCE",
                "13줄(dup): 전사자 FINAL의 표시가 이미", "14줄(dup): audio_path가 12줄과 다릅니다", "14줄(dup): target_text가 12줄과 다릅니다",
                "nofinal: annotator=FINAL", "16줄(missing): 녹음의 첫 행에 audio_path", "17줄: 열이 머리글보다 많습니다"))
            assertTrue(all.contains(expected), "빠진 문제: " + expected + "\n" + all);
        assertFalse(all.contains("(ok)"), "올바른 행은 문제로 보고하지 않는다");
        assertThrows(SpeechValidation.InputException.class, () -> SpeechValidation.readManifest(write(dir, "empty.tsv", "# 주석만\n"), dir));
        assertThrows(IllegalArgumentException.class, () -> SpeechValidation.segments("500-1000;1000-1200"), "맞닿은 구간은 하나로 표시");
        assertThrows(IllegalArgumentException.class, () -> SpeechValidation.segments("-10-500"));
    }

    private static Path write(Path dir, String name, String content) throws Exception {
        return Files.writeString(dir.resolve(name), content);
    }

    /** 16-bit PCM mono 16kHz 무음 WAV */
    private static void wav(Path file, int ms) throws Exception {
        int dataSize = 16 * ms * 2;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(dataSize);
        Files.createDirectories(file.getParent());
        Files.write(file, b.array());
    }

    @Test
    void audioIsCheckedBeforeMeasuringAndServiceStorageAndOutsideLinksAreRefused(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("validation"), storage = dir.resolve("storage-audio");
        wav(root.resolve("ok.wav"), 2000);
        wav(root.resolve("short.wav"), 1000);
        Files.writeString(root.resolve("text.wav"), "not audio");
        wav(storage.resolve("unknown.wav"), 1000);
        wav(dir.resolve("outside.wav"), 1000);
        Files.createSymbolicLink(root.resolve("link.wav"), dir.resolve("outside.wav"));
        Files.createSymbolicLink(root.resolve("stored"), storage);
        Path manifest = write(root, "m.tsv", String.join("\t", SpeechValidation.HEADER) + "\n"
                + manifestRow("ok", "ok.wav", "오늘은 날씨가 좋아요.", "", "", "SENTENCE", "", "FINAL", "9", "9", "500-1500", "")
                + manifestRow("long", "short.wav", "오늘은 날씨가 좋아요.", "", "", "SENTENCE", "", "FINAL", "9", "9", "500-1500", "")
                + manifestRow("gone", "missing.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "100-300", "")
                + manifestRow("text", "text.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "100-300", "")
                + manifestRow("link", "link.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "100-300", "")
                + manifestRow("stored", "stored/unknown.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "100-300", ""));
        List<SpeechValidation.Recording> recordings = SpeechValidation.readManifest(manifest, root);
        SpeechValidation.InputException error = assertThrows(SpeechValidation.InputException.class,
                () -> SpeechValidation.checkAudio(recordings, root, List.of(storage)));
        String all = String.join("\n", error.problems);
        assertTrue(all.contains("long: 전사자 FINAL의 말소리 구간이 녹음 길이(1000ms)를 넘습니다"), all);
        assertTrue(all.contains("gone: 녹음 파일이 없습니다(missing.wav)"), all);
        assertTrue(all.contains("text: 16-bit PCM WAV가 아닙니다"), all);
        assertTrue(all.contains("link: 녹음 파일의 실제 위치가 검증 녹음 폴더 밖입니다"), all);
        assertTrue(all.contains("stored: 녹음 파일의 실제 위치가 검증 녹음 폴더 밖입니다") || all.contains("stored: 서비스 녹음 저장소"), all);
        assertFalse(all.contains("ok:"), all);
        assertFalse(all.contains(dir.toString()), "문제 보고에 절대 경로를 적지 않는다");
        // 검증 폴더 안에 서비스 저장소가 있어도 거부한다
        wav(root.resolve("storage/audio/kept.wav"), 1000);
        Path inside = write(root, "inside.tsv", String.join("\t", SpeechValidation.HEADER) + "\n"
                + manifestRow("kept", "storage/audio/kept.wav", "사과", "", "", "WORD", "", "FINAL", "2", "2", "100-300", ""));
        SpeechValidation.InputException refused = assertThrows(SpeechValidation.InputException.class,
                () -> SpeechValidation.checkAudio(SpeechValidation.readManifest(inside, root), root, List.of(root.resolve("storage/audio"))));
        assertTrue(refused.problems.get(0).contains("서비스 녹음 저장소의 파일은 검증에 쓰지 않습니다"));
        SpeechValidation.checkAudio(List.of(recordings.get(0)), root, List.of(storage)); // 올바른 녹음은 통과
    }

    @Test
    void eachRunGetsANewOutputDirectoryAndExistingResultsAreKept(@TempDir Path dir) throws Exception {
        Path first = SpeechValidationRun.newOutputDirectory(dir);
        Files.writeString(first.resolve("summary.md"), "이전 결과");
        Path second = SpeechValidationRun.newOutputDirectory(dir);
        Path third = SpeechValidationRun.newOutputDirectory(dir);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
        assertEquals("이전 결과", Files.readString(first.resolve("summary.md")));
        assertTrue(SpeechValidationRun.deniedAudioRoots().contains(Path.of("storage", "audio")));
        assertTrue(SpeechValidationRun.deniedAudioRoots().contains(Path.of("build", "test-db", "audio")));
    }

    @Test
    void reportsCarryInputFingerprintsInsteadOfContent() {
        SpeechValidation.Recording rec = recording("r", "SENTENCE", List.of(), annotation("FINAL", 9, 9, "0-3000"));
        List<SpeechValidation.Row> rows = List.of(new SpeechValidation.Row(rec, "COMPLETED", null, "오늘은 날씨가 좋아요", 9,
                measured("3.00", 3000, 0, 0, 0), "NO_CANDIDATES", SpeechValidation.reference(rec.reference(), 250)));
        String tsv = SpeechValidation.resultsTsv(rows, false, java.util.Map.of("r", "abc123"));
        assertTrue(tsv.lines().findFirst().orElseThrow().startsWith("recording_id\taudio_sha256\t"));
        assertTrue(tsv.lines().skip(1).findFirst().orElseThrow().startsWith("r\tabc123\t"));
        assertEquals(tsv.lines().findFirst().orElseThrow().split("\t").length, tsv.lines().skip(1).findFirst().orElseThrow().split("\t", -1).length,
                "머리글과 값의 열 수가 같다(입력과 결과 연결)");
    }
}
