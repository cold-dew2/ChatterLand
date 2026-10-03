"use client";

import { useRef, useState } from "react";
import { CheckCircle, Mic, RotateCcw, Square } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import { useAudioRecorder } from "@/features/student/hooks/useAudioRecorder";
import { isConsentRequired } from "@/features/student/utils/speechErrors";
import { textMatchRate } from "@/features/student/utils/speechAssessment";
import { toWav16k } from "@/features/student/utils/wavEncoder";
import AiFeedbackPanel from "@/features/student/components/AiFeedbackPanel";
import SpeechAnalysisResult from "@/features/student/components/SpeechAnalysisResult";
import type { EvaluationMode, Exercise, ExerciseResult, ItemResult, SpeechAnalysis } from "@/features/student/types";
import { errorMessage } from "@/shared/api/client";
import { newIdempotencyKey } from "@/shared/api/idempotencyKey";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import Notice from "@/shared/components/feedback/Notice";
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

export default function SpeechActivityScreen({ exercises, exIdx, onComplete, onBack, onOpenConsent }: {
  exercises: Exercise[]; exIdx: number; onComplete: (result: ExerciseResult) => void; onBack: () => void; onOpenConsent: () => void;
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
      <div className="min-h-screen bg-white">
        <PageHeader title={exercise?.label ?? "말하기 연습"} onBack={onBack} />
        <div className="px-5 py-6"><EmptyState title="연습할 문항이 없어요" description="선생님이 문항을 등록하면 연습할 수 있어요." /></div>
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
      <div className="flex min-h-screen flex-col bg-white">
        <PageHeader title={exercise.label} subtitle={`활동 ${exIdx + 1}/${exercises.length}`} onBack={onBack} />
        <div className="flex flex-1 flex-col items-center justify-center gap-5 px-5">
          <div className="flex h-20 w-20 items-center justify-center rounded-full border border-green-100 bg-green-50">
            <CheckCircle size={40} className="text-green-500" />
          </div>
          <div className="text-center">
            <h3 className="mb-1 text-2xl font-black text-gray-900">활동 완료</h3>
            <p className="text-sm text-gray-400">&quot;{exercise.label}&quot;를 마쳤어요</p>
          </div>
          <Card padding="lg" className="w-full text-center">
            {average !== null ? <>
              <p className="mb-1 text-sm text-gray-400">평균 텍스트 일치율</p>
              <p className="text-5xl font-black text-gray-900">{average}<span className="ml-1 text-xl font-bold text-gray-400">%</span></p>
              <p className="mt-3 text-xs text-gray-500">음성 인식 결과와 목표 문장을 비교한 값이에요. 발음 점수가 아니에요.</p>
            </> : <>
              <p className="text-sm font-semibold text-gray-700">{itemResults.length}개 문항을 녹음했어요</p>
              <p className="mt-2 text-xs text-gray-500">{pendingReview > 0 ? "선생님이 녹음을 듣고 발음을 확인해 줄 거예요." : "이번 활동에는 계산된 텍스트 일치율이 없어요."}</p>
            </>}
            {pendingReview > 0 && <div className="mt-3 flex justify-center"><Badge tone="info">선생님 확인 대기 {pendingReview}개</Badge></div>}
          </Card>
          {unsaved > 0 && <Notice tone="warning" className="w-full">{unsaved}개 문항의 학습 기록을 저장하지 못했어요.</Notice>}
          <Button size="lg" fullWidth onClick={() => onComplete({ exerciseId: exercise.id, label: exercise.label, items: itemResults })}>
            {exIdx < exercises.length - 1 ? "다음 활동" : "결과 보기"}
          </Button>
        </div>
      </div>
    );
  }

  const recording = recorder.status === "recording";
  return (
    <div className="flex min-h-screen flex-col bg-white">
      <PageHeader title={exercise.label} subtitle={`활동 ${exIdx + 1}/${exercises.length}`} onBack={onBack}
        action={<Badge tone="neutral">{itemIdx + 1}/{total}</Badge>} />
      <ProgressBar value={(itemIdx / total) * 100} label={`문항 진행률 ${itemIdx}/${total}`} className="rounded-none" />

      <div className="flex flex-1 flex-col items-center gap-5 px-5 py-6">
        <p className="text-center text-sm text-gray-400">{exercise.instruction}</p>
        <Card tone="muted" padding="none" className="w-full p-8 text-center">
          {item.emoji && <div className="mb-3 text-6xl" aria-hidden="true">{item.emoji}</div>}
          <p className="text-3xl font-black text-gray-900">{item.word}</p>
        </Card>

        <div className="mt-auto flex w-full flex-col items-center gap-3">
          {(phase === "none" || phase === "failed") && recorder.status !== "recorded" && (
            <>
              <button type="button" onClick={() => void (recording ? recorder.stop() : recorder.start())}
                disabled={recorder.status === "requesting"} aria-pressed={recording}
                aria-label={recording ? "녹음 중지" : "녹음 시작"}
                className={`flex h-24 w-24 select-none items-center justify-center rounded-full shadow-lg transition-all disabled:opacity-60 ${recording ? "scale-110 bg-red-400" : "bg-[var(--brand-primary)] hover:bg-[var(--brand-primary-hover)]"}`}>
                {recording ? <Square size={28} color="white" fill="white" /> : recorder.status === "requesting" ? <Spinner size="md" className="text-white" /> : <Mic size={28} color="white" />}
              </button>
              <p className="text-xs text-gray-500" aria-live="polite">
                {recording ? `녹음 중… ${recorder.elapsed}초 (최대 ${recorder.maxSeconds}초) · 다 말했으면 버튼을 눌러 멈춰요`
                  : recorder.status === "requesting" ? "마이크 권한을 확인하고 있어요…" : "버튼을 누르고 말해보세요"}
              </p>
            </>
          )}

          {recorder.status === "recorded" && phase !== "done" && (
            <Card padding="md" className="w-full space-y-3">
              <p className="text-sm font-semibold text-gray-700">내 녹음 들어보기</p>
              {recorder.previewUrl && <audio controls src={recorder.previewUrl} className="w-full" aria-label="내 녹음 재생" />}
              {phase === "converting" || phase === "analyzing" ? (
                <div role="status" className="flex items-center justify-center gap-2 py-2 text-sm font-semibold text-gray-700">
                  <Spinner className="text-[var(--brand-primary)]" />{phase === "converting" ? "녹음을 준비하고 있어요…" : "음성을 인식하고 있어요…"}
                </div>
              ) : (
                <div className="flex gap-2">
                  <Button variant="neutral" fullWidth onClick={resetItem} disabled={busy}><RotateCcw size={16} aria-hidden="true" />다시 녹음</Button>
                  <Button fullWidth onClick={() => void analyze()} disabled={busy}>{phase === "failed" ? "다시 분석하기" : "분석하기"}</Button>
                </div>
              )}
            </Card>
          )}

          {phase === "done" && analysis && (
            <div className="flex w-full flex-col gap-3">
              <SpeechAnalysisResult analysis={analysis} />
              <AiFeedbackPanel analysisId={analysis.analysisId} />
              {saveState === "saving" && <Notice tone="info">학습 기록을 저장하고 있어요…</Notice>}
              {saveState === "saved" && <Notice tone="success">학습 기록에 저장했어요.</Notice>}
              {saveState === "failed" && (
                <div className="flex items-center gap-2">
                  <Notice tone="error" className="flex-1">학습 기록을 저장하지 못했어요.</Notice>
                  <Button size="sm" variant="line" onClick={() => void saveAttempt(analysis)}>다시 저장</Button>
                </div>
              )}
              <div className="flex gap-2">
                <Button variant="neutral" size="lg" fullWidth onClick={resetItem} disabled={busy}><RotateCcw size={16} aria-hidden="true" />다시 녹음</Button>
                <Button size="lg" fullWidth onClick={handleNext} disabled={busy}>{itemIdx < total - 1 ? "다음" : "활동 완료"}</Button>
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
