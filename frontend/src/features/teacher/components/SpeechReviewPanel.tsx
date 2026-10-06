"use client";

import { useEffect, useState } from "react";
import type { ConfirmedError, PhonemeCandidate } from "@/features/student/types";
import { candidateText, errorTypeLabel, textMatchRate } from "@/features/student/utils/speechAssessment";
import { teacherApi } from "@/features/teacher/api/teacherApi";
import SpeechAssessmentDetails from "@/features/teacher/components/SpeechAssessmentDetails";
import TeacherAiFeedback from "@/features/teacher/components/TeacherAiFeedback";
import type { SpeechJudgement, TeacherSpeechAnalysis } from "@/features/teacher/types";
import { errorMessage } from "@/shared/api/client";
import Badge, { type BadgeTone } from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import Input from "@/shared/components/input/Input";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Select from "@/shared/components/select/Select";
import Tabs from "@/shared/components/tabs/Tabs";
import Textarea from "@/shared/components/textarea/Textarea";

type ReviewFilter = "PENDING" | "ALL";

const judgementOptions: { value: SpeechJudgement; label: string }[] = [
  { value: "ACCEPTABLE", label: "목표 발음에 가까움" },
  { value: "NEEDS_PRACTICE", label: "연습 필요" },
  { value: "UNCLEAR", label: "판단 어려움 (다시 녹음 필요)" },
];
const errorTypeOptions = Object.entries(errorTypeLabel).map(([value, label]) => ({ value, label }));

/** 자동 후보를 선생님 확정 오류 형식으로 바꾼다(선생님이 체크한 것만 저장). */
function toConfirmed(candidate: PhonemeCandidate): ConfirmedError {
  const errorType = candidate.type === "SUBSTITUTION" ? "SUBSTITUTION" : candidate.type.endsWith("ADDITION") ? "ADDITION" : "OMISSION";
  return {
    phoneme: (candidate.expected ?? candidate.produced ?? "").slice(0, 4), errorType,
    produced: candidate.produced?.slice(0, 4) ?? null,
    position: candidate.word ? `${candidate.word} ${candidate.syllableIndex}번째 음절`.slice(0, 40) : null,
  };
}

const confirmedKey = (error: ConfirmedError) => `${error.phoneme}|${error.errorType}|${error.produced ?? ""}|${error.position ?? ""}`;

const judgementLabel = Object.fromEntries(judgementOptions.map((option) => [option.value, option.label])) as Record<string, string>;

function reviewBadge(analysis: TeacherSpeechAnalysis): { tone: BadgeTone; label: string } {
  if (analysis.status === "FAILED") return { tone: "danger", label: "분석 실패" };
  if (analysis.reviewStatus === "PENDING") return { tone: "warning", label: "검토 대기" };
  if (analysis.reviewStatus === "REVIEWED") return { tone: "success", label: "검토 완료" };
  return { tone: "neutral", label: "검토 불필요" };
}

function AudioPlayer({ analysisId }: { analysisId: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [state, setState] = useState<"idle" | "loading" | "error">("idle");
  const [error, setError] = useState("");
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);
  const load = async () => {
    setState("loading");
    try {
      const blob = await teacherApi.speechAnalysisAudio(analysisId);
      setUrl(URL.createObjectURL(blob));
      setState("idle");
    } catch (cause) {
      setError(errorMessage(cause, "녹음을 불러오지 못했어요."));
      setState("error");
    }
  };
  if (url) return <audio controls autoPlay src={url} className="w-full" aria-label="학생 녹음 재생" />;
  return (
    <div className="space-y-2">
      <Button size="sm" variant="secondary" loading={state === "loading"} loadingLabel="불러오는 중…" onClick={() => void load()}>녹음 듣기</Button>
      {state === "error" && <Notice tone="error">{error}</Notice>}
    </div>
  );
}

