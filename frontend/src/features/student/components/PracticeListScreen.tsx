"use client";

import { useEffect, useState } from "react";
import { Mic } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { LoadState, PracticeCategory } from "@/features/student/types";
import { errorMessage } from "@/shared/api/client";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import MenuCard from "@/shared/components/menuCard/MenuCard";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

export default function PracticeListScreen({ onSelect, onBack }: { onSelect: (c: PracticeCategory) => void; onBack: () => void }) {
  const [categories, setCategories] = useState<PracticeCategory[]>([]);
  const [state, setState] = useState<LoadState>("loading");
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  useEffect(() => {
    let active = true;
    studentApi.categories().then((rows) => {
      if (!active) return;
      setCategories(rows.map((item) => ({
        id: String(item.categoryId ?? item.id), label: String(item.name ?? item.label ?? "연습"),
        color: String(item.color ?? "var(--brand-blue)"), desc: String(item.description ?? item.desc ?? "말하기 연습"), exercises: [],
      })));
      setState("ready");
    }).catch((cause: unknown) => { if (active) { setError(errorMessage(cause, "연습 목록을 불러오지 못했어요.")); setState("error"); } });
    return () => { active = false; };
  }, [retryKey]);
  return (
    <div>
      <PageHeader title="연습하기" onBack={onBack} />
      <div className="px-5 py-5 space-y-3">
        {state === "loading" && <LoadingState label="연습 목록을 불러오고 있어요…" />}
        {state === "error" && <ErrorState message={error} onRetry={() => { setState("loading"); setRetryKey((value) => value + 1); }} />}
        {state === "ready" && categories.length === 0 && <EmptyState title="준비된 연습이 없어요" />}
        {categories.map((cat) => (
          <MenuCard key={cat.id} icon={Mic} color={cat.color} title={cat.label} description={cat.desc} onClick={() => onSelect(cat)} />
        ))}
      </div>
    </div>
  );
}
