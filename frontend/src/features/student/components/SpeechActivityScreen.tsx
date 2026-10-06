"use client";

import { useRef, useState } from "react";
import { CheckCircle, RotateCcw } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import { useAudioRecorder } from "@/features/student/hooks/useAudioRecorder";
import { isConsentRequired } from "@/features/student/utils/speechErrors";
import { textMatchRate } from "@/features/student/utils/speechAssessment";
import { toWav16k } from "@/features/student/utils/wavEncoder";
import AiFeedbackPanel from "@/features/student/components/AiFeedbackPanel";
import RecordButton from "@/features/student/components/RecordButton";
import SpeechAnalysisResult from "@/features/student/components/SpeechAnalysisResult";
import type { EvaluationMode, Exercise, ExerciseResult, ItemResult, SpeechAnalysis } from "@/features/student/types";
import { errorMessage } from "@/shared/api/client";
import { newIdempotencyKey } from "@/shared/api/idempotencyKey";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import Notice from "@/shared/components/feedback/Notice";
import Mascot from "@/shared/components/mascot/Mascot";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import ProgressBar from "@/shared/components/progressBar/ProgressBar";
import Spinner from "@/shared/components/spinner/Spinner";

type AnalysisPhase = "none" | "converting" | "analyzing" | "done" | "failed";
type SaveState = "idle" | "saving" | "saved" | "failed";

const POLL_LIMIT = 20;
const POLL_INTERVAL_MS = 1500;

async function waitForAnalysis(analysisId: string): Promise<SpeechAnalysis> {
  for (let attempt = 0; attempt < POLL_LIMIT; attempt += 1) {
    const result = await studentApi.speechAnalysis(analysisId);
    if (result.status === "COMPLETED") return result;
    if (result.status === "FAILED") throw new Error("음성 분석에 실패했어요. 다시 시도해 주세요.");
    await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS));
  }
  throw new Error("분석 결과를 아직 받지 못했어요. 잠시 뒤 다시 시도해 주세요.");
}

export function averageMatchRate(items: ItemResult[]) {
  const rates = items.map((item) => item.matchRate).filter((rate): rate is number => typeof rate === "number");
  return rates.length ? Math.round(rates.reduce((sum, rate) => sum + rate, 0) / rates.length) : null;
}

