import Badge from "@/shared/components/badge/Badge";
import Card from "@/shared/components/card/Card";
import Notice from "@/shared/components/feedback/Notice";
import ProgressBar from "@/shared/components/progressBar/ProgressBar";
import type { SpeechAnalysis, SpeechWordDiff } from "@/features/student/types";
import { analysisTypeLabel, isWordAnalysis, textMatchRate } from "@/features/student/utils/speechAssessment";

const LOW_CONFIDENCE = 0.6;

const metricLabels: { key: "pronunciationScore" | "speechRateScore" | "fluencyScore"; label: string }[] = [
  { key: "pronunciationScore", label: "발음 정확도" },
  { key: "speechRateScore", label: "말하기 속도" },
  { key: "fluencyScore", label: "유창성" },
];

function WordChip({ word }: { word: SpeechWordDiff }) {
  if (word.type === "MATCH") return <Badge tone="success">{word.expected}</Badge>;
  if (word.type === "MISSING") return <Badge tone="danger">누락 가능 · {word.expected}</Badge>;
  if (word.type === "INSERTED") return <Badge tone="warning">추가됨 · {word.recognized}</Badge>;
  return <Badge tone="warning">{word.expected} → {word.recognized}</Badge>;
}

/** 판정 보류(녹음 품질·인식 결과가 불확실함)일 때 아이에게 다시 녹음하도록 안내한다. */
function HoldNotice({ analysis }: { analysis: SpeechAnalysis }) {
  if (analysis.assessmentStatus !== "HOLD") return null;
  const reasons = analysis.holdReasons ?? [];
  const message = reasons.includes("SPEECH_CUT_OFF_END") ? "말이 끝나기 전에 녹음이 멈췄어요. 다 말한 뒤에 멈춤 버튼을 눌러 주세요."
    : reasons.includes("LOW_VOLUME") ? "소리가 작게 녹음됐어요. 마이크 가까이에서 다시 말해 주세요."
      : reasons.includes("CLIPPING") ? "소리가 너무 크게 녹음됐어요. 마이크에서 조금 떨어져서 다시 말해 주세요."
        : "녹음이 또렷하지 않아서 결과를 판단하지 않았어요. 한 번 더 녹음해 주세요.";
  return <Notice tone="warning"><b>판정 보류</b> · {message}</Notice>;
}

/** 측정값이 있으면 막대로, 없으면 '미평가'로 표시한다. 다른 값으로 대체하지 않는다. */
function MetricRows({ analysis }: { analysis: SpeechAnalysis }) {
  return (
    <dl className="space-y-2">
      {metricLabels.map(({ key, label }) => {
        const value = analysis[key];
        return (
          <div key={key} className="flex min-h-6 items-center gap-2.5">
            <dt className="w-[76px] shrink-0 text-xs text-[var(--ink-500)]">{label}</dt>
            {typeof value === "number"
              ? <dd className="flex flex-1 items-center gap-2"><ProgressBar value={value} label={`${label} ${value}점`} /><span className="w-7 text-right text-xs font-semibold text-[var(--ink-700)]">{value}</span></dd>
              : <dd className="flex-1"><Badge tone="neutral">미평가</Badge></dd>}
          </div>
        );
      })}
    </dl>
  );
}

/** 목표와 음성 인식 결과를 나란히 보여 주는 보조 영역 */
function TargetRows({ target, transcript }: { target?: string | null; transcript?: string | null }) {
  return (
    <dl className="grid grid-cols-[64px_1fr] gap-x-2.5 gap-y-1.5 rounded-[var(--radius-xl)] bg-[var(--surface-sunken)] px-3.5 py-3 text-sm">
      <dt className="pt-px text-xs text-[var(--ink-500)]">목표</dt><dd className="font-bold text-[var(--ink-900)]">{target}</dd>
      <dt className="pt-px text-xs text-[var(--ink-500)]">인식 결과</dt><dd className="font-bold text-[var(--ink-900)]">{transcript}</dd>
    </dl>
  );
}

