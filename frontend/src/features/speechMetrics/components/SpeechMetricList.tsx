import type { ReactNode } from "react";
import Badge, { type BadgeTone } from "@/shared/components/badge/Badge";
import type { SpeechMetrics, SpeechMetricStatus } from "@/features/student/types";

type Audience = "student" | "teacher";

/**
 * 말하기 측정 상태(학생 결과·선생님 연습 기록·음성 검토 공통). 저장된 speechMetrics만 표시하고 값을 새로 계산하지 않는다.
 * - 측정 완료: 실제 측정값과 단위(점수·막대·등급으로 바꾸지 않음)
 * - 준비 중: 발음 분석(평가 모델 없음). 분석이 진행 중이라는 뜻이 아니다
 * - 해당 없음: 낱말 문항의 속도·멈춤
 * - 측정 불가: 말소리 구간 없음·판정 보류·짧은 발화(이유 함께 표시)
 * - 기록 없음: 측정 기능 전에 저장된 분석(speechMetrics 없음)
 * - 분석 실패/분석 중: 분석 상태가 COMPLETED가 아니면 측정 항목 대신 상태만 표시
 * 상태는 색이 아니라 배지 글자와 설명으로 구분한다.
 */
type DisplayState = { badge: string; tone: BadgeTone; text: string };

const reasonText: Record<Audience, Record<string, string>> = {
  student: {
    WORD_ITEM: "낱말 하나는 재지 않아요",
    VAD_UNAVAILABLE: "말소리 구간을 확인하지 못했어요",
    ASSESSMENT_HOLD: "녹음이 또렷하지 않아 재지 않았어요",
    UTTERANCE_TOO_SHORT: "말이 짧아서 재지 않았어요",
  },
  teacher: {
    WORD_ITEM: "낱말 문항은 측정하지 않음",
    VAD_UNAVAILABLE: "말소리 구간(VAD)을 얻지 못함",
    ASSESSMENT_HOLD: "판정 보류 녹음이라 측정하지 않음",
    UTTERANCE_TOO_SHORT: "4음절·1초 미만 발화라 측정하지 않음",
  },
};

const seconds = (ms: number) => `${(ms / 1000).toFixed(1)}초`;

function notMeasured(status: SpeechMetricStatus, reason: string | null | undefined, audience: Audience): DisplayState {
  const text = (reason && reasonText[audience][reason]) ?? "";
  if (status === "NOT_SUPPORTED") return { badge: "해당 없음", tone: "neutral", text };
  return { badge: "측정 불가", tone: "neutral", text };
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-2.5">
      <dt className="w-[76px] shrink-0 pt-0.5 text-xs text-[var(--ink-500)]">{label}</dt>
      <dd className="min-w-0 flex-1 break-keep text-xs leading-relaxed text-[var(--ink-600)]">{children}</dd>
    </div>
  );
}

function State({ state }: { state: DisplayState }) {
  return (
    <span className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
      <Badge tone={state.tone}>{state.badge}</Badge>
      {state.text && <span>{state.text}</span>}
    </span>
  );
}

/** 발음 정확도: 평가 모델이 없어 항상 '준비 중'(텍스트 일치율과 별개) */
export function PronunciationPreparing({ audience }: { audience: Audience }) {
  return (
    <Row label={audience === "student" ? "발음 정확도" : "발음 분석"}>
      <span className="flex flex-col gap-1">
        <span className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
          <Badge tone="info">준비 중</Badge>
          <span>{audience === "student" ? "발음을 분석하는 기능을 준비하고 있어요." : "발음 분석 준비 중 · 발음 평가 모델이 없어 발음 정확도를 제공하지 않습니다."}</span>
        </span>
        {audience === "student" && <span className="text-[11px] text-[var(--ink-500)]">현재는 발음 정확도 점수를 제공하지 않아요.</span>}
      </span>
    </Row>
  );
}

export default function SpeechMetricList({ metrics, audience, analysisStatus = "COMPLETED" }: {
  metrics?: SpeechMetrics | null; audience: Audience; analysisStatus?: string | null;
}) {
  if (analysisStatus === "FAILED")
    return <dl><Row label="말하기 측정"><State state={{ badge: "분석 실패", tone: "danger", text: audience === "student" ? "결과를 받지 못해 측정값이 없어요" : "분석이 실패해 측정값이 없습니다" }} /></Row></dl>;
  if (analysisStatus !== "COMPLETED")
    return <dl><Row label="말하기 측정"><State state={{ badge: "분석 중", tone: "neutral", text: "" }} /></Row></dl>;

  const rate = metrics?.speakingRate;
  const pauses = metrics?.pauses;
  const noRecord: DisplayState = { badge: "기록 없음", tone: "neutral", text: audience === "student" ? "이 기록에는 측정값이 없어요" : "측정 기능 이전에 저장된 기록이라 측정값이 없습니다" };
  return (
    <dl className="space-y-2">
      <PronunciationPreparing audience={audience} />
      <Row label="말하기 속도">
        {!rate ? <State state={noRecord} />
          : rate.status === "MEASURED" && typeof rate.syllablesPerSecond === "number" && typeof rate.utteranceMs === "number"
            ? <span><b className="text-sm text-[var(--ink-900)]">초당 {rate.syllablesPerSecond.toFixed(1)}글자</b>
              <span className="ml-1.5 text-[var(--ink-500)]">{audience === "student"
                ? `${rate.syllables}글자를 ${seconds(rate.utteranceMs)} 동안 말했어요`
                : `${rate.syllables}음절 / 발화 ${seconds(rate.utteranceMs)}${typeof rate.articulationRate === "number" ? ` · 멈춤 제외 초당 ${rate.articulationRate.toFixed(1)}음절` : ""}`}</span></span>
            : <State state={notMeasured(rate.status, rate.reason, audience)} />}
      </Row>
      <Row label="멈춤">
        {!pauses ? <State state={noRecord} />
          : pauses.status === "MEASURED" && typeof pauses.pauseCount === "number"
            ? <span><b className="text-sm text-[var(--ink-900)]">{pauses.pauseCount === 0 ? "멈춤 없음" : audience === "student" ? `${pauses.pauseCount}번 멈췄어요` : `${pauses.pauseCount}회`}</b>
              {pauses.pauseCount > 0 && typeof pauses.longestPauseMs === "number" && <span className="ml-1.5 text-[var(--ink-500)]">{audience === "student"
                ? `가장 길게 ${seconds(pauses.longestPauseMs)}`
                : `최장 ${seconds(pauses.longestPauseMs)}${typeof pauses.totalPauseMs === "number" ? ` · 합계 ${seconds(pauses.totalPauseMs)}` : ""}`}</span>}</span>
            : <State state={notMeasured(pauses.status, pauses.reason, audience)} />}
      </Row>
      <p className="pt-1 text-[11px] leading-relaxed text-[var(--ink-500)]">
        {audience === "student"
          ? `말하기 속도와 멈춤은 녹음에서 잰 값이에요. 점수나 유창성 평가가 아니에요. 멈춤은 ${(pauses?.pauseThresholdMs ?? 250) / 1000}초 넘게 쉰 곳을 셌어요.`
          : `속도·멈춤은 음성 인식 음절 수와 말소리 구간 검출(VAD)로 잰 측정값입니다(멈춤: 무음 ${(pauses?.pauseThresholdMs ?? 250) / 1000}초 이상). 점수·종합 유창성 평가가 아니며, 아동 음성 기준으로는 아직 검증하지 않았습니다.`}
      </p>
    </dl>
  );
}
