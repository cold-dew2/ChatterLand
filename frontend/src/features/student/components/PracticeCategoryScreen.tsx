"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Mic, Play, RotateCcw, Shuffle, X } from "lucide-react";
import ContentBrowser, { type BrowsableContent } from "@/features/practiceContent/components/ContentBrowser";
import { MAX_SELECTION, removeSelection, type SelectedContent } from "@/features/practiceContent/utils/selection";
import { fetchPracticePage, pickRandomOrder, PRACTICE_PAGE_SIZE, RANDOM_SESSION_SIZE } from "@/features/student/hooks/usePracticeSequence";
import type { Exercise, PracticeCategory, PracticeRunMode } from "@/features/student/types";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import Mascot from "@/shared/components/mascot/Mascot";
import MenuCard from "@/shared/components/menuCard/MenuCard";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import Tabs from "@/shared/components/tabs/Tabs";

type ListTab = "all" | "selected" | "retry";

/** 목록 카드 보조 설명: 이해력은 질문(답을 미리 보여 주지 않음), 나머지는 문항 미리보기 */
export function preview(exercise: Exercise) {
  if (exercise.categoryId === "comprehension") {
    const question = exercise.instruction.split("\n").find((line) => line.startsWith("질문:"));
    if (question) return question;
  }
  const text = exercise.items.map((item) => item.word).join(" · ");
  return text.length > 40 ? `${text.slice(0, 40)}…` : text || `${exercise.items.length}개 항목`;
}

/**
 * 연습 영역 화면. 영역의 콘텐츠를 서버에서 쪽 단위로 받아 보여 주고(한 번에 다 받지 않음), 검색·쪽 이동·선택을 함께 제공한다.
 * 진입 방식: 전체 순서 연습 · 랜덤 연습 · 다시 연습(연습했던 세트) · 선택 연습(고른 순서) · 검색 결과 연습.
 * 숙제와 상관없이 누구나 전체 콘텐츠를 쓸 수 있다.
 */
