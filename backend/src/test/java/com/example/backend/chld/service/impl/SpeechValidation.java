package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.response.SpeechAssessment;
import com.example.backend.chld.dto.response.SpeechRecognitionResult.SpeechSegment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 말하기 측정값(속도·멈춤) 검증 도구: 기준 정답(사람이 표시한 말소리 구간·음절 수)과 시스템 측정값을 비교한다.
 * 검증 실행(SpeechValidationRun)과 단위 테스트가 함께 쓴다. 운영 코드가 아니며 합격·불합격 기준(허용 오차)은 정하지 않는다
 * (허용 오차·멈춤 기준의 아동 적용은 전문가 검토 항목, docs/design/speech-metrics.md 8장).
 *
 * 매니페스트(TSV, UTF-8, 첫 줄 머리글): 한 녹음에 전사자별로 여러 행. annotator=FINAL 행이 조정된 기준 정답이다.
 * recording_id, audio_path(매니페스트 기준 상대 경로 가능), target_text, age_band, learner_type, item_type, conditions(|로 구분),
 * annotator, ref_syllables_with_disfluency, ref_syllables_without_disfluency, ref_speech_segments(예: 500-930;1670-2720, ms), notes
 */
final class SpeechValidation {
    static final String FINAL = "FINAL";
    static final List<String> HEADER = List.of("recording_id", "audio_path", "target_text", "age_band", "learner_type", "item_type", "conditions",
            "annotator", "ref_syllables_with_disfluency", "ref_syllables_without_disfluency", "ref_speech_segments", "notes");

    private SpeechValidation() { }

    /** 전사자 한 명의 표시 */
    record Annotation(String annotator, Integer syllablesWithDisfluency, Integer syllablesWithoutDisfluency, List<SpeechSegment> speech) { }

    /** 녹음 한 건과 전사자별 표시 */
    record Recording(String id, Path audio, String targetText, String ageBand, String learnerType, String itemType, List<String> conditions,
                     List<Annotation> annotations) {
        Annotation reference() { return annotations.stream().filter(a -> FINAL.equals(a.annotator())).findFirst().orElse(null); }
        List<Annotation> annotators() { return annotations.stream().filter(a -> !FINAL.equals(a.annotator())).toList(); }
    }

    /** 기준 정답에서 시스템과 같은 정의로 계산한 값(발화 구간 = 첫 말소리 시작~마지막 말소리 끝, 멈춤 = 구간 사이 무음 ≥ 기준) */
    record ReferenceMetrics(Long utteranceMs, Long speechMs, Integer pauseCount, Long totalPauseMs, Long longestPauseMs,
                            Integer shortSilenceCount, Double rateWithDisfluency, Double rateWithoutDisfluency) { }

    /** 녹음 한 건의 비교 결과. analysisStatus: COMPLETED·FAILED */
    record Row(Recording recording, String analysisStatus, String failureCode, String transcript, Integer systemSyllables,
               SpeechAssessment.SpeechMetrics system, String assessmentStatus, ReferenceMetrics reference) {
        SpeechAssessment.SpeakingRateMetric rate() { return system == null ? null : system.speakingRate(); }
        SpeechAssessment.PauseMetric pauses() { return system == null ? null : system.pauses(); }
        boolean rateMeasured() { return rate() != null && SpeechAssessment.METRIC_MEASURED.equals(rate().status()); }
        boolean pausesMeasured() { return pauses() != null && SpeechAssessment.METRIC_MEASURED.equals(pauses().status()); }
        Double rateError(boolean withDisfluency) {
            Double ref = reference == null ? null : withDisfluency ? reference.rateWithDisfluency() : reference.rateWithoutDisfluency();
            return rateMeasured() && ref != null ? rate().syllablesPerSecond().doubleValue() - ref : null;
        }
        Double rateRelativeErrorPct(boolean withDisfluency) {
            Double error = rateError(withDisfluency);
            Double ref = reference == null ? null : withDisfluency ? reference.rateWithDisfluency() : reference.rateWithoutDisfluency();
            return error == null || ref == null || ref == 0 ? null : error / ref * 100;
        }
        Integer pauseCountDiff() { return pausesMeasured() && reference != null && reference.pauseCount() != null ? pauses().pauseCount() - reference.pauseCount() : null; }
        Long longestPauseError() { return pausesMeasured() && reference != null && reference.longestPauseMs() != null ? pauses().longestPauseMs() - reference.longestPauseMs() : null; }
        Long totalPauseError() { return pausesMeasured() && reference != null && reference.totalPauseMs() != null ? pauses().totalPauseMs() - reference.totalPauseMs() : null; }
        Long utteranceError() { return rate() != null && rate().utteranceMs() != null && reference != null && reference.utteranceMs() != null ? rate().utteranceMs() - reference.utteranceMs() : null; }
    }

