"use client";

import { useEffect, useRef, useState } from "react";
import { Mic, Play, RotateCcw, Shuffle } from "lucide-react";
import { fetchPracticePage, pickRandomOrder, PRACTICE_PAGE_SIZE, RANDOM_SESSION_SIZE, type PracticeSource } from "@/features/student/hooks/usePracticeSequence";
import type { Exercise, PracticeCategory, PracticeRunMode } from "@/features/student/types";
import { errorMessage } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Mascot from "@/shared/components/mascot/Mascot";
import MenuCard from "@/shared/components/menuCard/MenuCard";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import Tabs from "@/shared/components/tabs/Tabs";

type ListState = { items: Exercise[]; total: number; pages: number; status: "loading" | "ready" | "error"; error: string };
const emptyList: ListState = { items: [], total: 0, pages: 0, status: "loading", error: "" };

/** 목록 카드 보조 설명: 이해력은 질문(답을 미리 보여 주지 않음), 나머지는 문항 미리보기 */
function preview(exercise: Exercise) {
  if (exercise.categoryId === "comprehension") {
    const question = exercise.instruction.split("\n").find((line) => line.startsWith("질문:"));
    if (question) return question;
  }
  const text = exercise.items.map((item) => item.word).join(" · ");
  return text.length > 40 ? `${text.slice(0, 40)}…` : text || `${exercise.items.length}개 항목`;
}

/** 목록의 앞쪽 pages개 페이지를 순서대로 받아 이어 붙인다 */
async function loadPages(categoryId: string, source: PracticeSource, pages: number, color: string) {
  const results = await Promise.all(Array.from({ length: pages }, (_, page) => fetchPracticePage(categoryId, source, page, color)));
  return { items: results.flatMap((result) => result.exercises), total: results[0]?.total ?? 0 };
}

/**
 * 연습 영역 화면. 영역의 전체 콘텐츠를 페이지 단위로 보여 주고(한 번에 다 받지 않음),
 * 전체 순서 연습·랜덤 연습·다시 연습(연습했던 세트)으로 들어간다. 숙제와 상관없이 누구나 전체 콘텐츠를 쓸 수 있다.
 */
