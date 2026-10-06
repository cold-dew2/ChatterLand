"use client";

import { useEffect, useState } from "react";
import { Mic } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { Exercise, LoadState, PracticeCategory } from "@/features/student/types";
import { mapExercise } from "@/features/student/utils/mappers";
import { errorMessage } from "@/shared/api/client";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import MenuCard from "@/shared/components/menuCard/MenuCard";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

export default function PracticeCategoryScreen({ category, onSelect, onBack }: {
  category: PracticeCategory; onSelect: (idx: number, exercises: Exercise[]) => void; onBack: () => void;
}) {
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [state, setState] = useState<LoadState>("loading");
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  useEffect(() => {
    let active = true;
    studentApi.exercises(category.id, 0, 20).then((response) => {
      const payload = response as { content?: Record<string, unknown>[] };
      if (!active) return;
      setExercises((payload.content ?? []).map((item) => mapExercise(item, category.color)));
      setState("ready");
    }).catch((cause: unknown) => { if (active) { setError(errorMessage(cause, "연습 문제를 불러오지 못했어요.")); setState("error"); } });
    return () => { active = false; };
  }, [category, retryKey]);
  return (
    <div>
      <PageHeader title={category.label} onBack={onBack} />
      <div className="space-y-3 px-5 pt-2 pb-8">
        {state === "loading" && <LoadingState label="연습 문제를 불러오고 있어요…" />}
        {state === "error" && <ErrorState message={error} onRetry={() => { setState("loading"); setRetryKey((value) => value + 1); }} />}
        {state === "ready" && exercises.length === 0 && <EmptyState title="이 영역에는 아직 연습 문제가 없어요" />}
        {exercises.map((ex, i) => (
          <MenuCard key={ex.id} icon={Mic} color={category.color} title={ex.label}
            description={`${ex.items.length}개 항목`} onClick={() => onSelect(i, exercises)} />
        ))}
      </div>
    </div>
  );
}