export default function PracticeCategoryScreen({ category, focusIndex, selection: controlledSelection, onSelectionChange, onBack, onStart }: {
  category: PracticeCategory;
  /** 전체 순서 연습에서 돌아왔을 때 보여 줄 위치 */
  focusIndex?: number;
  /** 고른 콘텐츠. 상위 화면이 주면 연습하고 돌아와도 유지된다(주지 않으면 이 화면 안에서만 기억) */
  selection?: SelectedContent[];
  onSelectionChange?: (next: SelectedContent[]) => void;
  onBack: () => void;
  /** order: 랜덤 순서 · extra: 검색어(검색 결과 연습)·고른 콘텐츠(선택 연습) */
  onStart: (mode: PracticeRunMode, index: number, order?: number[], extra?: { keyword?: string; selection?: SelectedContent[] }) => void;
}) {
  const [ownSelection, setOwnSelection] = useState<SelectedContent[]>([]);
  const selection = controlledSelection ?? ownSelection;
  const setSelection = onSelectionChange ?? setOwnSelection;
  const [tab, setTab] = useState<ListTab>("all");
  const [selectMode, setSelectMode] = useState(false);
  const [total, setTotal] = useState<number | null>(null);
  const [practiced, setPracticed] = useState<{ items: Exercise[]; total: number } | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;
    fetchPracticePage(category.id, "practiced", 0, category.color)
      .then((page) => { if (active) setPracticed({ items: page.exercises, total: page.total }); })
      .catch(() => { if (active) setPracticed({ items: [], total: 0 }); });
    return () => { active = false; };
  }, [category]);

  // 전체 목록: 검색어가 없으면 영역 목록 API, 있으면 공통 콘텐츠 검색(같은 영역·같은 순서)
  const fetchAll = useCallback(async (page: number, keyword: string) => {
    const result = await fetchPracticePage(category.id, keyword ? "search" : "all", page, category.color, keyword);
    if (!keyword) setTotal(result.total);
    return { items: result.exercises as BrowsableContent[], total: result.total };
  }, [category]);
  const fetchPracticed = useCallback(async (page: number) => {
    const result = await fetchPracticePage(category.id, "practiced", page, category.color);
    return { items: result.exercises as BrowsableContent[], total: result.total };
  }, [category]);

  const showTab = (next: ListTab) => { setTab(next); listRef.current?.scrollIntoView?.({ block: "start" }); };
  const recent = practiced?.items.slice(0, 3) ?? [];
  const randomCount = Math.min(RANDOM_SESSION_SIZE, total ?? RANDOM_SESSION_SIZE);
  // 선택 중이거나 고른 콘텐츠가 있으면 아래에 연습 시작 막대를 둔다(0개면 버튼을 막는다)
  const showSelectionBar = selectMode || selection.length > 0;
  return (
    <div className={showSelectionBar ? "pb-28" : "pb-8"}>
      <PageHeader title={category.label} subtitle={total !== null ? `연습 세트 ${total}개` : undefined} onBack={onBack} backLabel="연습 영역 목록으로" />
      <div className="space-y-5 px-5 pt-2">
        <Card tone="raised" padding="lg" className="space-y-3 rounded-[var(--radius-card-lg)]">
          <p className="text-sm leading-relaxed text-[var(--ink-600)]">{category.desc}</p>
          <Button size="lg" fullWidth disabled={!total} onClick={() => onStart("sequential", 0)}>
            <Play size={18} aria-hidden="true" />전체 {category.label} 연습하기
          </Button>
          <div className="grid grid-cols-2 gap-2">
            <Button variant="secondary" disabled={!total} onClick={() => onStart("random", 0, pickRandomOrder(total ?? 0))}>
              <Shuffle size={16} aria-hidden="true" />랜덤 연습
            </Button>
            <Button variant="line" disabled={!practiced?.total} onClick={() => onStart("retry", 0)}>
              <RotateCcw size={16} aria-hidden="true" />다시 연습
            </Button>
          </div>
          <p className="text-xs leading-relaxed text-[var(--ink-500)]">
            전체 연습은 1번부터 차례대로, 랜덤 연습은 {randomCount}개를 골라 연습해요. 목록에서 원하는 것만 골라 연습할 수도 있어요. 숙제와 상관없이 언제든 연습할 수 있어요.
          </p>
        </Card>

        {recent.length > 0 && (
          <section aria-labelledby="recent-practice-title" className="space-y-2">
            <div className="flex items-center justify-between">
              <h3 id="recent-practice-title" className="text-[15px] font-bold text-[var(--ink-900)]">최근 학습</h3>
              <button type="button" onClick={() => showTab("retry")} className="min-h-9 rounded-lg px-1 text-[13px] font-semibold text-[var(--brand-primary)] hover:underline">전체 보기</button>
            </div>
            {recent.map((exercise, index) => (
              <MenuCard key={exercise.id} icon={RotateCcw} color="var(--sky-500)" title={exercise.label} description="다시 연습하기"
                onClick={() => onStart("retry", index)} />
            ))}
          </section>
        )}

        <div ref={listRef}>
          <Tabs ariaLabel="연습 목록 종류" variant="underline" value={tab} onChange={setTab} className="rounded-t-[var(--radius-lg)]"
            items={[
              { value: "all", label: `전체${total !== null ? ` ${total}` : ""}` },
              { value: "selected", label: `선택 ${selection.length}` },
              { value: "retry", label: `다시 연습${practiced ? ` ${practiced.total}` : ""}` },
            ]} />
        </div>

        {tab === "all" && (
          <ContentBrowser sourceKey={`all-${category.id}`} fetchPage={fetchAll} pageSize={PRACTICE_PAGE_SIZE}
            searchPlaceholder="단어 또는 문장을 검색하세요" searchLabel={`${category.label} 콘텐츠 검색`}
            selectMode={selectMode} onSelectModeChange={setSelectMode} selection={selection} onSelectionChange={setSelection}
            onOpen={(_, index, keyword) => keyword ? onStart("search", index, undefined, { keyword }) : onStart("sequential", index)}
            describe={preview} icon={Mic} color={(exercise) => exercise.color || category.color} focusIndex={focusIndex}
            emptyTitle="이 영역에는 아직 연습 세트가 없어요" listLabel={`${category.label} 전체 목록`} />
        )}

        {tab === "retry" && (
          <ContentBrowser sourceKey={`retry-${category.id}`} fetchPage={fetchPracticed} pageSize={PRACTICE_PAGE_SIZE} searchable={false}
            selectMode={selectMode} onSelectModeChange={setSelectMode} selection={selection} onSelectionChange={setSelection}
            initialPage={practiced ? { items: practiced.items as BrowsableContent[], total: practiced.total } : undefined}
            onOpen={(_, index) => onStart("retry", index)} describe={preview} icon={RotateCcw} color={() => "var(--sky-500)"}
            emptyTitle="아직 이 영역에서 연습한 기록이 없어요" emptyDescription="전체 목록에서 연습하면 여기에서 다시 연습할 수 있어요." listLabel="다시 연습 목록" />
        )}

        {tab === "selected" && (
          selection.length === 0
            ? <EmptyState title="아직 고른 콘텐츠가 없어요" description="전체 목록에서 선택하기를 누르고 연습할 콘텐츠를 골라 보세요." icon={<Mascot size={64} />}
                action={<Button variant="secondary" onClick={() => { setTab("all"); setSelectMode(true); }}>콘텐츠 고르러 가기</Button>} />
            : <ol aria-label="고른 콘텐츠" className="space-y-2">
                {selection.map((item, index) => (
                  <li key={item.id} className="flex min-h-14 items-center gap-3 rounded-[var(--radius-xl)] border border-[var(--line-soft)] bg-white px-4 py-2.5">
                    <span className="font-number text-[15px] font-extrabold text-[var(--ink-400)]">{index + 1}</span>
                    <button type="button" onClick={() => onStart("selected", index, undefined, { selection })} className="min-w-0 flex-1 truncate text-left text-[15px] font-bold text-[var(--ink-900)] hover:underline">
                      {item.label}
                    </button>
                    <Button variant="ghost" size="icon" aria-label={`${item.label} 빼기`} onClick={() => setSelection(removeSelection(selection, item.id))}><X size={18} /></Button>
                  </li>
                ))}
              </ol>
        )}
      </div>

      {showSelectionBar && (
        <div className="sticky bottom-0 z-10 mt-4 border-t border-[var(--line-soft)] bg-white/95 px-5 pt-3 pb-5 backdrop-blur">
          <div className="flex items-center gap-2">
            <Button size="lg" className="min-w-0 flex-1" disabled={selection.length === 0} onClick={() => onStart("selected", 0, undefined, { selection })}>
              <Play size={18} aria-hidden="true" />{selection.length > 0 ? `선택한 ${selection.length}개 연습하기` : "선택한 콘텐츠 연습하기"}
            </Button>
            <Button variant="line" size="lg" disabled={selection.length === 0} onClick={() => setSelection([])}>선택 해제</Button>
          </div>
          {selection.length === 0 && <p className="mt-2 text-center text-xs text-[var(--ink-500)]">연습할 콘텐츠를 목록에서 골라 주세요. 최대 {MAX_SELECTION}개까지 고를 수 있어요.</p>}
        </div>
      )}
    </div>
  );
}
