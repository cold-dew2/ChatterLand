"use client";

import { useEffect, useState } from "react";
import { CheckCircle, ClipboardList } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { Session } from "@/features/student/types";
import { mapSession } from "@/features/student/utils/mappers";
import { errorMessage } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import MenuCard from "@/shared/components/menuCard/MenuCard";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

export default function SessionListScreen({ onSelect, onBack }: { onSelect: (s: Session) => void; onBack: () => void }) {
  const [sessionItems, setSessionItems] = useState<Session[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  useEffect(() => {
    let active = true;
    studentApi.sessions(page, 10).then((response) => {
      const payload = response as { content?: Record<string, unknown>[]; totalPages?: number };
      if (payload.content) {
        const rows = payload.content.map(mapSession);
        if (!active) return;
        setSessionItems((current) => page === 0 ? rows : [...current, ...rows]);
        setHasMore(payload.totalPages === undefined ? rows.length === 10 : page + 1 < payload.totalPages);
      }
    }).catch((cause: unknown) => {
      if (active) setError(errorMessage(cause, "수업 일정을 불러오지 못했어요."));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [page, retryKey]);
  const upcoming = sessionItems.filter((item) => !item.done);
  const completed = sessionItems.filter((item) => item.done);
  return (
    <div>
      <PageHeader title="수업 일정" onBack={onBack} />
      <div className="space-y-3 px-5 pt-2 pb-8">
        {error && <ErrorState message={error} onRetry={() => { setLoading(true); setError(""); setRetryKey((current) => current + 1); }} />}
        {loading && sessionItems.length === 0 && <LoadingState label="수업 일정을 불러오고 있어요…" />}
        {!loading && !error && sessionItems.length === 0 && <EmptyState title="예정된 수업이 없어요" description="새 수업이 배정되면 이곳에 표시돼요." />}
        {!error && upcoming.length > 0 && <p className="text-xs font-bold text-[var(--ink-600)]">예정된 수업</p>}
        {upcoming.map((s) => (
          <MenuCard key={s.id} icon={ClipboardList} color="var(--brand-blue)" title={s.title}
            description={`${s.date} · 활동 ${s.exercises.length}개`} onClick={() => onSelect(s)} />
        ))}
        {completed.length > 0 && (
          <>
            <p className="pt-3 text-xs font-bold text-[var(--ink-600)]">완료된 세션</p>
            {completed.map((s) => (
              <MenuCard key={s.id} icon={CheckCircle} color="var(--ink-400)" title={s.title} description={s.date} muted onClick={() => onSelect(s)} />
            ))}
          </>
        )}
        {hasMore && !error && <Button variant="secondary" fullWidth loading={loading} onClick={() => { setLoading(true); setPage((current) => current + 1); }}>더 보기</Button>}
      </div>
    </div>
  );
}
