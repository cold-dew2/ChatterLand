"use client";

import { useEffect, useRef, useState } from "react";
import { Search } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import type { HistoryItem, PracticeCategory, PracticeContent } from "@/features/student/types";
import { mapHistoryItem, mapPracticeContent } from "@/features/student/utils/mappers";
import { contentTypeLabel, contentTypeOptions, difficultyLabel, difficultyOptions, ruleLabel, ruleOptions } from "@/features/student/utils/practiceLabels";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Input from "@/shared/components/input/Input";
import Mascot from "@/shared/components/mascot/Mascot";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import Select from "@/shared/components/select/Select";

const PAGE_SIZE = 20;
type Filters = { keyword: string; categoryId: string; difficulty: string; rule: string; contentType: string };
const EMPTY: Filters = { keyword: "", categoryId: "", difficulty: "", rule: "", contentType: "" };
const all = (options: { value: string; label: string }[]) => [{ value: "", label: "전체" }, ...options];

/**
 * 전체 연습 찾기(숙제 없이 하는 자율 연습). 검색어·카테고리·난이도·발음 유형·콘텐츠 유형으로 고르고 바로 연습한다.
 * 발음 유형은 글자에 들어 있는 발음 규칙으로 나눈 것이며, 아이의 발음을 평가한 결과가 아니다.
 */