function ReviewForm({ analysis, onSaved }: { analysis: TeacherSpeechAnalysis; onSaved: (updated: TeacherSpeechAnalysis) => void }) {
  const [judgement, setJudgement] = useState<SpeechJudgement | "">((analysis.teacherJudgement as SpeechJudgement | null) ?? "");
  const [note, setNote] = useState(analysis.teacherNote ?? "");
  const candidates = analysis.phonemeCandidates ?? [];
  const [confirmed, setConfirmed] = useState<ConfirmedError[]>(analysis.teacherConfirmedErrors ?? []);
  const [manual, setManual] = useState<{ phoneme: string; errorType: ConfirmedError["errorType"] }>({ phoneme: "", errorType: "DISTORTION" });
  const isChecked = (error: ConfirmedError) => confirmed.some((item) => confirmedKey(item) === confirmedKey(error));
  const toggle = (error: ConfirmedError) => setConfirmed((current) => isChecked(error)
    ? current.filter((item) => confirmedKey(item) !== confirmedKey(error)) : [...current, error]);
  const addManual = () => {
    const phoneme = manual.phoneme.trim();
    if (!phoneme) return;
    toggle({ phoneme: phoneme.slice(0, 4), errorType: manual.errorType, produced: null, position: null });
    setManual({ phoneme: "", errorType: manual.errorType });
  };
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const save = async () => {
    if (saving) return;
    if (!judgement) { setError("검토 결과를 선택해 주세요."); return; }
    setSaving(true); setError(""); setSaved(false);
    try {
      const updated = await teacherApi.reviewSpeechAnalysis(analysis.analysisId, { judgement, note: note.trim() || undefined, confirmedErrors: confirmed });
      onSaved({ ...analysis, ...updated });
      setSaved(true);
    } catch (cause) {
      setError(errorMessage(cause, "검토 결과를 저장하지 못했어요."));
    } finally {
      setSaving(false);
    }
  };
  return (
    <div className="space-y-3 border-t border-gray-100 pt-3">
      <Select label="선생님 검토 결과" size="sm" value={judgement} placeholder="결과를 선택하세요" options={judgementOptions}
        onChange={(event) => { setJudgement(event.target.value as SpeechJudgement); setError(""); }} error={error || undefined} />
      <fieldset className="space-y-1.5">
        <legend className="text-xs font-medium text-gray-600">선생님 확정 오류 (직접 듣고 확인한 것만 선택)</legend>
        {candidates.map((candidate, index) => {
          const error = toConfirmed(candidate);
          return (
            <label key={index} className="flex items-start gap-2 text-xs text-gray-600">
              <input type="checkbox" className="mt-0.5 h-4 w-4 accent-[var(--brand-primary)]" checked={isChecked(error)} onChange={() => toggle(error)} />
              <span>{candidateText(candidate)}</span>
            </label>
          );
        })}
        {confirmed.filter((error) => !candidates.some((candidate) => confirmedKey(toConfirmed(candidate)) === confirmedKey(error))).map((error) => (
          <label key={confirmedKey(error)} className="flex items-start gap-2 text-xs text-gray-600">
            <input type="checkbox" className="mt-0.5 h-4 w-4 accent-[var(--brand-primary)]" checked onChange={() => toggle(error)} />
            <span>{error.phoneme} {errorTypeLabel[error.errorType]}{error.produced ? ` → ${error.produced}` : ""} (직접 추가)</span>
          </label>
        ))}
        <div className="flex items-end gap-2">
          <Input label="음소" size="sm" fieldClassName="w-20" maxLength={4} value={manual.phoneme} placeholder="ㄹ"
            onChange={(event) => setManual({ ...manual, phoneme: event.target.value })} />
          <Select label="오류 유형" size="sm" fieldClassName="flex-1" value={manual.errorType} options={errorTypeOptions}
            onChange={(event) => setManual({ ...manual, errorType: event.target.value as ConfirmedError["errorType"] })} />
          <Button size="sm" variant="secondary" onClick={addManual} disabled={!manual.phoneme.trim()}>추가</Button>
        </div>
      </fieldset>
      <Textarea label="검토 메모" size="sm" rows={2} maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} placeholder="예: ㄹ 받침 소리가 약해요" />
      {saved && <Notice tone="success">검토 결과를 저장했어요.</Notice>}
      <Button size="sm" fullWidth loading={saving} loadingLabel="저장 중…" onClick={() => void save()}>
        {analysis.reviewStatus === "REVIEWED" ? "검토 수정 저장" : "검토 완료"}
      </Button>
    </div>
  );
}

/**
 * 학생 녹음 분석 결과를 선생님이 확인·검토한다.
 * AI 인식 결과와 선생님 판단을 구분해서 보여주고, 발음 점수는 만들지 않는다.
 */
