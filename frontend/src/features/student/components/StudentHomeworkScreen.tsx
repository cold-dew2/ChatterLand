"use client";

import { useEffect, useState } from "react";
import { CheckCircle, ClipboardList, Mic } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { Homework } from "@/features/student/types";
import { isOverdue, mapHomework } from "@/features/student/utils/mappers";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

export default function StudentHomeworkScreen({ onBack, onCompleteHomework, onStartPractice }: {
  onBack: () => void; onCompleteHomework: (id: number) => void; onStartPractice: () => void;
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
      setActionError(errorMessage(cause, "숙제를 완료 처리하지 못했어요."));
    } finally {
      setBusyId(null);
    }
  };
  const retry = () => {
    setLoading(true);
    setError("");
    setRevision((value) => value + 1);
  };
  const pending = homeworks.filter((homework) => !homework.done);
  const completed = homeworks.filter((homework) => homework.done);

  return (
    <div>
      <PageHeader title="숙제하기" onBack={onBack} backLabel="홈으로 돌아가기" />
      <div className="space-y-3 px-5 py-5">
        {loading && <LoadingState label="숙제를 불러오고 있어요…" />}
        {error && <ErrorState message={error} onRetry={retry} />}
        {actionError && <Notice tone="error">{actionError}</Notice>}
        {!loading && !error && homeworks.length === 0 && <EmptyState title="등록된 숙제가 없어요" description="선생님이 숙제를 내주면 이곳에서 확인할 수 있어요." />}
        {!loading && !error && homeworks.length > 0 && <>
          <p className="text-xs font-semibold uppercase tracking-wider text-gray-400">진행 중 {pending.length}</p>
          {pending.length === 0 && <EmptyState title="남은 숙제가 없어요" description="모든 숙제를 완료했어요." variant="plain" />}
          {pending.map((homework) => {
            const overdue = isOverdue(homework.dueDate, homework.done);
            return <Card as="article" key={homework.id}>
              <div className="flex items-start gap-3">
                <ClipboardList size={20} className="mt-0.5 shrink-0 text-[var(--brand-primary)]" aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  <h3 className="text-sm font-bold text-gray-900">{homework.title}</h3>
                  <p className="mt-1 text-xs text-gray-500">{homework.type}{homework.targetMinutes > 0 ? ` · ${homework.targetMinutes}분` : ""}</p>
                  {homework.description && <p className="mt-2 text-sm leading-relaxed text-gray-600">{homework.description}</p>}
                  <p className={`mt-2 text-xs font-medium ${overdue ? "text-red-500" : "text-gray-400"}`}>마감 {homework.dueDate || "미정"}{overdue ? " · 기한 지남" : ""}</p>
                </div>
              </div>
              <div className="mt-3 flex gap-2">
                <Button size="sm" variant="secondary" fullWidth onClick={onStartPractice}><Mic size={14} aria-hidden="true" />연습하러 가기</Button>
                <Button size="sm" fullWidth disabled={busyId !== null} loading={busyId === homework.id} loadingLabel="저장 중…"
                  onClick={() => void completeHomework(homework.id)}>완료</Button>
              </div>
            </Card>;
          })}
          {completed.length > 0 && <>
            <p className="pt-3 text-xs font-semibold uppercase tracking-wider text-gray-400">완료 {completed.length}</p>
            {completed.map((homework) => <Card key={homework.id} tone="muted" className="flex items-center gap-3 text-gray-400">
              <CheckCircle size={20} className="shrink-0 text-green-500" aria-hidden="true" />
              <span className="min-w-0 flex-1 truncate text-sm font-semibold line-through">{homework.title}</span>
              <Badge tone="success">완료</Badge>
            </Card>)}
          </>}
        </>}
      </div>
    </div>
  );
}