export default function SpeechActivityScreen({ exercises, exIdx, onComplete, onBack, onOpenConsent, homeworkId, lessonSessionId }: {
  exercises: Exercise[]; exIdx: number; onComplete: (result: ExerciseResult) => void; onBack: () => void; onOpenConsent: () => void;
  /** 숙제로 하는 연습이면 숙제 ID(기록이 숙제 연습으로 저장된다). 없으면 자율 연습 */
  homeworkId?: number;
  /** 수업(세션)에서 하는 연습이면 수업 ID(수업 연습 LESSON으로 저장). 숙제도 수업도 아니면 자율 연습 SELF */
  lessonSessionId?: string;
}) {
  const exercise = exercises[exIdx];
  const recorder = useAudioRecorder();
  const [itemIdx, setItemIdx] = useState(0);
  const [phase, setPhase] = useState<AnalysisPhase>("none");
  const [analysis, setAnalysis] = useState<SpeechAnalysis | null>(null);
  const [analysisError, setAnalysisError] = useState("");
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [consentNeeded, setConsentNeeded] = useState(false);
  const [itemResults, setItemResults] = useState<ItemResult[]>([]);
  const [finished, setFinished] = useState(false);
  const busyRef = useRef(false);
  // 녹음 한 개당 요청 키 하나. 같은 녹음의 재시도(다시 분석하기·응답 유실)는 같은 키로 보내 서버가 한 번만 분석한다.
  const requestKeyRef = useRef<{ recording: Blob; key: string } | null>(null);

  if (!exercise || exercise.items.length === 0) {
    return (
      <div className="min-h-screen">
        <PageHeader title={exercise?.label ?? "말하기 연습"} onBack={onBack} />
        <div className="px-5 py-6"><EmptyState title="연습할 문항이 없어요" description="선생님이 문항을 등록하면 연습할 수 있어요." icon={<Mascot size={64} />} /></div>
      </div>
    );
  }

  const total = exercise.items.length;
  const item = exercise.items[itemIdx];
  const busy = phase === "converting" || phase === "analyzing" || saveState === "saving";

  const saveAttempt = async (result: SpeechAnalysis) => {
    setSaveState("saving");
    try {
      await studentApi.saveAttempt({
        exerciseId: exercise.id, itemId: item.id, audioId: result.analysisId,
        ...(typeof result.overallScore === "number" ? { score: result.overallScore } : {}),
        ...(homeworkId ? { homeworkId } : lessonSessionId ? { practiceType: "LESSON" as const, sessionId: Number(lessonSessionId) } : { practiceType: "SELF" as const }),
      });
      setSaveState("saved");
    } catch {
      setSaveState("failed");
    }
  };

  const analyze = async () => {
    const recording = recorder.recording;
    if (!recording || busyRef.current) return;
    busyRef.current = true;
    setAnalysisError("");
    try {
      setPhase("converting");
      const wav = await toWav16k(recording);
      setPhase("analyzing");
      if (requestKeyRef.current?.recording !== recording) requestKeyRef.current = { recording, key: newIdempotencyKey() };
      const job = await studentApi.analyzeSpeech(wav, exercise.id, item.id, requestKeyRef.current.key);
      const result = await waitForAnalysis(job.analysisId);
      setAnalysis(result);
      setPhase("done");
      await saveAttempt(result);
    } catch (cause) {
      setConsentNeeded(isConsentRequired(cause));
      setAnalysisError(errorMessage(cause, "음성을 분석하지 못했어요. 다시 시도해 주세요."));
      setPhase("failed");
    } finally {
      busyRef.current = false;
    }
  };

  const resetItem = () => {
    recorder.reset();
    setPhase("none");
    setAnalysis(null);
    setAnalysisError("");
    setSaveState("idle");
  };

  const handleNext = () => {
    if (!analysis) return;
    const mode: EvaluationMode = analysis.evaluationMode ?? "EXTERNAL_PROVIDER";
    const nextResults = [...itemResults, {
      itemId: item.id, word: item.word, mode,
      matchRate: textMatchRate(analysis),
      overallScore: typeof analysis.overallScore === "number" ? analysis.overallScore : null,
      saved: saveState === "saved",
    }];
    setItemResults(nextResults);
    resetItem();
    if (itemIdx < total - 1) setItemIdx((value) => value + 1);
    else setFinished(true);
  };

  if (finished) {
    const average = averageMatchRate(itemResults);
    const pendingReview = itemResults.filter((result) => result.mode === "PRONUNCIATION_REVIEW").length;
    const unsaved = itemResults.filter((result) => !result.saved).length;
    return (
      <div className="flex min-h-screen flex-col">
        <PageHeader title={exercise.label} subtitle={`활동 ${exIdx + 1}/${exercises.length}`} onBack={onBack} />
        <div className="flex flex-1 flex-col items-center justify-center gap-5 px-5 py-6">
          <span className="flex h-[84px] w-[84px] items-center justify-center rounded-full bg-[var(--meadow-100)]" aria-hidden="true">
            <CheckCircle size={40} className="text-[var(--meadow-700)]" />
          </span>
          <div className="text-center">
            <h3 className="font-display text-[26px] leading-tight text-[var(--ink-900)]">활동 완료</h3>
            <p className="mt-1 text-sm text-[var(--ink-600)]">&quot;{exercise.label}&quot;를 마쳤어요</p>
          </div>
          <Card tone="raised" padding="lg" className="w-full text-center">
            {average !== null ? <>
              <p className="text-[13px] text-[var(--ink-600)]">평균 텍스트 일치율</p>
              {/* 글자 비교 값이라 점수처럼 강조색을 쓰지 않는다 */}
              <p className="mt-1.5 font-number text-[60px] font-black leading-none text-[var(--ink-900)]">{average}<span className="ml-0.5 text-2xl text-[var(--ink-400)]">%</span></p>
              <p className="mt-3 text-xs leading-relaxed text-[var(--ink-500)]">음성 인식 결과와 목표 문장을 비교한 값이에요. 발음 점수가 아니에요.</p>
            </> : <>
              <p className="text-[15px] font-semibold text-[var(--ink-800)]">{itemResults.length}개 문항을 녹음했어요</p>
              <p className="mt-2 text-xs leading-relaxed text-[var(--ink-500)]">{pendingReview > 0 ? "선생님이 녹음을 듣고 발음을 확인해 줄 거예요." : "이번 활동에는 계산된 텍스트 일치율이 없어요."}</p>
            </>}
            {pendingReview > 0 && <div className="mt-3 flex justify-center"><Badge tone="info">선생님 확인 대기 {pendingReview}개</Badge></div>}
          </Card>
          {unsaved > 0 && <Notice tone="warning" className="w-full">{unsaved}개 문항의 학습 기록을 저장하지 못했어요.</Notice>}
        </div>
        <div className="shrink-0 px-5 pt-3 pb-8">
          <Button size="lg" fullWidth onClick={() => onComplete({ exerciseId: exercise.id, label: exercise.label, items: itemResults })}>
            {exIdx < exercises.length - 1 ? "다음 활동" : "결과 보기"}
          </Button>
        </div>
      </div>
    );
  }

  const recording = recorder.status === "recording";
  const compact = recorder.status === "recorded" || phase === "done";
  const longText = item.word.length > 6;
  return (
    <div className="flex min-h-screen flex-col">
      <PageHeader title={exercise.label} subtitle={`활동 ${exIdx + 1}/${exercises.length}`} onBack={onBack}
        action={<Badge tone="neutral" className="px-3 py-1 text-[13px] font-bold">{itemIdx + 1}/{total}</Badge>} />
      <div className="px-5"><ProgressBar value={(itemIdx / total) * 100} label={`문항 진행률 ${itemIdx}/${total}`} size="md" /></div>

      <div className="flex flex-1 flex-col items-center gap-4 px-5 pt-5 pb-8">
        <p className="text-center text-[15px] leading-relaxed text-[var(--ink-600)]">{exercise.instruction}</p>
        {/* 문항 그림과 목표 낱말·문장 */}
        <div className={`flex w-full flex-col items-center gap-3 rounded-[var(--radius-sheet)] border border-[var(--line-soft)] bg-white text-center shadow-[var(--shadow-card-lg)] ${compact ? "px-5 py-5" : "px-5 pt-6 pb-7"}`}>
          {item.emoji && (
            <div className={`flex items-center justify-center rounded-[var(--radius-card-lg)] bg-[repeating-linear-gradient(135deg,var(--butter-50)_0_10px,var(--butter-100)_10px_20px)] ${compact ? "h-[110px] w-[110px] text-6xl" : "h-[150px] w-[150px] text-7xl"}`} aria-hidden="true">{item.emoji}</div>
          )}
          <p className={`break-keep font-display leading-[1.15] tracking-[-0.02em] text-[var(--ink-900)] ${longText ? "text-[30px]" : compact ? "text-[44px]" : "text-[52px]"}`}>{item.word}</p>
        </div>

        <div className="mt-auto flex w-full flex-col items-center gap-3 pt-2">
          {(phase === "none" || phase === "failed") && recorder.status !== "recorded" && (
            <>
              <RecordButton state={recording ? "recording" : recorder.status === "requesting" ? "requesting" : "idle"}
                onClick={() => void (recording ? recorder.stop() : recorder.start())}
                disabled={recorder.status === "requesting"} startLabel="녹음 시작" />
              <p className={`max-w-[290px] text-center text-[13px] leading-relaxed ${recording ? "font-semibold text-[var(--coral-700)]" : "text-[var(--ink-600)]"}`} aria-live="polite">
                {recording ? `녹음 중… ${recorder.elapsed}초 (최대 ${recorder.maxSeconds}초) · 다 말했으면 버튼을 눌러 멈춰요`
                  : recorder.status === "requesting" ? "마이크 권한을 확인하고 있어요…" : "버튼을 누르고 말해보세요"}
              </p>
            </>
          )}

          {recorder.status === "recorded" && phase !== "done" && (
            <Card tone="raised" padding="md" className="w-full space-y-3 rounded-[26px]">
              <p className="text-sm font-bold text-[var(--ink-900)]">내 녹음 들어보기</p>
              {recorder.previewUrl && <audio controls src={recorder.previewUrl} className="h-11 w-full" aria-label="내 녹음 재생" />}
              {phase === "converting" || phase === "analyzing" ? (
                <div role="status" className="flex items-center justify-center gap-2.5 rounded-[var(--radius-lg)] bg-[var(--meadow-50)] py-3 text-sm font-semibold text-[var(--meadow-900)]">
                  <Spinner size="md" className="text-[var(--meadow-700)]" />{phase === "converting" ? "녹음을 준비하고 있어요…" : "음성을 인식하고 있어요…"}
                </div>
              ) : (
                <div className="grid grid-cols-[1fr_1.4fr] gap-2.5">
                  <Button variant="line" size="lg" onClick={resetItem} disabled={busy}><RotateCcw size={16} aria-hidden="true" />다시 녹음</Button>
                  <Button size="lg" onClick={() => void analyze()} disabled={busy}>{phase === "failed" ? "다시 분석하기" : "분석하기"}</Button>
                </div>
              )}
            </Card>
          )}

          {phase === "done" && analysis && (
            <div className="flex w-full flex-col gap-3">
              <SpeechAnalysisResult analysis={analysis} />
              <AiFeedbackPanel analysisId={analysis.analysisId} />
              {saveState === "saving" && <Notice tone="info">학습 기록을 저장하고 있어요…</Notice>}
              {saveState === "saved" && <p role="status" className="flex items-center gap-1.5 px-1 text-[13px] font-semibold text-[var(--meadow-700)]"><CheckCircle size={16} aria-hidden="true" />학습 기록에 저장했어요.</p>}
              {saveState === "failed" && (
                <div className="flex items-center gap-2">
                  <Notice tone="error" className="flex-1">학습 기록을 저장하지 못했어요.</Notice>
                  <Button size="sm" variant="line" onClick={() => void saveAttempt(analysis)}>다시 저장</Button>
                </div>
              )}
              <div className="grid grid-cols-[1fr_1.4fr] gap-2.5 pt-1">
                <Button variant="line" size="lg" onClick={resetItem} disabled={busy}><RotateCcw size={16} aria-hidden="true" />다시 녹음</Button>
                <Button size="lg" onClick={handleNext} disabled={busy}>{itemIdx < total - 1 ? "다음" : "활동 완료"}</Button>
              </div>
            </div>
          )}
        </div>
        {recorder.error && <Notice tone="error" className="w-full text-center">{recorder.error}</Notice>}
        {analysisError && <Notice tone="error" className="w-full text-center">{analysisError}</Notice>}
        {consentNeeded && <Button variant="secondary" fullWidth onClick={onOpenConsent}>마이페이지에서 동의 관리하기</Button>}
      </div>
    </div>
  );
}