    // ── 매니페스트 ─────────────────────────────

    static final java.util.Set<String> ITEM_TYPES = java.util.Set.of(SpeechAssessment.TYPE_WORD, SpeechAssessment.TYPE_SENTENCE);
    /** 기준 구간 끝이 녹음 길이를 넘어도 되는 여유(표시 도구의 10ms 반올림) */
    static final long SEGMENT_TOLERANCE_MS = 10;

    /** 매니페스트·녹음 입력 오류. 문제를 모두 모아(줄 번호 포함) 한 번에 알린다. 하나라도 있으면 측정하지 않는다. */
    static final class InputException extends IllegalArgumentException {
        final List<String> problems;
        InputException(List<String> problems) {
            super("검증 입력 오류 " + problems.size() + "건:\n- " + String.join("\n- ", problems));
            this.problems = List.copyOf(problems);
        }
    }

    /**
     * 매니페스트를 읽고 형식을 검사한다. audio_path는 audioRoot 기준 상대 경로(절대 경로·root 밖 경로는 거부).
     * 검사: 머리글, 열 개수, 필수 값(recording_id·audio_path·target_text·item_type·annotator), item_type(WORD·SENTENCE),
     * 숫자 형식·음수, 음절(포함) ≥ 음절(제외), 말소리 없음인데 음절 > 0, 구간 형식·순서, 같은 녹음의 메타데이터 충돌,
     * 같은 녹음의 전사자 중복, FINAL(조정된 기준 정답) 누락.
     */
    static List<Recording> readManifest(Path manifest, Path audioRoot) throws IOException {
        List<String> all = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        List<String> problems = new ArrayList<>();
        Path root = audioRoot.toAbsolutePath().normalize();
        List<String> header = null;
        Map<String, Recording> recordings = new LinkedHashMap<>();
        Map<String, Integer> firstLine = new LinkedHashMap<>();
        for (int index = 0; index < all.size(); index++) {
            String line = all.get(index);
            int lineNo = index + 1;
            if (line.isBlank() || line.startsWith("#")) continue;
            if (header == null) {
                header = Arrays.asList(line.split("\t", -1));
                if (!header.containsAll(HEADER.subList(0, 11)))
                    throw new InputException(List.of("머리글이 다릅니다(" + lineNo + "줄). 필요한 열: " + String.join(", ", HEADER.subList(0, 11))));
                continue;
            }
            String[] cells = line.split("\t", -1);
            if (cells.length > header.size()) { problems.add(lineNo + "줄: 열이 머리글보다 많습니다(" + cells.length + " > " + header.size() + ")"); continue; }
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i), i < cells.length ? cells[i].trim() : "");
            String id = row.get("recording_id"), annotator = row.get("annotator");
            if (id.isEmpty()) { problems.add(lineNo + "줄: recording_id가 비어 있습니다"); continue; }
            if (annotator.isEmpty()) problems.add(lineNo + "줄(" + id + "): annotator가 비어 있습니다");
            Integer with = number(row.get("ref_syllables_with_disfluency"), lineNo, id, "ref_syllables_with_disfluency", problems);
            Integer without = number(row.get("ref_syllables_without_disfluency"), lineNo, id, "ref_syllables_without_disfluency", problems);
            if (with != null && without != null && with < without) problems.add(lineNo + "줄(" + id + "): 반복·간투어 포함 음절 수가 제외 음절 수보다 적습니다");
            List<SpeechSegment> speech;
            try { speech = segments(row.get("ref_speech_segments")); }
            catch (IllegalArgumentException e) { problems.add(lineNo + "줄(" + id + "): " + e.getMessage()); continue; }
            if (speech.isEmpty() && ((with != null && with > 0) || (without != null && without > 0)))
                problems.add(lineNo + "줄(" + id + "): 말소리 구간이 없는데 음절 수가 0보다 큽니다");

