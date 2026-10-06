"use client";

import { useEffect, useState } from "react";
import { Apple, Bot, BookOpen, Calendar } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { HistoryItem } from "@/features/student/types";
import { mapHistoryItem } from "@/features/student/utils/mappers";
import { errorMessage } from "@/shared/api/client";
import { attemptTypeLabel } from "@/features/student/utils/practiceLabels";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Mascot from "@/shared/components/mascot/Mascot";
import Tabs from "@/shared/components/tabs/Tabs";

type HistoryFilter = "전체" | "대화" | "단어" | "숙제";

const historyTypeMap: Record<HistoryFilter, HistoryItem["type"] | null> = {
  전체: null, 대화: "ai", 단어: "word", 숙제: "hw",
};
const historyFilters = (Object.keys(historyTypeMap) as HistoryFilter[]).map((value) => ({ value, label: value }));

const historyIcon = {
  ai: { icon: Bot, tint: "bg-[var(--butter-100)] text-[var(--butter-800)]" },
  word: { icon: Apple, tint: "bg-[var(--coral-100)] text-[var(--coral-600)]" },
  hw: { icon: BookOpen, tint: "bg-[var(--meadow-100)] text-[var(--meadow-800)]" },
} as const;

function HistoryItemIcon({ type }: { type: HistoryItem["type"] }) {
  const { icon: Icon, tint } = historyIcon[type];
  return (
    <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${tint}`} aria-hidden="true">
      <Icon size={21} />
    </div>
  );
}

/** 저장된 실제 값만 표시한다: 외부 분석 점수 > 텍스트 일치율 > 미평가 */
function HistoryResult({ item }: { item: HistoryItem }) {
  if (item.score !== null) return <span className="text-sm font-bold text-[var(--ink-800)]">{item.score}점</span>;
  if (item.matchRate !== null) return <span className="text-xs font-semibold text-[var(--ink-700)]">텍스트 일치율 {Math.round(item.matchRate)}%</span>;
  if (item.type === "word") return <Badge tone="neutral">미평가</Badge>;
  return null;
}

export default function StudentHistoryScreen() {
  const [filter, setFilter] = useState<HistoryFilter>("전체");
  const dateLabel = new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", weekday: "short" }).format(new Date());
  const [page, setPage] = useState(0);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [canLoadMore, setCanLoadMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    studentApi.history(historyTypeMap[filter] ?? undefined, page, 3).then((response) => {
      const payload = response as { content?: Record<string, unknown>[]; items?: Record<string, unknown>[]; totalPages?: number };
      const rows = (payload.content ?? payload.items ?? []).map(mapHistoryItem);
      if (!active) return;
      setHistory((current) => page === 0 ? rows : [...current, ...rows]);
      setCanLoadMore(page + 1 < (payload.totalPages ?? 0));
      setError("");
    }).catch((cause: unknown) => {
      if (active) setError(errorMessage(cause, "학습 기록을 불러오지 못했어요."));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filter, page, retryKey]);

  const visibleHistory = history.filter((entry) => historyTypeMap[filter] === null || entry.type === historyTypeMap[filter]);
  const selectFilter = (value: HistoryFilter) => {
    setLoading(true); setError(""); setHistory([]); setCanLoadMore(true); setPage(0); setFilter(value);
  };
  const retry = () => { setLoading(true); setError(""); setRetryKey((current) => current + 1); };

  return (
    <div className="flex flex-col min-h-full">
      {/* Date header */}
      <div className="px-5 pt-8 pb-4">
        <h1 className="font-display text-[28px] leading-tight text-[var(--ink-900)]">히스토리</h1>
        <p className="mt-1 flex items-center gap-1.5 text-[13px] font-medium text-[var(--ink-600)]"><Calendar size={15} aria-hidden="true" />{dateLabel}</p>
      </div>

      <Tabs ariaLabel="기록 유형" variant="pill" items={historyFilters} value={filter} onChange={selectFilter} className="px-5 mb-4" />

      {/* History list */}
      <div className="px-5 space-y-2 flex-1">
        {loading && history.length === 0 && <LoadingState label="기록을 불러오고 있어요…" />}
        {error && <ErrorState message={error} onRetry={retry} />}
        {visibleHistory.map((item) => (
          <Card key={`${item.type}-${item.id}`} className="flex min-h-[72px] items-center gap-3 px-4 py-3.5" padding="none">
            <HistoryItemIcon type={item.type} />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-gray-900 truncate">{item.title}</p>
              {item.attemptType && <p className="mt-0.5"><Badge tone={item.attemptType === "HOMEWORK" ? "info" : "neutral"}>{attemptTypeLabel[item.attemptType]}</Badge></p>}
              <p className="text-xs text-gray-400 mt-0.5">{item.date}{item.time ? ` · ${item.time}` : ""}</p>
            </div>
            <div className="shrink-0 text-right"><HistoryResult item={item} /></div>
          </Card>
        ))}

        {!loading && !error && visibleHistory.length === 0 && (
          filter === "전체"
            ? <EmptyState title="기록이 없어요" description="연습하거나 숙제를 하면 기록이 쌓여요." variant="plain" icon={<Mascot size={72} />} />
            : <EmptyState title={`${filter} 기록이 없어요`} description="다른 종류를 고르거나 '전체'에서 모든 기록을 확인해 보세요." variant="plain" />
        )}
      </div>

      {canLoadMore && !error && visibleHistory.length > 0 && (
        <div className="px-5 mt-4 pb-6">
          <Button variant="line" fullWidth loading={loading} loadingLabel="불러오는 중…" onClick={() => { setLoading(true); setPage((current) => current + 1); }}>
            더 보기
          </Button>
        </div>
      )}
    </div>
  );
}
