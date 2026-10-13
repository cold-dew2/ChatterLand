"use client";

import { useEffect, useState } from "react";
import { ChevronRight, Sparkles } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { AppScreen } from "@/features/student/types";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import CardGrid from "@/shared/components/cardGrid/CardGrid";
import { cardClassName } from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";

export type RecommendedContent = {
  exerciseId: number; title: string; categoryId: string; categoryName: string; color: string;
  kind: "REVIEW" | "NEW"; reasonCode: string; reason: string;
};
type Recommendations = { personalized: boolean; basis: string; items: RecommendedContent[]; pendingHomeworks: number };

export function mapRecommendations(row: Record<string, unknown>): Recommendations {
  const items = Array.isArray(row.items) ? row.items as Record<string, unknown>[] : [];
  return {
    personalized: row.personalized === true, basis: String(row.basis ?? ""), pendingHomeworks: Number(row.pendingHomeworks ?? 0) || 0,
    items: items.map((item) => ({
      exerciseId: Number(item.exerciseId), title: String(item.title ?? ""), categoryId: String(item.categoryId ?? ""),
      categoryName: String(item.categoryName ?? ""), color: String(item.color ?? "var(--brand-primary)"),
      kind: item.kind === "REVIEW" ? "REVIEW" : "NEW", reasonCode: String(item.reasonCode ?? ""), reason: String(item.reason ?? ""),
    })),
  };
}

/** 콘텐츠 하나를 기존 연습 흐름(선택 연습, 해당 영역으로 돌아가기)으로 연다. 추천·AI 대화 관련 연습이 함께 쓴다 */
export type PracticeTarget = Pick<RecommendedContent, "exerciseId" | "title" | "categoryId" | "categoryName" | "color">;
export function practiceScreenFor(item: PracticeTarget): AppScreen {
  return {
    kind: "practice-run", category: { id: item.categoryId, label: item.categoryName, color: item.color, desc: "", exercises: [] },
    mode: "selected", index: 0, results: [], selection: [{ id: String(item.exerciseId), label: item.title }], origin: "category",
  };
}

/**
 * 학생 홈의 추천 연습. 서버가 저장된 연습 기록과 콘텐츠 분류로 고른 결과와 이유를 그대로 보여 준다.
 * 기록이 없으면 기본 콘텐츠이며 맞춤 추천이라고 하지 않는다. 진행 중 숙제 콘텐츠는 숙제하기에서 연습하도록 따로 안내한다.
 */
export default function RecommendationSection({ onNavigate }: { onNavigate: (target: AppScreen) => void }) {
  const [data, setData] = useState<Recommendations | null>(null);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    studentApi.recommendations()
      .then((row) => { if (active) { setData(mapRecommendations(row)); setError(""); } })
      .catch((cause: unknown) => { if (active) setError(errorMessage(cause, "추천 연습을 불러오지 못했어요.")); });
    return () => { active = false; };
  }, [retryKey]);

  return (
    <section aria-labelledby="recommend-title" className="space-y-2.5">
      <div className="flex items-center gap-2">
        <h2 id="recommend-title" className="text-base font-bold text-[var(--ink-900)]">{data && !data.personalized ? "이렇게 시작해 보세요" : "추천 연습"}</h2>
        {data && <Badge tone={data.personalized ? "primary" : "neutral"}>{data.personalized ? "내 기록 기반" : "기본 추천"}</Badge>}
      </div>
      {!data && !error && <LoadingState label="추천 연습을 고르고 있어요…" />}
      {error && <ErrorState message={error} onRetry={() => { setError(""); setRetryKey((value) => value + 1); }} />}
      {data && <>
        <p className="text-xs leading-relaxed text-[var(--ink-500)]">{data.basis}</p>
        {data.items.length === 0
          ? <EmptyState title="지금 추천할 연습이 없어요" description="연습하기에서 원하는 콘텐츠를 골라 보세요." />
          : <CardGrid as="ul" gap="sm" aria-label="추천 연습 목록">
              {data.items.map((item) => (
                <li key={item.exerciseId}>
                  <button type="button" onClick={() => onNavigate(practiceScreenFor(item))} aria-label={`${item.title} 연습하기, ${item.reason}`}
                    className={cardClassName({ interactive: true, padding: "sm", className: "flex min-h-[64px] items-center gap-3" })}>
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full" aria-hidden="true"
                      style={{ backgroundColor: `color-mix(in srgb, ${item.color} 18%, white)`, color: `color-mix(in srgb, ${item.color} 70%, var(--ink-950))` }}>
                      <Sparkles size={16} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[15px] font-bold text-[var(--ink-900)]">{item.title}</span>
                      <span className="block truncate text-xs text-[var(--ink-600)]">{item.categoryName} · {item.reason}</span>
                    </span>
                    <ChevronRight size={16} className="shrink-0 text-[var(--ink-300)]" aria-hidden="true" />
                  </button>
                </li>
              ))}
            </CardGrid>}
        {data.pendingHomeworks > 0 && (
          <button type="button" onClick={() => onNavigate({ kind: "homework-list" })}
            className="min-h-9 rounded-lg px-1 text-[13px] font-semibold text-[var(--brand-primary)] hover:underline">
            숙제 {data.pendingHomeworks}개는 숙제하기에서 연습해요 (숙제 기록으로 저장)
          </button>
        )}
      </>}
    </section>
  );
}