            Recording recording = recordings.get(id);
            if (recording == null) {
                String audio = row.get("audio_path"), target = row.get("target_text"), itemType = row.get("item_type");
                List<String> missing = new ArrayList<>();
                if (audio.isEmpty()) missing.add("audio_path");
                if (target.isEmpty()) missing.add("target_text");
                if (itemType.isEmpty()) missing.add("item_type");
                if (!missing.isEmpty()) { problems.add(lineNo + "줄(" + id + "): 녹음의 첫 행에 " + String.join("·", missing) + " 값이 필요합니다"); continue; }
                if (!ITEM_TYPES.contains(itemType)) problems.add(lineNo + "줄(" + id + "): item_type은 WORD 또는 SENTENCE입니다(" + itemType + ")");
                Path audioPath = Path.of(audio);
                if (audioPath.isAbsolute()) { problems.add(lineNo + "줄(" + id + "): audio_path는 검증 녹음 폴더 기준 상대 경로로 적어야 합니다"); continue; }
                Path resolved = root.resolve(audioPath).normalize();
                if (!resolved.startsWith(root)) { problems.add(lineNo + "줄(" + id + "): audio_path가 검증 녹음 폴더 밖을 가리킵니다(" + audio + ")"); continue; }
                recording = new Recording(id, resolved, target, row.get("age_band"), row.get("learner_type"), itemType,
                        row.get("conditions").isEmpty() ? List.of() : List.of(row.get("conditions").split("\\|")), new ArrayList<>());
                recordings.put(id, recording);
                firstLine.put(id, lineNo);
            } else {
                // 같은 녹음의 다음 행: 메타데이터는 비우거나 첫 행과 같아야 한다
                for (String column : List.of("audio_path", "target_text", "age_band", "learner_type", "item_type", "conditions")) {
                    String value = row.get(column), first = switch (column) {
                        case "audio_path" -> null;
                        case "target_text" -> recording.targetText();
                        case "age_band" -> recording.ageBand();
                        case "learner_type" -> recording.learnerType();
                        case "item_type" -> recording.itemType();
                        default -> String.join("|", recording.conditions());
                    };
                    if (column.equals("audio_path") && !value.isEmpty() && !root.resolve(value).normalize().equals(recording.audio()))
                        problems.add(lineNo + "줄(" + id + "): audio_path가 " + firstLine.get(id) + "줄과 다릅니다");
                    else if (first != null && !value.isEmpty() && !value.equals(first))
                        problems.add(lineNo + "줄(" + id + "): " + column + "가 " + firstLine.get(id) + "줄과 다릅니다");
                }
            }
            if (recording.annotations().stream().anyMatch(a -> a.annotator().equals(annotator)))
                problems.add(lineNo + "줄(" + id + "): 전사자 " + annotator + "의 표시가 이미 있습니다");
            recording.annotations().add(new Annotation(annotator, with, without, speech));
        }
        if (header == null) throw new InputException(List.of("매니페스트가 비어 있습니다"));
        for (Recording recording : recordings.values())
            if (recording.reference() == null) problems.add(recording.id() + ": annotator=FINAL(조정된 기준 정답) 행이 없습니다");
        if (recordings.isEmpty() && problems.isEmpty()) problems.add("녹음 행이 없습니다");
        if (!problems.isEmpty()) throw new InputException(problems);
        return List.copyOf(recordings.values());
    }

    /**
     * 녹음 파일 검사(측정 전): 존재·일반 파일, 실제 경로(심볼릭 링크 해석)가 검증 녹음 폴더 안, 서비스 녹음 저장소가 아님,
     * 16-bit PCM WAV, 기준 구간 끝 ≤ 녹음 길이. 녹음 내용은 측정 외에는 읽지 않는다.
     */
    static void checkAudio(List<Recording> recordings, Path audioRoot, List<Path> deniedRoots) throws IOException {
        List<String> problems = new ArrayList<>();
        Path root = audioRoot.toRealPath(), declaredRoot = audioRoot.toAbsolutePath().normalize();
        List<Path> denied = new ArrayList<>();
        for (Path path : deniedRoots) if (Files.exists(path)) denied.add(path.toRealPath());
        for (Recording recording : recordings) {
            Path audio = recording.audio();
            if (!Files.isRegularFile(audio)) { problems.add(recording.id() + ": 녹음 파일이 없습니다(" + declaredRoot.relativize(audio.toAbsolutePath().normalize()) + ")"); continue; }
            Path real = audio.toRealPath();
            if (!real.startsWith(root)) { problems.add(recording.id() + ": 녹음 파일의 실제 위치가 검증 녹음 폴더 밖입니다(링크 확인)"); continue; }
            if (denied.stream().anyMatch(real::startsWith)) { problems.add(recording.id() + ": 서비스 녹음 저장소의 파일은 검증에 쓰지 않습니다"); continue; }
            long durationMs;
            try { durationMs = WavAudio.read(real).durationMs(); }
            catch (org.springframework.web.server.ResponseStatusException e) { problems.add(recording.id() + ": 16-bit PCM WAV가 아닙니다"); continue; }
            for (Annotation annotation : recording.annotations())
                if (!annotation.speech().isEmpty() && annotation.speech().get(annotation.speech().size() - 1).endMs() > durationMs + SEGMENT_TOLERANCE_MS)
                    problems.add(recording.id() + ": 전사자 " + annotation.annotator() + "의 말소리 구간이 녹음 길이(" + durationMs + "ms)를 넘습니다");
        }
        if (!problems.isEmpty()) throw new InputException(problems);
    }

    static List<SpeechSegment> segments(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<SpeechSegment> segments = new ArrayList<>();
        for (String part : text.split(";")) {
            String[] range = part.trim().split("-");
            if (range.length != 2) throw new IllegalArgumentException("말소리 구간 형식은 시작-끝(ms)입니다: " + part.trim());
            long start, end;
            try { start = Long.parseLong(range[0].trim()); end = Long.parseLong(range[1].trim()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("말소리 구간은 0 이상의 정수 ms입니다: " + part.trim()); }
            if (start < 0) throw new IllegalArgumentException("말소리 구간은 0 이상의 정수 ms입니다: " + part.trim());
            if (end <= start) throw new IllegalArgumentException("말소리 구간의 끝이 시작보다 빠릅니다: " + part.trim());
            if (!segments.isEmpty() && start <= segments.get(segments.size() - 1).endMs())
                throw new IllegalArgumentException("말소리 구간이 겹치거나 맞닿거나 순서가 다릅니다(맞닿은 구간은 하나로 표시): " + text);
            segments.add(new SpeechSegment(start, end));
        }
        return segments;
    }

    private static Integer number(String text, int lineNo, String id, String column, List<String> problems) {
        if (text == null || text.isBlank()) return null;
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 0) { problems.add(lineNo + "줄(" + id + "): " + column + "는 0 이상이어야 합니다"); return null; }
            return value;
        } catch (NumberFormatException e) {
            problems.add(lineNo + "줄(" + id + "): " + column + "는 정수여야 합니다(" + text.trim() + ")");
            return null;
        }
    }

    /** 입력 재현 확인용 지문(내용 대신 SHA-256만 기록) */
    static String sha256(Path file) throws IOException {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── 기준 정답 계산·비교 ─────────────────────────────

    static ReferenceMetrics reference(Annotation annotation, long pauseThresholdMs) {
        if (annotation == null || annotation.speech().isEmpty()) return new ReferenceMetrics(null, null, null, null, null, null, null, null);
        List<SpeechSegment> speech = annotation.speech();
        long utterance = speech.get(speech.size() - 1).endMs() - speech.get(0).startMs();
        long speechMs = 0, total = 0, longest = 0;
        int pauses = 0, shortSilences = 0;
        for (int i = 0; i < speech.size(); i++) {
            speechMs += speech.get(i).endMs() - speech.get(i).startMs();
            if (i == 0) continue;
            long gap = speech.get(i).startMs() - speech.get(i - 1).endMs();
            if (gap >= pauseThresholdMs) { pauses++; total += gap; longest = Math.max(longest, gap); }
            else if (gap > 0) shortSilences++;
        }
        return new ReferenceMetrics(utterance, speechMs, pauses, total, longest, shortSilences,
                rate(annotation.syllablesWithDisfluency(), utterance), rate(annotation.syllablesWithoutDisfluency(), utterance));
    }

    private static Double rate(Integer syllables, long ms) { return syllables == null || ms <= 0 ? null : syllables * 1000.0 / ms; }

    /** 전사자 간 일치도: 녹음마다 전사자 쌍의 멈춤 수·음절 수 일치 여부와 구간 경계 차이(구간 수가 같을 때만) */
    record Agreement(String recordingId, String first, String second, boolean pauseCountEqual, Boolean syllablesEqual, Double meanBoundaryDiffMs) { }

    static List<Agreement> agreements(Recording recording, long pauseThresholdMs) {
        List<Agreement> result = new ArrayList<>();
        List<Annotation> annotators = recording.annotators();
        for (int i = 0; i < annotators.size(); i++)
            for (int j = i + 1; j < annotators.size(); j++) {
                Annotation a = annotators.get(i), b = annotators.get(j);
                ReferenceMetrics ra = reference(a, pauseThresholdMs), rb = reference(b, pauseThresholdMs);
                Double boundary = null;
                if (a.speech().size() == b.speech().size() && !a.speech().isEmpty()) {
                    double sum = 0;
                    for (int k = 0; k < a.speech().size(); k++)
                        sum += Math.abs(a.speech().get(k).startMs() - b.speech().get(k).startMs()) + Math.abs(a.speech().get(k).endMs() - b.speech().get(k).endMs());
                    boundary = sum / (a.speech().size() * 2);
                }
                Boolean syllables = a.syllablesWithDisfluency() == null || b.syllablesWithDisfluency() == null ? null
                        : a.syllablesWithDisfluency().equals(b.syllablesWithDisfluency());
                result.add(new Agreement(recording.id(), a.annotator(), b.annotator(), Objects.equals(ra.pauseCount(), rb.pauseCount()), syllables, boundary));
            }
        return result;
    }

    // ── 집계 ─────────────────────────────

    /** 묶음별 요약. 오차 = 시스템 − 기준 정답(양수면 시스템이 크게 잼). 측정된 녹음만 오차에 넣는다. */
    record GroupSummary(String group, int recordings, int failed, int rateMeasured, int rateNotSupported, int rateUnavailable, int rateNoReference,
                        Double rateBiasWith, Double rateMaeWith,
                        Double rateBiasWithout, Double rateMaeWithout, Double medianAbsRelErrorPctWith, int pauseMeasured, Double pauseCountExactPct,
                        Double meanAbsPauseCountDiff, Double longestPauseBiasMs, Double longestPauseMaeMs, Double totalPauseMaeMs, Double utteranceBiasMs) { }

    static GroupSummary summarize(String group, List<Row> rows) {
        List<Double> errWith = values(rows, r -> r.rateError(true)), errWithout = values(rows, r -> r.rateError(false));
        List<Double> rel = values(rows, r -> r.rateRelativeErrorPct(true)).stream().map(Math::abs).sorted().toList();
        List<Double> countDiff = values(rows, r -> r.pauseCountDiff() == null ? null : r.pauseCountDiff().doubleValue());
        List<Double> longest = values(rows, r -> r.longestPauseError() == null ? null : r.longestPauseError().doubleValue());
        List<Double> total = values(rows, r -> r.totalPauseError() == null ? null : r.totalPauseError().doubleValue());
        List<Double> utterance = values(rows, r -> r.utteranceError() == null ? null : r.utteranceError().doubleValue());
        int failed = (int) rows.stream().filter(r -> "FAILED".equals(r.analysisStatus())).count();
        int rateMeasured = (int) rows.stream().filter(Row::rateMeasured).count();
        int notSupported = (int) rows.stream().filter(r -> r.rate() != null && SpeechAssessment.METRIC_NOT_SUPPORTED.equals(r.rate().status())).count();
        int unavailable = (int) rows.stream().filter(r -> r.rate() != null && SpeechAssessment.METRIC_UNAVAILABLE.equals(r.rate().status())).count();
        int noReference = (int) rows.stream().filter(r -> r.rateMeasured() && r.rateError(true) == null && r.rateError(false) == null).count();
        return new GroupSummary(group, rows.size(), failed, rateMeasured, notSupported, unavailable, noReference,
                mean(errWith), meanAbs(errWith), mean(errWithout), meanAbs(errWithout), median(rel),
                countDiff.size(), countDiff.isEmpty() ? null : countDiff.stream().filter(d -> d == 0).count() * 100.0 / countDiff.size(),
                meanAbs(countDiff), mean(longest), meanAbs(longest), meanAbs(total), mean(utterance));
    }

    /** 전체·문항 유형·연령대·학습자 유형·조건 태그별 요약 */
    static List<GroupSummary> summaries(List<Row> rows) {
        List<GroupSummary> result = new ArrayList<>();
        result.add(summarize("전체", rows));
        addGroups(result, rows, "문항", r -> List.of(r.recording().itemType()));
        addGroups(result, rows, "연령대", r -> List.of(r.recording().ageBand()));
        addGroups(result, rows, "학습자", r -> List.of(r.recording().learnerType()));
        addGroups(result, rows, "조건", r -> r.recording().conditions());
        return result;
    }

    private static void addGroups(List<GroupSummary> result, List<Row> rows, String axis, Function<Row, List<String>> keys) {
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row row : rows) for (String key : keys.apply(row)) if (key != null && !key.isBlank()) groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        groups.forEach((key, members) -> result.add(summarize(axis + ":" + key, members)));
    }

    private static List<Double> values(List<Row> rows, Function<Row, Double> value) {
        return rows.stream().map(value).filter(Objects::nonNull).toList();
    }

    static Double mean(List<Double> values) { return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow(); }
    static Double meanAbs(List<Double> values) { return values.isEmpty() ? null : values.stream().mapToDouble(Math::abs).average().orElseThrow(); }
    static Double median(List<Double> sorted) {
        if (sorted.isEmpty()) return null;
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    // ── 보고서 ─────────────────────────────

    static String resultsTsv(List<Row> rows, boolean includeTranscript) { return resultsTsv(rows, includeTranscript, Map.of()); }

    /** 녹음별 결과. audioHashes(녹음 ID → SHA-256)는 같은 입력으로 다시 실행했는지 확인하는 용도다(녹음 내용은 기록하지 않음). */
    static String resultsTsv(List<Row> rows, boolean includeTranscript, Map<String, String> audioHashes) {
        List<String> header = new ArrayList<>(List.of("recording_id", "audio_sha256", "age_band", "learner_type", "item_type", "conditions", "analysis_status", "failure_code",
                "assessment_status", "rate_status", "rate_reason", "pause_status", "pause_reason", "sys_syllables", "ref_syllables_with", "ref_syllables_without",
                "sys_utterance_ms", "ref_utterance_ms", "sys_rate", "ref_rate_with", "ref_rate_without", "rate_err_with", "rate_err_without", "rate_relerr_with_pct",
                "sys_pauses", "ref_pauses", "ref_short_silences", "pause_count_diff", "sys_longest_pause_ms", "ref_longest_pause_ms", "longest_pause_err_ms",
                "sys_total_pause_ms", "ref_total_pause_ms", "total_pause_err_ms"));
        if (includeTranscript) header.add("transcript");
        StringBuilder out = new StringBuilder(String.join("\t", header)).append('\n');
        for (Row r : rows) {
            Recording rec = r.recording();
            ReferenceMetrics ref = r.reference();
            Annotation finalAnnotation = rec.reference();
            List<Object> cells = new ArrayList<>(Arrays.asList(rec.id(), audioHashes.get(rec.id()), rec.ageBand(), rec.learnerType(), rec.itemType(), String.join("|", rec.conditions()),
                    r.analysisStatus(), r.failureCode(), r.assessmentStatus(),
                    r.rate() == null ? null : r.rate().status(), r.rate() == null ? null : r.rate().reason(),
                    r.pauses() == null ? null : r.pauses().status(), r.pauses() == null ? null : r.pauses().reason(),
                    r.systemSyllables(), finalAnnotation == null ? null : finalAnnotation.syllablesWithDisfluency(),
                    finalAnnotation == null ? null : finalAnnotation.syllablesWithoutDisfluency(),
                    r.rate() == null ? null : r.rate().utteranceMs(), ref == null ? null : ref.utteranceMs(),
                    r.rateMeasured() ? r.rate().syllablesPerSecond() : null, fmt(ref == null ? null : ref.rateWithDisfluency()),
                    fmt(ref == null ? null : ref.rateWithoutDisfluency()), fmt(r.rateError(true)), fmt(r.rateError(false)), fmt(r.rateRelativeErrorPct(true)),
                    r.pausesMeasured() ? r.pauses().pauseCount() : null, ref == null ? null : ref.pauseCount(), ref == null ? null : ref.shortSilenceCount(),
                    r.pauseCountDiff(), r.pausesMeasured() ? r.pauses().longestPauseMs() : null, ref == null ? null : ref.longestPauseMs(), r.longestPauseError(),
                    r.pausesMeasured() ? r.pauses().totalPauseMs() : null, ref == null ? null : ref.totalPauseMs(), r.totalPauseError()));
            if (includeTranscript) cells.add(r.transcript());
            out.append(String.join("\t", cells.stream().map(c -> c == null ? "" : String.valueOf(c).replace('\t', ' ')).toList())).append('\n');
        }
        return out.toString();
    }

    static String summaryMarkdown(String title, List<Row> rows, List<Agreement> agreements, Map<String, String> settings) {
        StringBuilder out = new StringBuilder("# ").append(title).append("\n\n");
        settings.forEach((key, value) -> out.append("- ").append(key).append(": ").append(value).append('\n'));
        out.append("- 오차 = 시스템 − 기준 정답. 측정된 녹음만 오차에 포함한다. 합격·불합격 판정은 하지 않는다(허용 오차는 전문가 검토 후 정함).\n\n");
        out.append("| 묶음 | 녹음 | 분석 실패 | 속도 측정 | 속도 해당 없음 | 속도 측정 불가 | 측정됐지만 기준 음절 없음 | 속도 오차 평균(반복·간투어 포함 기준) | 속도 MAE(포함) | 속도 오차 평균(제외 기준) | 속도 MAE(제외) | 상대 오차 절댓값 중앙값 %(포함) | 멈춤 측정 | 멈춤 수 일치 % | 멈춤 수 차이 절댓값 평균 | 최장 멈춤 오차 평균 ms | 최장 멈춤 MAE ms | 멈춤 합 MAE ms | 발화 구간 오차 평균 ms |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (GroupSummary s : summaries(rows))
            out.append(String.join(" | ", List.of("| " + s.group(), String.valueOf(s.recordings()), String.valueOf(s.failed()), String.valueOf(s.rateMeasured()),
                    String.valueOf(s.rateNotSupported()), String.valueOf(s.rateUnavailable()), String.valueOf(s.rateNoReference()), fmt(s.rateBiasWith()), fmt(s.rateMaeWith()), fmt(s.rateBiasWithout()), fmt(s.rateMaeWithout()),
                    fmt(s.medianAbsRelErrorPctWith()), String.valueOf(s.pauseMeasured()), fmt(s.pauseCountExactPct()), fmt(s.meanAbsPauseCountDiff()),
                    fmt(s.longestPauseBiasMs()), fmt(s.longestPauseMaeMs()), fmt(s.totalPauseMaeMs()), fmt(s.utteranceBiasMs()))) + " |\n");
        out.append("\n## 전사자 간 일치도\n\n");
        if (agreements.isEmpty()) out.append("전사자 2명 이상이 표시한 녹음이 없어 계산하지 않았다.\n");
        else {
            long pauseEqual = agreements.stream().filter(Agreement::pauseCountEqual).count();
            List<Agreement> withSyllables = agreements.stream().filter(a -> a.syllablesEqual() != null).toList();
            List<Double> boundaries = agreements.stream().map(Agreement::meanBoundaryDiffMs).filter(Objects::nonNull).toList();
            out.append("- 전사자 쌍: ").append(agreements.size()).append('\n');
            out.append("- 멈춤 수 일치: ").append(fmt(pauseEqual * 100.0 / agreements.size())).append("%\n");
            out.append("- 음절 수(반복·간투어 포함) 일치: ").append(withSyllables.isEmpty() ? "-" : fmt(withSyllables.stream().filter(Agreement::syllablesEqual).count() * 100.0 / withSyllables.size()) + "%").append('\n');
            out.append("- 구간 경계 평균 차이(구간 수가 같은 쌍 ").append(boundaries.size()).append("개): ").append(fmt(mean(boundaries))).append(" ms\n");
        }
        return out.toString();
    }

    static String fmt(Double value) { return value == null ? "-" : String.format(Locale.ROOT, "%.2f", value); }
}
