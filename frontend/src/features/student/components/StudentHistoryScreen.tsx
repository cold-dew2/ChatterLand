"use client";

import { useEffect, useState } from "react";
import { Bot, BookOpen, Calendar } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { HistoryItem } from "@/features/student/types";
import { mapHistoryItem } from "@/features/student/utils/mappers";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Tabs from "@/shared/components/tabs/Tabs";

type HistoryFilter = "전체" | "대화" | "단어" | "숙제";

const historyTypeMap: Record<HistoryFilter, HistoryItem["type"] | null> = {
  전체: null, 대화: "ai", 단어: "word", 숙제: "hw",
};
const historyFilters = (Object.keys(historyTypeMap) as HistoryFilter[]).map((value) => ({ value, label: value }));

function HistoryItemIcon({ type }: { type: HistoryItem["type"] }) {
  if (type === "ai") {
    return (
      <div className="w-11 h-11 rounded-full flex items-center justify-center shrink-0 bg-orange-100" aria-hidden="true">
        <Bot size={22} className="text-orange-500" />
      </div>
    );
  }
  if (type === "word") {
    return (
      <div className="w-11 h-11 rounded-full flex items-center justify-center shrink-0 bg-red-50" aria-hidden="true">
        <svg viewBox="0 0 36 36" width="26" height="26" xmlns="http://www.w3.org/2000/svg">
          <ellipse cx="18" cy="21" rx="11" ry="12" fill="#ef4444" />
          <ellipse cx="18" cy="19" rx="11" ry="11" fill="#f87171" />
          <ellipse cx="13" cy="14" rx="4" ry="3" fill="#fca5a5" opacity="0.45" />
          <path d="M17 9 Q18 5 21 6" stroke="#16a34a" strokeWidth="2" fill="none" strokeLinecap="round" />
        </svg>
      </div>
    );
  }
  return (
    <div className="w-11 h-11 rounded-full flex items-center justify-center shrink-0 bg-green-100" aria-hidden="true">
      <BookOpen size={20} className="text-green-600" />
    </div>
  );
}

/** 저장된 실제 값만 표시한다: 외부 분석 점수 > 문장 일치도 > 미평가 */
function HistoryResult({ item }: { item: HistoryItem }) {
  if (item.score !== null) return <span className="text-sm font-bold text-gray-700">{item.score}점</span>;
  if (item.matchRate !== null) return <span className="text-sm font-bold text-gray-700">일치도 {Math.round(item.matchRate)}%</span>;
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
      <div className="flex items-center gap-2 px-5 pt-8 pb-4">
        <span className="p-1.5 text-gray-300" aria-hidden="true"><Calendar size={17} /></span>
        <span className="text-sm font-semibold text-gray-800 flex-1">{dateLabel}</span>
      </div>

      <Tabs ariaLabel="기록 유형" variant="pill" items={historyFilters} value={filter} onChange={selectFilter} className="px-5 mb-4" />

      {/* History list */}
      <div className="px-5 space-y-2 flex-1">
        {loading && history.length === 0 && <LoadingState label="기록을 불러오고 있어요…" />}
        {error && <ErrorState message={error} onRetry={retry} />}
        {visibleHistory.map((item) => (
          <Card key={`${item.type}-${item.id}`} className="flex items-center gap-3 px-4 py-3.5" padding="none">
            <HistoryItemIcon type={item.type} />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-gray-900 truncate">{item.title}</p>
              <p className="text-xs text-gray-400 mt-0.5">{item.date}{item.time ? ` · ${item.time}` : ""}</p>
            </div>
            <div className="shrink-0 text-right"><HistoryResult item={item} /></div>
          </Card>
        ))}

        {!loading && !error && visibleHistory.length === 0 && (
          <EmptyState title="기록이 없어요" description="연습하거나 숙제를 하면 기록이 쌓여요." variant="plain" />
        )}
      </div>

      {canLoadMore && !error && visibleHistory.length > 0 && (
        <div className="px-5 mt-4 pb-6">
          <Button fullWidth loading={loading} loadingLabel="불러오는 중…" onClick={() => { setLoading(true); setPage((current) => current + 1); }}>
            더 보기
          </Button>
        </div>
      )}
    </div>
  );
}