export default function PracticeBrowseScreen({ onBack, onStart }: { onBack: () => void; onStart: (content: PracticeContent) => void }) {
  const [draft, setDraft] = useState<Filters>(EMPTY);
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [items, setItems] = useState<PracticeContent[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [categories, setCategories] = useState<{ value: string; label: string }[]>([]);
  const [recent, setRecent] = useState<HistoryItem[]>([]);
  const request = useRef(0);

  useEffect(() => {
    studentApi.categories().then((rows) => setCategories(rows.map((row) => ({ value: String(row.categoryId ?? row.id), label: String(row.name ?? row.label ?? "연습") })))).catch(() => undefined);
    studentApi.history("word", 0, 3).then((response) => {
      const payload = response as { content?: Record<string, unknown>[] };
      setRecent((payload.content ?? []).map(mapHistoryItem));
    }).catch(() => undefined);
  }, []);

  useEffect(() => {
    const id = ++request.current;
    studentApi.practiceContents({ ...filters, keyword: filters.keyword.trim() || undefined, page: 0, size: PAGE_SIZE }).then((response) => {
      if (id !== request.current) return; // 늦게 도착한 이전 검색 결과는 버린다
      setItems(response.content.map(mapPracticeContent)); setTotal(response.totalElements); setPage(0); setState("ready");
    }).catch((cause: unknown) => { if (id === request.current) { setError(errorMessage(cause, "연습 목록을 불러오지 못했어요.")); setState("error"); } });
  }, [filters, retryKey]);

  const loadMore = async () => {
    if (loadingMore) return;
    const id = request.current;
    setLoadingMore(true);
    try {
      const response = await studentApi.practiceContents({ ...filters, keyword: filters.keyword.trim() || undefined, page: page + 1, size: PAGE_SIZE });
      if (id !== request.current) return;
      setItems((current) => [...current, ...response.content.map(mapPracticeContent)]); setPage(page + 1);
    } catch (cause) {
      setError(errorMessage(cause, "더 불러오지 못했어요."));
    } finally {
      setLoadingMore(false);
    }
  };

  const apply = (next: Filters) => { setDraft(next); setFilters(next); setState("loading"); };

  return (
    <div>
      <PageHeader title="전체 연습 찾기" onBack={onBack} />
      <div className="space-y-4 px-5 pt-2 pb-8">
        {recent.length > 0 && (
          <section aria-label="최근 연습" className="space-y-2">
            <p className="text-xs font-bold text-[var(--ink-600)]">최근 연습</p>
            {recent.map((item) => (
              <Card key={item.id} padding="none" className="flex min-h-12 items-center gap-2 px-4 py-2.5">
                <p className="min-w-0 flex-1 truncate text-sm font-medium text-[var(--ink-800)]">{item.title}</p>
                {item.attemptType === "HOMEWORK" && <Badge tone="info">숙제</Badge>}
                <span className="text-xs text-gray-400">{item.date}</span>
              </Card>
            ))}
          </section>
        )}

        <form className="space-y-3" role="search" onSubmit={(event) => { event.preventDefault(); apply(draft); }}>
          <div className="flex items-end gap-2">
            <Input label="검색" hideLabel placeholder="낱말이나 문장으로 찾기 (예: 받침, 고구마)" value={draft.keyword} maxLength={50} fieldClassName="flex-1"
              onChange={(event) => setDraft({ ...draft, keyword: event.target.value })} />
            <Button type="submit" variant="secondary" aria-label="검색하기" className="h-[54px] w-[54px] shrink-0 rounded-[var(--radius-control)] px-0"><Search size={20} aria-hidden="true" /></Button>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Select label="카테고리" size="sm" value={draft.categoryId} options={all(categories)} onChange={(event) => apply({ ...draft, categoryId: event.target.value })} />
            <Select label="난이도" size="sm" value={draft.difficulty} options={all(difficultyOptions)} onChange={(event) => apply({ ...draft, difficulty: event.target.value })} />
            <Select label="발음 유형" size="sm" value={draft.rule} options={all(ruleOptions)} onChange={(event) => apply({ ...draft, rule: event.target.value })} />
            <Select label="콘텐츠 유형" size="sm" value={draft.contentType} options={all(contentTypeOptions)} onChange={(event) => apply({ ...draft, contentType: event.target.value })} />
          </div>
        </form>

        {state === "loading" && <LoadingState label="연습 목록을 불러오고 있어요…" />}
        {state === "error" && <ErrorState message={error} onRetry={() => { setState("loading"); setRetryKey((value) => value + 1); }} />}
        {state === "ready" && (
          <>
            <p className="text-[13px] font-semibold text-[var(--ink-600)]" aria-live="polite">연습 세트 {total}개</p>
            {items.length === 0 && <EmptyState title="조건에 맞는 연습이 없어요" description="검색어를 바꾸거나 필터를 '전체'로 바꿔 보세요." variant="plain" icon={<Mascot size={64} />} />}
            <ul className="space-y-2">
              {items.map((content) => (
                <li key={content.id}>
                  <Card tone="raised" padding="none" className="space-y-2.5 px-4 py-4">
                    <div className="flex items-start justify-between gap-2">
                      <p className="min-w-0 pt-1 text-[15px] font-bold text-[var(--ink-900)]">{content.label}</p>
                      <Button size="sm" onClick={() => onStart(content)} aria-label={`${content.label} 연습 시작`}>연습 시작</Button>
                    </div>
                    <p className="truncate text-[13px] text-[var(--ink-600)]">{content.items.map((item) => item.word).join(" · ")}</p>
                    <div className="flex flex-wrap gap-1">
                      {content.pronunciationRule && <Badge tone="info">{ruleLabel[content.pronunciationRule]}</Badge>}
                      {content.difficulty && <Badge tone="neutral">{difficultyLabel[content.difficulty]}</Badge>}
                      {content.contentType && <Badge tone="neutral">{contentTypeLabel[content.contentType]}</Badge>}
                      <Badge tone="neutral">{content.items.length}개</Badge>
                    </div>
                  </Card>
                </li>
              ))}
            </ul>
            {items.length < total && <Button variant="line" fullWidth loading={loadingMore} loadingLabel="불러오는 중…" onClick={() => void loadMore()}>더 보기</Button>}
          </>
        )}
      </div>
    </div>
  );
}

/** 연습 콘텐츠 하나를 기존 연습 화면(SpeechActivityScreen)이 받는 카테고리 형식으로 바꾼다. */
export function asPracticeCategory(content: PracticeContent): PracticeCategory {
  return { id: `content-${content.id}`, label: content.label, color: content.color, desc: content.instruction, exercises: [content] };
}
