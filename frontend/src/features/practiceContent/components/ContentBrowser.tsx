"use client";

import { useEffect, useRef, useState } from "react";
import type { ElementType } from "react";
import { Search, X } from "lucide-react";
import ContentCard from "@/features/practiceContent/components/ContentCard";
import { addSelection, isSelected, MAX_SELECTION, toggleSelection, type SelectedContent } from "@/features/practiceContent/utils/selection";
import type { Exercise } from "@/features/student/types";
import { errorMessage } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import Pagination from "@/shared/components/pagination/Pagination";
import CardGrid from "@/shared/components/cardGrid/CardGrid";

/** 목록 항목: 연습 세트 + (공통 콘텐츠 검색 결과면) 영역 이름 */
export type BrowsableContent = Exercise & { categoryName?: string };
export type ContentPage = { items: BrowsableContent[]; total: number };

type ListState = { start: number; pages: number; items: BrowsableContent[]; total: number; status: "loading" | "ready" | "error"; error: string };

type ContentBrowserProps = {
  /** 목록 출처가 바뀌면(영역·필터) key를 바꿔 처음부터 다시 불러온다 */
  sourceKey: string;
  /** 서버에서 한 쪽을 받는다(전체를 한 번에 받지 않는다). keyword가 빈 문자열이면 검색 없이 전체 */
  fetchPage: (page: number, keyword: string) => Promise<ContentPage>;
  pageSize: number;
  searchable?: boolean;
  searchLabel?: string;
  searchButtonLabel?: string;
  searchPlaceholder?: string;
  /** 선택 모드(카드를 누르면 선택/해제). alwaysSelect면 선택하기 버튼 없이 항상 선택 모드 */
  selectMode: boolean;
  onSelectModeChange?: (value: boolean) => void;
  alwaysSelect?: boolean;
  selection: SelectedContent[];
  onSelectionChange: (next: SelectedContent[]) => void;
  /** 일반 모드에서 카드를 눌렀을 때(index: 이 목록 전체 순서, keyword: 적용된 검색어) */
  onOpen?: (exercise: Exercise, index: number, keyword: string) => void;
  describe?: (exercise: Exercise) => string;
  icon: ElementType;
  color: (exercise: Exercise) => string;
  /** 처음 보여 줄 위치(전체 순서). 그 위치가 든 쪽부터 보여 주고 카드에 초점을 둔다 */
  focusIndex?: number;
  emptyTitle: string;
  emptyDescription?: string;
  dense?: boolean;
  listLabel: string;
  /** 이미 받아 둔 첫 쪽(검색어 없이). 있으면 바로 보여 주고 다시 받지 않는다 */
  initialPage?: ContentPage;
};

const emptyList: ListState = { start: 0, pages: 0, items: [], total: 0, status: "loading", error: "" };

/**
 * 많은 연습 콘텐츠를 찾고 고르는 목록(학생 연습·선생님 숙제 공통).
 * 서버 검색 + 전체 개수 + 더 보기 + 쪽 이동(맨 끝으로 바로) + 선택(쪽·검색어가 바뀌어도 유지)을 함께 제공한다.
 */
