import type { TeacherSpeechAnalysis } from "@/features/teacher/types";
import { analysisTypeLabel, candidateText, errorTypeLabel, holdReasonLabel, positionText } from "@/features/student/utils/speechAssessment";
import Badge, { type BadgeTone } from "@/shared/components/badge/Badge";

const statusView: Record<string, { tone: BadgeTone; label: string }> = {
  NO_CANDIDATES: { tone: "neutral", label: "자동 후보 없음" },
  ERROR_CANDIDATES: { tone: "warning", label: "자동 오류 후보 있음" },
  HOLD: { tone: "danger", label: "판정 보류" },
};

const seconds = (ms: number | null | undefined) => (typeof ms === "number" ? `${(ms / 1000).toFixed(1)}초` : "-");

/**
 * 자동 분석 근거(단어/문장 유형, 목표 음소 위치, 오류 후보, 발화 시간, 반복 비교)와 선생님 확정 결과를 구분해 보여준다.
 * 자동 결과는 음성 인식 텍스트를 표기 기준으로 비교한 후보이며 확정 판정이 아니다.
 */
export default function SpeechAssessmentDetails({ analysis }: { analysis: TeacherSpeechAnalysis }) {
  if (!analysis.assessmentStatus) return null;
  const status = statusView[analysis.assessmentStatus] ?? { tone: "neutral" as BadgeTone, label: analysis.assessmentStatus };
  const positions = analysis.targetPositions ?? [];
  const candidates = analysis.phonemeCandidates ?? [];
  const timing = analysis.speechTiming;
  const repetition = analysis.repetition;
  const confirmed = analysis.teacherConfirmedErrors ?? [];

  return (
    <div className="space-y-2.5 rounded-xl border border-gray-100 p-3 text-xs text-gray-600">
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="font-semibold text-gray-700">자동 분석</span>
        {analysis.analysisType && <Badge tone="neutral">{analysisTypeLabel[analysis.analysisType]}</Badge>}
        <Badge tone={status.tone}>{status.label}</Badge>
        {analysis.analysisVersion && <span className="text-gray-400">{analysis.analysisVersion} · {analysis.modelName ?? "-"}</span>}
      </div>

      {(analysis.holdReasons ?? []).length > 0 && (
        <ul className="list-disc space-y-0.5 pl-4 text-red-600">
          {(analysis.holdReasons ?? []).map((reason) => <li key={reason}>{holdReasonLabel[reason] ?? reason}</li>)}
        </ul>
      )}

      {(analysis.targetPhonemes ?? []).length > 0 && (
        <div>
          <p className="font-medium text-gray-500">목표 음소 {(analysis.targetPhonemes ?? []).join(", ")} 위치 (표기 기준)</p>
          {positions.length > 0
            ? <ul className="mt-0.5 space-y-0.5">{positions.map((p, i) => <li key={i}>· {p.phoneme}: {positionText(p)}</li>)}</ul>
            : <p className="mt-0.5 text-gray-400">목표 텍스트에 목표 음소가 없어요.</p>}
        </div>
      )}

      <div>
        <p className="font-medium text-gray-500">오류 후보 (자동 · 미확정)</p>
        {candidates.length > 0
          ? <ul className="mt-0.5 space-y-0.5">{candidates.map((c, i) => <li key={i} className={c.targetPhoneme ? "font-semibold text-amber-700" : ""}>· {candidateText(c)}{c.targetPhoneme ? " · 목표 음소" : ""}</li>)}</ul>
          : <p className="mt-0.5 text-gray-400">인식 텍스트 기준 후보가 없어요. 발음이 정확하다는 뜻은 아니에요.</p>}
      </div>

      {timing && (
        <p>
          <span className="font-medium text-gray-500">발화 시간</span>{" "}
          {timing.source === "VAD"
            ? <>말소리 {seconds(timing.speechMs)} / 녹음 {seconds(timing.audioMs)} · 시작 전 무음 {seconds(timing.leadingSilenceMs)} · 끝 무음 {seconds(timing.trailingSilenceMs)} · 쉼 {timing.pauseCount ?? 0}회(최장 {seconds(timing.longestPauseMs)}){typeof timing.syllablesPerSecond === "number" ? ` · 초당 ${timing.syllablesPerSecond}음절(인식 기준)` : ""}</>
            : <>녹음 {seconds(timing.audioMs)} · 말소리 구간 측정 불가(VAD 미설정)</>}
        </p>
      )}

      {repetition && repetition.previousAttempts > 0 && (
        <div>
          <p className="font-medium text-gray-500">같은 문항 이전 발화 {repetition.previousAttempts}회 · 같은 인식 결과 {repetition.sameTranscriptCount}회</p>
          {repetition.recurringCandidates.length > 0 && <p className="mt-0.5 text-amber-700">반복된 후보: {repetition.recurringCandidates.join(", ")}</p>}
          <ul className="mt-0.5 space-y-0.5 text-gray-500">
            {repetition.recent.map((r, i) => <li key={i}>· {r.createdAt} “{r.transcript ?? "-"}”{typeof r.textMatchRate === "number" ? ` · 텍스트 일치율 ${Math.round(r.textMatchRate)}%` : ""}</li>)}
          </ul>
        </div>
      )}

      {confirmed.length > 0 && (
        <div className="border-t border-gray-100 pt-2">
          <p className="font-medium text-gray-700">선생님 확정 오류</p>
          <ul className="mt-0.5 space-y-0.5">{confirmed.map((e, i) => <li key={i}>· {e.phoneme} {errorTypeLabel[e.errorType] ?? e.errorType}{e.produced ? ` → ${e.produced}` : ""}{e.position ? ` (${e.position})` : ""}</li>)}</ul>
        </div>
      )}
    </div>
  );
}