export default function SpeechReviewPanel({ studentId }: { studentId: number }) {
  const [filter, setFilter] = useState<ReviewFilter>("PENDING");
  const [items, setItems] = useState<TeacherSpeechAnalysis[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    teacherApi.speechAnalyses(studentId, filter === "PENDING" ? "PENDING" : undefined, 0, 20).then((page) => {
      if (!active) return;
      setItems(page.content ?? []);
      setState("ready");
    }).catch((cause: unknown) => {
      if (active) { setError(errorMessage(cause, "음성 분석 기록을 불러오지 못했어요.")); setState("error"); }
    });
    return () => { active = false; };
  }, [studentId, filter, retryKey]);

  const changeFilter = (value: ReviewFilter) => { setState("loading"); setFilter(value); };
  const replace = (updated: TeacherSpeechAnalysis) => setItems((current) => current.map((item) => item.analysisId === updated.analysisId ? updated : item));

  return (
    <section aria-labelledby="speech-review-title" className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 id="speech-review-title" className="text-sm font-bold text-gray-700">음성 연습 기록</h3>
      </div>
      <Tabs ariaLabel="검토 상태 필터" variant="chip" value={filter} onChange={changeFilter}
        items={[{ value: "PENDING", label: "검토 대기" }, { value: "ALL", label: "전체" }]} />
      {state === "loading" && <LoadingState label="음성 분석 기록을 불러오고 있어요…" />}
      {state === "error" && <ErrorState message={error} onRetry={() => { setState("loading"); setRetryKey((value) => value + 1); }} />}
      {state === "ready" && items.length === 0 && (
        <EmptyState title={filter === "PENDING" ? "검토할 녹음이 없어요" : "음성 연습 기록이 없어요"}
          description={filter === "PENDING" ? "언어재활 학습자로 설정된 학생의 녹음이 여기에 쌓여요." : "학생이 말하기 연습을 하면 기록이 표시돼요."} />
      )}
      {items.map((analysis) => {
        const badge = reviewBadge(analysis);
        return (
          <Card as="article" key={analysis.analysisId} className="space-y-3">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="text-sm font-semibold text-gray-800">{analysis.exerciseTitle ?? "말하기 연습"}</p>
                <p className="text-xs text-gray-400">{analysis.createdAt}</p>
              </div>
              <Badge tone={badge.tone}>{badge.label}</Badge>
            </div>
            <dl className="space-y-1.5 rounded-xl bg-gray-50 p-3 text-sm">
              <div className="flex gap-2"><dt className="w-20 shrink-0 text-xs text-gray-400">목표</dt><dd className="font-semibold text-gray-800">{analysis.targetText ?? "-"}</dd></div>
              <div className="flex gap-2"><dt className="w-20 shrink-0 text-xs text-gray-400">AI 인식 결과</dt><dd className="font-semibold text-gray-800">{analysis.transcript || "인식 결과 없음"}</dd></div>
              {textMatchRate(analysis) !== null && <div className="flex gap-2"><dt className="w-20 shrink-0 text-xs text-gray-400">텍스트 일치율</dt><dd className="font-semibold text-gray-800">{Math.round(textMatchRate(analysis) ?? 0)}%</dd></div>}
              <div className="flex gap-2"><dt className="w-20 shrink-0 text-xs text-gray-400">발음 평가</dt><dd>{typeof analysis.pronunciationScore === "number" ? `${analysis.pronunciationScore}점 (외부 제공자)` : <Badge tone="neutral">미평가</Badge>}</dd></div>
            </dl>
            <SpeechAssessmentDetails analysis={analysis} />
            {analysis.status === "COMPLETED" && <TeacherAiFeedback analysisId={analysis.analysisId} />}
            {analysis.reviewStatus === "REVIEWED" && analysis.teacherJudgement && (
              <p className="text-xs text-gray-600">선생님 판단: <b>{judgementLabel[analysis.teacherJudgement] ?? analysis.teacherJudgement}</b>{analysis.teacherNote ? ` · ${analysis.teacherNote}` : ""}</p>
            )}
            {analysis.hasAudio ? <AudioPlayer analysisId={analysis.analysisId} /> : <p className="text-xs text-gray-400">보관된 녹음이 없어요. (일반 연습 녹음은 인식 후 바로 삭제돼요)</p>}
            {analysis.status === "COMPLETED" && analysis.reviewStatus && analysis.reviewStatus !== "NOT_REQUIRED" && <ReviewForm analysis={analysis} onSaved={replace} />}
          </Card>
        );
      })}
      <p className="text-xs leading-relaxed text-gray-400">AI 인식 결과는 음성 인식 모델이 알아들은 글자이며 발음 정확도 평가가 아니에요. 오류 후보는 인식 글자를 표기 기준으로 비교한 자동 추정이라 틀린 발음을 놓치거나(모델이 고쳐 적음) 맞는 발음을 후보로 잡을 수 있어요. 발음 판단은 선생님 검토 결과를 기준으로 해 주세요.</p>
    </section>
  );
}