export default function ContentBrowser({
  sourceKey, fetchPage, pageSize, searchable = true, searchLabel = "콘텐츠 검색", searchButtonLabel = "검색", searchPlaceholder = "단어 또는 문장을 검색하세요",
  selectMode, onSelectModeChange, alwaysSelect = false, selection, onSelectionChange, onOpen, describe, icon, color, focusIndex,
  emptyTitle, emptyDescription, dense = false, listLabel, initialPage,
}: ContentBrowserProps) {
  const [draft, setDraft] = useState("");
  const [keyword, setKeyword] = useState("");
  const usesInitial = useRef(Boolean(initialPage) && focusIndex === undefined);
  const [list, setList] = useState<ListState>(() => usesInitial.current && initialPage
    ? { start: 0, pages: 1, items: initialPage.items, total: initialPage.total, status: "ready", error: "" } : emptyList);
  const [busy, setBusy] = useState(false);
  const [retryKey, setRetryKey] = useState(0);
  const [skipped, setSkipped] = useState(0);
  const fetchRef = useRef(fetchPage);
  const request = useRef(0);
  const topRef = useRef<HTMLDivElement>(null);
  const focused = useRef(false);
  const selecting = alwaysSelect || selectMode;

  useEffect(() => { fetchRef.current = fetchPage; }, [fetchPage]);

  /** start쪽 하나를 새로 보여 준다(이전 결과는 버린다. 늦게 온 응답도 버린다) */
  const show = async (start: number, nextKeyword: string) => {
    const id = ++request.current;
    setList((current) => ({ ...current, status: "loading", error: "" }));
    try {
      const page = await fetchRef.current(start, nextKeyword);
      if (id !== request.current) return;
      setList({ start, pages: 1, items: page.items, total: page.total, status: "ready", error: "" });
    } catch (cause) {
      if (id === request.current) setList({ ...emptyList, status: "error", error: errorMessage(cause, "목록을 불러오지 못했어요.") });
    }
  };

  useEffect(() => {
    if (usesInitial.current) { usesInitial.current = false; return; } // 첫 쪽은 이미 받아 둔 것을 쓴다
    const start = focusIndex === undefined || keyword ? 0 : Math.floor(focusIndex / pageSize);
    Promise.resolve().then(() => show(start, keyword));
    // show는 매번 새로 만들어지지만 ref로 최신 fetchPage를 쓰므로 출처·검색어가 바뀔 때만 다시 부른다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceKey, keyword, retryKey, pageSize]);

  // 연속 연습에서 돌아오면 보던 카드로 초점을 옮긴다.
  useEffect(() => {
    if (focusIndex === undefined || focused.current || list.status !== "ready" || keyword) return;
    const element = document.getElementById(`content-item-${sourceKey}-${focusIndex}`);
    if (!element) return;
    focused.current = true;
    element.scrollIntoView?.({ block: "center" });
    element.querySelector<HTMLButtonElement>("button")?.focus({ preventScroll: true });
  }, [focusIndex, list.status, keyword, sourceKey]);

  const extend = async (direction: "next" | "prev") => {
    if (busy) return;
    const id = request.current;
    setBusy(true);
    try {
      const target = direction === "next" ? list.start + list.pages : list.start - 1;
      const page = await fetchRef.current(target, keyword);
      if (id !== request.current) return;
      setList((current) => direction === "next"
        ? { ...current, pages: current.pages + 1, items: [...current.items, ...page.items], total: page.total }
        : { ...current, start: current.start - 1, pages: current.pages + 1, items: [...page.items, ...current.items], total: page.total });
    } catch (cause) {
      setList((current) => ({ ...current, error: errorMessage(cause, "더 불러오지 못했어요.") }));
    } finally {
      setBusy(false);
    }
  };

  const jump = (page: number) => {
    void show(page, keyword);
    topRef.current?.scrollIntoView?.({ block: "start" });
  };

  const totalPages = Math.max(1, Math.ceil(list.total / pageSize));
  const firstNumber = list.start * pageSize;
  const shown = list.items.map((exercise, offset) => ({ exercise, index: firstNumber + offset }));
  const asSelected = (exercise: BrowsableContent): SelectedContent => ({ id: exercise.id, label: exercise.label, ...(exercise.categoryName ? { categoryName: exercise.categoryName } : {}) });
  const full = selection.length >= MAX_SELECTION;
  /** 현재 목록 전체 선택에서 최대 개수 때문에 담지 못한 개수(안내용) */
  const selectCurrentList = () => {
    const candidates = list.items.map(asSelected).filter((item) => !isSelected(selection, item.id));
    const next = addSelection(selection, candidates);
    setSkipped(candidates.length - (next.length - selection.length));
    onSelectionChange(next);
  };

  return (
    <div className="space-y-3" ref={topRef}>
      {searchable && (
        <form role="search" className="flex items-end gap-2" onSubmit={(event) => { event.preventDefault(); setKeyword(draft.trim()); }}>
          <Input label={searchLabel} hideLabel placeholder={searchPlaceholder} value={draft} maxLength={50} fieldClassName="min-w-0 flex-1"
            onChange={(event) => setDraft(event.target.value)}
            endAdornment={<Search size={18} aria-hidden="true" />} />
          <Button type="submit" variant="secondary" className="min-h-[54px] shrink-0 rounded-[var(--radius-control)]">{searchButtonLabel}</Button>
        </form>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <p className="text-[13px] font-semibold text-[var(--ink-700)]" aria-live="polite">
          {list.status === "ready" ? (keyword ? `검색 결과 ${list.total}개` : `전체 ${list.total}개`) : " "}
        </p>
        {keyword && (
          <button type="button" onClick={() => { setDraft(""); setKeyword(""); }} aria-label={`검색어 ${keyword} 지우기`}
            className="inline-flex min-h-8 items-center gap-1 rounded-full bg-[var(--ink-100)] px-3 text-xs font-semibold text-[var(--ink-700)] hover:bg-[var(--ink-150)]">
            검색어: {keyword}<X size={13} aria-hidden="true" />
          </button>
        )}
        {!alwaysSelect && onSelectModeChange && (
          <Button size="sm" variant={selectMode ? "secondary" : "line"} className="ml-auto" aria-pressed={selectMode}
            onClick={() => onSelectModeChange(!selectMode)}>{selectMode ? "선택 끝내기" : "선택하기"}</Button>
        )}
      </div>

      {selecting && list.status === "ready" && list.items.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 rounded-[var(--radius-lg)] bg-[var(--surface-sunken)] px-3 py-2">
          <span className="text-[13px] font-bold text-[var(--meadow-900)]">선택 {selection.length}개</span>
          <Button size="sm" variant="line" className="ml-auto" disabled={full} onClick={selectCurrentList}>현재 목록 전체 선택</Button>
          <Button size="sm" variant="ghost" disabled={selection.length === 0} onClick={() => { setSkipped(0); onSelectionChange([]); }}>선택 해제</Button>
        </div>
      )}
      {selecting && (full || skipped > 0) && (
        <Notice tone="info">
          한 번에 {MAX_SELECTION}개까지 고를 수 있어요.{skipped > 0 ? ` ${skipped}개는 담지 못했어요.` : ""} 더 고르려면 고른 콘텐츠를 빼 주세요.
        </Notice>
      )}

      {list.status === "loading" && <LoadingState label="목록을 불러오고 있어요…" />}
      {list.status === "error" && <ErrorState message={list.error} onRetry={() => setRetryKey((value) => value + 1)} />}
      {list.status === "ready" && list.items.length === 0 && (
        keyword
          ? <EmptyState title="검색 결과가 없어요" description="다른 낱말이나 문장으로 찾아보세요." />
          : <EmptyState title={emptyTitle} description={emptyDescription} />
      )}

      {list.status === "ready" && list.start > 0 && (
        <Button variant="line" fullWidth loading={busy} loadingLabel="불러오는 중…" onClick={() => void extend("prev")}>
          앞쪽 항목 보기 ({firstNumber}번까지)
        </Button>
      )}
      {list.status === "ready" && list.items.length > 0 && (
        <CardGrid as="ol" aria-label={listLabel} gap="md">
          {shown.map(({ exercise, index }) => (
            <li key={`${exercise.id}-${index}`} id={`content-item-${sourceKey}-${index}`}>
              <ContentCard number={index + 1} title={exercise.label} description={describe?.(exercise)} icon={icon} color={color(exercise)} dense={dense}
                mode={selecting ? "select" : "open"} selected={isSelected(selection, exercise.id)}
                disabled={selecting && full && !isSelected(selection, exercise.id)}
                onToggle={() => { setSkipped(0); onSelectionChange(toggleSelection(selection, asSelected(exercise))); }}
                onOpen={() => onOpen?.(exercise, index, keyword)} />
            </li>
          ))}
        </CardGrid>
      )}
      {list.status === "ready" && list.error && <Notice tone="error">{list.error}</Notice>}
      {list.status === "ready" && firstNumber + list.items.length < list.total && (
        <Button variant="line" fullWidth loading={busy} loadingLabel="불러오는 중…" onClick={() => void extend("next")}>
          더 보기 ({firstNumber + list.items.length} / {list.total})
        </Button>
      )}
      {list.status === "ready" && totalPages > 1 && (
        <div className="space-y-1.5 pt-1">
          <p className="text-center text-xs text-[var(--ink-500)]">{firstNumber + 1}–{firstNumber + list.items.length}번을 보고 있어요 · 쪽을 골라 바로 이동</p>
          <Pagination ariaLabel={`${listLabel} 쪽 이동`} page={list.start + list.pages - 1} totalPages={totalPages} onChange={jump} />
        </div>
      )}
    </div>
  );
}
