"use client";

import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { AiFeedback } from "@/features/student/types";
import { isConsentRequired } from "@/features/student/utils/speechErrors";
import { ApiError, errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import Notice from "@/shared/components/feedback/Notice";
import Spinner from "@/shared/components/spinner/Spinner";

const sourceLabel = { AUTO_ANALYSIS: "자동 분석", TEACHER_CONFIRMED: "선생님 확인 결과" } as const;

type PanelState =
  | { kind: "loading" }
  | { kind: "loadError"; message: string }
  | { kind: "feedback"; feedback: AiFeedback };

/**
 * 분석 결과 아래의 AI 학습 피드백. 측정값(텍스트 일치율 등)과 분리해 'AI 설명'으로 표시하고 점수는 보여 주지 않는다.
 * 아직 만들지 않음(버튼) · 만드는 중 · 설명 · 평가할 수 없음(이유) · 동의 필요 · AI 오류(다시 시도)를 구분한다.
 */
export default function AiFeedbackPanel({ analysisId }: { analysisId: string }) {
  const [state, setState] = useState<PanelState>({ kind: "loading" });
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState("");
  const [consentNeeded, setConsentNeeded] = useState(false);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;
    // 호출 자체가 실패해도(예외) 결과 화면 전체가 아니라 이 패널만 오류로 표시한다.
    Promise.resolve().then(() => studentApi.speechFeedback(analysisId))
      .then((feedback) => { if (active) { setState({ kind: "feedback", feedback }); setConsentNeeded(Boolean(feedback.consentRequired)); } })
      .catch((cause) => { if (active) setState({ kind: "loadError", message: errorMessage(cause, "AI 설명 상태를 불러오지 못했어요.") }); });
    return () => { active = false; };
  }, [analysisId, revision]);

  const generate = async () => {
    if (generating) return;
    setGenerating(true);
    setGenerateError("");
    try {
      const feedback = await studentApi.generateSpeechFeedback(analysisId);
      setState({ kind: "feedback", feedback });
    } catch (cause) {
      if (isConsentRequired(cause)) setConsentNeeded(true);
      else setGenerateError(cause instanceof ApiError && cause.code === "AI_NOT_CONFIGURED"
        ? "지금은 AI 설명을 쓸 수 없어요. 선생님께 알려 주세요."
        : errorMessage(cause, "AI 설명을 만들지 못했어요. 잠시 뒤 다시 시도해 주세요."));
    } finally {
      setGenerating(false);
    }
  };

  if (state.kind === "loading") return null;
  if (state.kind === "loadError") return (
    <div className="flex items-center gap-2">
      <Notice tone="error" className="flex-1">{state.message}</Notice>
      <Button size="sm" variant="line" onClick={() => { setState({ kind: "loading" }); setRevision((value) => value + 1); }}>다시 시도</Button>
    </div>
  );

  const { feedback } = state;
  if (feedback.status === "NOT_EVALUABLE") return (
    <Notice tone="info"><b>AI 설명</b> · {feedback.reason ?? "이 녹음은 설명할 수 없어요."}</Notice>
  );

  return (
    <Card padding="lg" className="space-y-3" aria-label="AI 설명">
      <div className="flex items-center justify-between gap-2">
        <p className="flex items-center gap-1.5 text-sm font-semibold text-gray-700"><Sparkles size={15} aria-hidden="true" className="text-[var(--brand-primary)]" />AI 설명</p>
        <Badge tone="neutral">점수 아님</Badge>
      </div>
      {feedback.status === "READY" ? (
        <>
          <p className="text-sm leading-relaxed text-gray-800">{feedback.text}</p>
          <p className="text-xs leading-relaxed text-gray-400">
            {(feedback.basedOn ?? []).map((source) => sourceLabel[source]).join(" · ") || "분석 결과"}를 바탕으로 AI가 쉽게 풀어 쓴 설명이에요. 점수나 진단이 아니에요.
          </p>
        </>
      ) : consentNeeded ? (
        <Notice tone="info">AI 설명을 보려면 마이페이지의 동의 관리에서 &apos;AI 대화 외부 전송&apos;에 동의해 주세요. 분석 결과(글자)만 보내고 목소리는 보내지 않아요.</Notice>
      ) : feedback.available === false ? (
        <Notice tone="info">지금은 AI 설명을 쓸 수 없어요.</Notice>
      ) : generating ? (
        <p className="flex items-center gap-2 text-sm text-gray-500" role="status"><Spinner className="text-[var(--brand-primary)]" />AI가 설명을 쓰고 있어요…</p>
      ) : (
        <>
          <p className="text-sm text-gray-500">컴퓨터가 알아들은 결과를 AI가 쉽게 설명해 줄 수 있어요.</p>
          {generateError && <Notice tone="error">{generateError}</Notice>}
          <Button variant="line" fullWidth onClick={() => void generate()}>{generateError ? "다시 시도" : "AI 설명 보기"}</Button>
        </>
      )}
    </Card>
  );
}
