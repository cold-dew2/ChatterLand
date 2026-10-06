"use client";

import { useState } from "react";
import { Sparkles } from "lucide-react";
import AiFeedbackBody from "@/features/aiFeedback/components/AiFeedbackBody";
import type { AiFeedback } from "@/features/student/types";
import { teacherApi } from "@/features/teacher/api/teacherApi";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Spinner from "@/shared/components/spinner/Spinner";

/**
 * 선생님 검토 화면: 학생이 본 AI 설명(같은 저장 결과)을 펼쳐서 확인한다. 조회만 하며 AI를 부르지 않는다.
 * 선생님 판단·확정 오류와 구분해 'AI 설명'으로 표시한다.
 */
export default function TeacherAiFeedback({ analysisId }: { analysisId: string }) {
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<{ kind: "idle" | "loading" } | { kind: "error"; message: string } | { kind: "ready"; feedback: AiFeedback }>({ kind: "idle" });

  const load = () => {
    setState({ kind: "loading" });
    teacherApi.speechFeedback(analysisId)
      .then((feedback) => setState({ kind: "ready", feedback }))
      .catch((cause) => setState({ kind: "error", message: errorMessage(cause, "AI 설명을 불러오지 못했어요.") }));
  };

  if (!open) return (
    <Button size="sm" variant="line" onClick={() => { setOpen(true); load(); }}>
      <Sparkles size={14} aria-hidden="true" />학생이 본 AI 설명 보기
    </Button>
  );

  return (
    <section aria-label="학생이 본 AI 설명" className="space-y-2 rounded-xl border border-gray-100 p-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-semibold text-gray-600">학생이 본 AI 설명</p>
        <Badge tone="neutral">점수·진단 아님</Badge>
      </div>
      {state.kind === "loading" && <p className="flex items-center gap-2 text-xs text-gray-500" role="status"><Spinner />불러오고 있어요…</p>}
      {state.kind === "error" && (
        <div className="flex items-center gap-2">
          <Notice tone="error" className="flex-1">{state.message}</Notice>
          <Button size="sm" variant="line" onClick={load}>다시 시도</Button>
        </div>
      )}
      {state.kind === "ready" && (
        state.feedback.status === "READY" ? (
          <>
            {state.feedback.outdated && <Notice tone="warning">학생이 이 설명을 본 뒤 선생님 판단이나 교육 자료가 바뀌었어요. 이전 근거로 만든 설명이에요.</Notice>}
            <AiFeedbackBody feedback={state.feedback} />
            {state.feedback.generatedAt && <p className="text-xs text-gray-400">만든 시각 {state.feedback.generatedAt.replace("T", " ")}</p>}
          </>
        ) : (
          <p className="text-xs text-gray-500">
            {state.feedback.status === "INSUFFICIENT_SOURCES" ? "근거 자료 부족: " : ""}
            {state.feedback.reason ?? "AI 설명이 없어요."}
          </p>
        )
      )}
    </section>
  );
}