export default function PracticeCategoryScreen({ category, focusIndex, onBack, onStart }: {
  category: PracticeCategory;
  /** 연속 연습에서 돌아왔을 때 보여 줄 위치(전체 목록 순서) */
  focusIndex?: number;
  onBack: () => void;
  onStart: (mode: PracticeRunMode, index: number, order?: number[]) => void;
}) {
  const [tab, setTab] = useState<"all" | "retry">("all");
  const [all, setAll] = useState<ListState>(emptyList);
  const [practiced, setPracticed] = useState<ListState>(emptyList);
  const [loadingMore, setLoadingMore] = useState(false);
  const [retryKey, setRetryKey] = useState(0);
  const focused = useRef(false);

  useEffect(() => {
    let active = true;
    const pages = focusIndex === undefined ? 1 : Math.floor(focusIndex / PRACTICE_PAGE_SIZE) + 1;
    loadPages(category.id, "all", pages, category.color)
      .then((result) => { if (active) setAll({ ...result, pages, status: "ready", error: "" }); })
      .catch((cause: unknown) => { if (active) setAll({ ...emptyList, status: "error", error: errorMessage(cause, "연습 목록을 불러오지 못했어요.") }); });
    loadPages(category.id, "practiced", 1, category.color)
      .then((result) => { if (active) setPracticed({ ...result, pages: 1, status: "ready", error: "" }); })
      .catch((cause: unknown) => { if (active) setPracticed({ ...emptyList, status: "error", error: errorMessage(cause, "다시 연습 목록을 불러오지 못했어요.") }); });
    return () => { active = false; };
  }, [category, focusIndex, retryKey]);

  // 연속 연습에서 돌아오면 마지막으로 보던 세트를 목록에서 찾아 보여 준다.
  useEffect(() => {
    if (focusIndex === undefined || focused.current || all.status !== "ready") return;
    const element = document.getElementById(`practice-item-${focusIndex}`);
    if (!element) return;
    focused.current = true;
    element.scrollIntoView?.({ block: "center" });
    element.querySelector<HTMLButtonElement>("button")?.focus({ preventScroll: true });
  }, [focusIndex, all.status]);

  const list = tab === "all" ? all : practiced;
  const setList = tab === "all" ? setAll : setPracticed;
  const loadMore = async () => {
    if (loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetchPracticePage(category.id, tab === "all" ? "all" : "practiced", list.pages, category.color);
      setList((current) => ({ ...current, items: [...current.items, ...next.exercises], total: next.total, pages: current.pages + 1 }));
    } catch (cause) {
      setList((current) => ({ ...current, error: errorMessage(cause, "더 불러오지 못했어요.") }));
    } finally {
      setLoadingMore(false);
    }
  };

  const recent = practiced.items.slice(0, 3);
  const randomCount = Math.min(RANDOM_SESSION_SIZE, all.total);
  return (
    <div className="pb-8">
      <PageHeader title={category.label} subtitle={all.status === "ready" ? `연습 세트 ${all.total}개` : undefined} onBack={onBack} backLabel="연습 영역 목록으로" />
      <div className="space-y-5 px-5 pt-2">
        <Card tone="raised" padding="lg" className="space-y-3 rounded-[var(--radius-card-lg)]">
          <p className="text-sm leading-relaxed text-[var(--ink-600)]">{category.desc}</p>
          <Button size="lg" fullWidth disabled={all.status !== "ready" || all.total === 0} onClick={() => onStart("sequential", 0)}>
            <Play size={18} aria-hidden="true" />전체 {category.label} 연습하기
          </Button>
          <div className="grid grid-cols-2 gap-2">
            <Button variant="secondary" disabled={all.status !== "ready" || all.total === 0}
              onClick={() => onStart("random", 0, pickRandomOrder(all.total))}>
              <Shuffle size={16} aria-hidden="true" />랜덤 연습
            </Button>
            <Button variant="line" disabled={practiced.status !== "ready" || practiced.total === 0} onClick={() => onStart("retry", 0)}>
              <RotateCcw size={16} aria-hidden="true" />다시 연습
            </Button>
          </div>
          <p className="text-xs leading-relaxed text-[var(--ink-500)]">
            전체 연습은 1번부터 차례대로, 랜덤 연습은 {randomCount || RANDOM_SESSION_SIZE}개를 골라 연습해요. 숙제와 상관없이 언제든 연습할 수 있어요.
          </p>
        </Card>

        {recent.length > 0 && (
          <section aria-labelledby="recent-practice-title" className="space-y-2">
            <h3 id="recent-practice-title" className="text-[15px] font-bold text-[var(--ink-900)]">최근 학습</h3>
            {recent.map((exercise, index) => (
              <MenuCard key={exercise.id} icon={RotateCcw} color="var(--sky-500)" title={exercise.label} description="다시 연습하기"
                onClick={() => onStart("retry", index)} />
            ))}
          </section>
        )}

        <Tabs ariaLabel="연습 목록 종류" variant="underline" value={tab} onChange={setTab} className="rounded-t-[var(--radius-lg)]"
          items={[
            { value: "all", label: `전체 목록${all.status === "ready" ? ` ${all.total}` : ""}` },
            { value: "retry", label: `다시 연습${practiced.status === "ready" ? ` ${practiced.total}` : ""}` },
          ]} />

        {list.status === "loading" && <LoadingState label="연습 목록을 불러오고 있어요…" />}
        {list.status === "error" && <ErrorState message={list.error} onRetry={() => { setAll(emptyList); setPracticed(emptyList); setRetryKey((value) => value + 1); }} />}
        {list.status === "ready" && list.items.length === 0 && (
          tab === "all"
            ? <EmptyState title="이 영역에는 아직 연습 세트가 없어요" icon={<Mascot size={64} />} />
            : <EmptyState title="아직 이 영역에서 연습한 기록이 없어요" description="전체 목록에서 연습하면 여기에서 다시 연습할 수 있어요." icon={<Mascot size={64} />} />
        )}
        {list.status === "ready" && list.items.length > 0 && (
          <ol aria-label={tab === "all" ? "전체 목록" : "다시 연습 목록"} className="space-y-2.5">
            {list.items.map((exercise, index) => (
              <li key={`${tab}-${exercise.id}`} id={tab === "all" ? `practice-item-${index}` : undefined}>
                <MenuCard icon={tab === "all" ? Mic : RotateCcw} color={tab === "all" ? exercise.color : "var(--sky-500)"}
                  title={<><span className="mr-1 font-number text-[15px] font-extrabold text-[var(--ink-400)]">{index + 1}</span>{" "}{exercise.label}</>}
                  description={preview(exercise)}
                  onClick={() => onStart(tab === "all" ? "sequential" : "retry", index)} />
              </li>
            ))}
          </ol>
        )}
        {list.status === "ready" && list.error && <ErrorState message={list.error} />}
        {list.status === "ready" && list.items.length < list.total && (
          <Button variant="line" fullWidth loading={loadingMore} loadingLabel="불러오는 중…" onClick={() => void loadMore()}>
            더 보기 ({list.items.length} / {list.total})
          </Button>
        )}
      </div>
    </div>
  );
}
