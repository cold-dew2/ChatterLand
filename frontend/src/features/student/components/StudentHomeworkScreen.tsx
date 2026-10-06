"use client";

import { useEffect, useState } from "react";
import { CheckCircle, ClipboardList, Mic } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { Homework, PracticeContent } from "@/features/student/types";
import { isOverdue, mapHomework, mapPracticeContent } from "@/features/student/utils/mappers";
import { ApiError, errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Mascot from "@/shared/components/mascot/Mascot";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

export default function StudentHomeworkScreen({ onBack, onCompleteHomework, onStartPractice }: {
  onBack: () => void; onCompleteHomework: (id: number) => void;
  /** content: 선생님이 지정한 연습 세트(있으면 바로 그 연습을 숙제로 시작). 없으면 연습 유형 선택으로 */
  onStartPractice: (homework: Homework, content?: PracticeContent) => void;
}) {
  const [homeworks, setHomeworks] = useState<Homework[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;
    studentApi.homeworks(0, 100).then((response) => {
      const payload = response as { content?: Record<string, unknown>[] };
      if (!payload.content) throw new Error("숙제 응답을 확인할 수 없어요.");
      if (active) setHomeworks(payload.content.map(mapHomework));
    }).catch((cause: unknown) => {
      if (active) setError(errorMessage(cause, "숙제를 불러오지 못했어요."));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [revision]);

  const completeHomework = async (id: number) => {
    if (busyId !== null) return;
    setBusyId(id);
    setActionError("");
    try {
      await studentApi.completeHomework(id);
      setHomeworks((current) => current.map((homework) => homework.id === id ? { ...homework, done: true } : homework));
      onCompleteHomework(id);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 404) {
        // 선생님이 삭제한 숙제: 안내하고 목록을 서버 기준으로 다시 불러온다.
        setActionError("선생님이 삭제한 숙제예요. 목록을 새로 불러왔어요.");
        setRevision((value) => value + 1);
      } else {
        setActionError(errorMessage(cause, "숙제를 완료 처리하지 못했어요."));
      }
    } finally {
      setBusyId(null);
    }
  };
  const retry = () => {
    setLoading(true);
    setError("");
    setRevision((value) => value + 1);
  };
  const [startingId, setStartingId] = useState<number | null>(null);
  const startPractice = async (homework: Homework) => {
    if (!homework.exerciseId) { onStartPractice(homework); return; }
    if (startingId !== null) return;
    setStartingId(homework.id);
    setActionError("");
    try {
      onStartPractice(homework, mapPracticeContent(await studentApi.practiceContent(homework.exerciseId)));
    } catch (cause) {
      setActionError(errorMessage(cause, "숙제 연습을 불러오지 못했어요. 다시 시도해 주세요."));
    } finally {
      setStartingId(null);
    }
  };
  const pending = homeworks.filter((homework) => !homework.done);
  const completed = homeworks.filter((homework) => homework.done);

  return (
    <div>
      <PageHeader title="숙제하기" onBack={onBack} backLabel="홈으로 돌아가기" />
      <div className="space-y-3 px-5 pt-2 pb-8">
        {loading && <LoadingState label="숙제를 불러오고 있어요…" />}
        {error && <ErrorState message={error} onRetry={retry} />}
        {actionError && <Notice tone="error">{actionError}</Notice>}
        {!loading && !error && homeworks.length === 0 && <EmptyState title="등록된 숙제가 없어요" description="선생님이 숙제를 내주면 이곳에서 확인할 수 있어요." icon={<Mascot size={64} />} />}
        {!loading && !error && homeworks.length > 0 && <>
          <p className="text-xs font-bold text-[var(--ink-600)]">진행 중 {pending.length}</p>
          {pending.length === 0 && <EmptyState title="남은 숙제가 없어요" description="모든 숙제를 완료했어요." variant="plain" />}
          {pending.map((homework) => {
            const overdue = isOverdue(homework.dueDate, homework.done);
            return <Card as="article" tone="raised" key={homework.id}>
              <div className="flex items-start gap-3">
                <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${overdue ? "bg-[var(--coral-100)] text-[var(--coral-700)]" : "bg-[var(--butter-100)] text-[var(--butter-800)]"}`} aria-hidden="true">
                  <ClipboardList size={19} />
                </span>
                <div className="min-w-0 flex-1">
                  <h3 className="text-base font-bold text-[var(--ink-900)]">{homework.title}</h3>
                  <p className="mt-1 text-xs text-gray-500">{homework.type}{homework.targetMinutes > 0 ? ` · ${homework.targetMinutes}분` : ""}</p>
                  {homework.description && <p className="mt-2 text-sm leading-relaxed text-gray-600">{homework.description}</p>}
                  {homework.exerciseTitle && <p className="mt-2 text-xs text-gray-600">연습: <b>{homework.exerciseTitle}</b>{homework.attemptCount ? ` · ${homework.attemptCount}번 연습함` : ""}</p>}
                  <p className={`mt-2 text-xs font-semibold ${overdue ? "text-[var(--coral-600)]" : "text-[var(--ink-500)]"}`}>마감 {homework.dueDate || "미정"}{overdue ? " · 기한 지남" : ""}</p>
                </div>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-2">
                <Button variant="secondary" fullWidth loading={startingId === homework.id} loadingLabel="불러오는 중…"
                  onClick={() => void startPractice(homework)}><Mic size={14} aria-hidden="true" />연습하러 가기</Button>
                <Button fullWidth disabled={busyId !== null} loading={busyId === homework.id} loadingLabel="저장 중…"
                  onClick={() => void completeHomework(homework.id)}>완료</Button>
              </div>
            </Card>;
          })}
          {completed.length > 0 && <>
            <p className="pt-3 text-xs font-bold text-[var(--ink-600)]">완료 {completed.length}</p>
            {completed.map((homework) => <Card key={homework.id} tone="muted" className="flex items-center gap-3 text-[var(--ink-500)]">
              <CheckCircle size={20} className="shrink-0 text-[var(--meadow-600)]" aria-hidden="true" />
              <span className="min-w-0 flex-1 truncate text-sm font-semibold line-through">{homework.title}</span>
              <Badge tone="success">완료</Badge>
            </Card>)}
          </>}
        </>}
      </div>
    </div>
  );
}