export default function SpeechAnalysisResult({ analysis }: { analysis: SpeechAnalysis }) {
  const mode = analysis.evaluationMode ?? "EXTERNAL_PROVIDER";
  const words = analysis.comparison?.words ?? [];
  const lowConfidence = typeof analysis.recognitionConfidence === "number" && analysis.recognitionConfidence < LOW_CONFIDENCE;

  if (mode === "PRONUNCIATION_REVIEW") {
    return (
      <Card tone="raised" padding="lg" className="space-y-3 rounded-[26px]">
        <div className="flex items-center justify-between gap-2">
          <p className="text-[15px] font-bold text-[var(--ink-900)]">녹음이 저장되었어요</p>
          <Badge tone="info">선생님 확인 대기</Badge>
        </div>
        <p className="text-sm leading-relaxed text-[var(--ink-700)]">선생님이 녹음을 듣고 발음을 확인해 줄 거예요.</p>
        <HoldNotice analysis={analysis} />
        {analysis.transcript && (
          <div className="rounded-[var(--radius-xl)] bg-[var(--surface-sunken)] px-3.5 py-3">
            <p className="text-xs text-[var(--ink-500)]">컴퓨터가 알아들은 말 (참고용)</p>
            <p className="mt-1 text-[15px] font-bold text-[var(--ink-900)]">{analysis.transcript}</p>
          </div>
        )}
        <div className="flex items-center gap-2 border-t border-[var(--ink-100)] pt-3 text-xs text-[var(--ink-600)]">
          <span>발음 평가</span><Badge tone="neutral">미평가</Badge>
        </div>
      </Card>
    );
  }

  if (mode === "SENTENCE_MATCH") {
    const measured = textMatchRate(analysis);
    const rate = measured === null ? null : Math.round(measured);
    // 짧은 낱말은 문맥이 없어 음성 인식 오류가 잦다(합성 음성 측정에서 낱말 정확 인식 약 60%). 결과를 참고용으로 안내한다.
    const singleWord = isWordAnalysis(analysis);
    const held = analysis.assessmentStatus === "HOLD";
    return (
      <Card padding="lg" className="space-y-3.5 rounded-[var(--radius-card-lg)]">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1.5">
            <p className="text-sm font-semibold text-[var(--ink-600)]">텍스트 일치율</p>
            {(analysis.analysisType || held) && <div className="flex flex-wrap gap-1">
              {analysis.analysisType && <Badge tone="neutral">{analysisTypeLabel[analysis.analysisType]}</Badge>}
              {held && <Badge tone="warning">판정 보류</Badge>}
            </div>}
          </div>
          {/* 글자 비교 값: 점수처럼 보이지 않도록 강조색 없이 표시하고 성격을 함께 적는다 */}
          <div className="shrink-0 text-right">
            <p className="font-number text-[48px] font-black leading-none text-[var(--ink-900)]">{rate ?? "-"}<span className="ml-0.5 text-xl text-[var(--ink-400)]">%</span></p>
            <p className="mt-1 text-[11px] font-medium text-[var(--ink-500)]">글자 비교 · 발음 점수 아님</p>
          </div>
        </div>
        {rate !== null && <ProgressBar value={rate} label={`텍스트 일치율 ${rate}%`} size="md" color="var(--sky-400)" />}
        <TargetRows target={analysis.targetText} transcript={analysis.transcript} />
        {words.length > 0 && (
          <div>
            <p className="mb-1.5 text-xs text-[var(--ink-500)]">단어 비교</p>
            <div className="flex flex-wrap gap-1.5">{words.map((word, index) => <WordChip key={index} word={word} />)}</div>
          </div>
        )}
        <HoldNotice analysis={analysis} />
        {lowConfidence && <Notice tone="warning">컴퓨터가 확실하게 알아듣지 못했어요. 조용한 곳에서 다시 녹음해 보세요.</Notice>}
        {singleWord && <Notice tone="info">낱말 하나는 컴퓨터가 다르게 알아듣는 경우가 많아요. 텍스트 일치율이 낮아도 잘못 말한 것이 아닐 수 있어요.</Notice>}
        <div className="border-t border-[var(--ink-100)] pt-3"><MetricRows analysis={analysis} /></div>
        <p className="text-xs leading-relaxed text-[var(--ink-500)]">텍스트 일치율은 음성 인식 결과와 목표 문장의 글자 일치 정도예요. 발음 정확도 점수나 진단 결과가 아니에요.</p>
      </Card>
    );
  }

  // 기존 외부 음성 분석 제공자(SPEECH_ENGINE=external) 결과: 제공자가 반환한 값만 표시한다.
  return (
    <Card padding="lg" className="space-y-3 rounded-[var(--radius-card-lg)]">
      <div className="flex items-center justify-between">
        <p className="text-sm font-semibold text-[var(--ink-600)]">외부 분석 참고 점수</p>
        <span className="font-number text-[40px] font-black leading-none text-[var(--ink-900)]">{typeof analysis.overallScore === "number" ? analysis.overallScore : "-"}<span className="ml-1 text-base text-[var(--ink-400)]">점</span></span>
      </div>
      <MetricRows analysis={analysis} />
      {analysis.feedback && <p className="border-t border-[var(--ink-100)] pt-3 text-xs text-[var(--ink-600)]">{analysis.feedback}</p>}
      <p className="text-xs leading-relaxed text-[var(--ink-500)]">음성 분석 제공자가 반환한 연습 참고 결과이며 치료사의 평가를 대신하지 않아요.</p>
    </Card>
  );
}
