"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { studentApi } from "@/features/student/api/studentApi";
import type { Exercise, PracticeRunMode, PracticeSelection } from "@/features/student/types";
import { mapExercise, mapPracticeContent } from "@/features/student/utils/mappers";
import { errorMessage } from "@/shared/api/client";

/** 영역 목록·연속 연습이 함께 쓰는 페이지 크기. 전체(수백 개)를 한 번에 받지 않는다. */
export const PRACTICE_PAGE_SIZE = 20;
/** 랜덤 연습 한 번에 고르는 세트 수 */
export const RANDOM_SESSION_SIZE = 10;

export type PracticeSource = "all" | "practiced" | "search";
export type PracticePage = { exercises: Exercise[]; total: number };

/** 영역의 전체 목록(all)·다시 연습 목록(practiced)·검색 결과(search: 공통 콘텐츠 검색, 영역 순서 그대로) 한 페이지 */
export async function fetchPracticePage(categoryId: string, source: PracticeSource, page: number, color?: string, keyword = ""): Promise<PracticePage> {
  if (source === "search") {
    const response = await studentApi.practiceContents({ categoryId, keyword: keyword || undefined, page, size: PRACTICE_PAGE_SIZE });
    return { exercises: response.content.map(mapPracticeContent), total: response.totalElements };
  }
  const response = source === "practiced"
    ? await studentApi.practiced(categoryId, page, PRACTICE_PAGE_SIZE)
    : await studentApi.exercises(categoryId, page, PRACTICE_PAGE_SIZE);
  const payload = response as { content?: Record<string, unknown>[]; totalElements?: number };
  return { exercises: (payload.content ?? []).map((row) => mapExercise(row, color)), total: Number(payload.totalElements ?? 0) };
}

/**
 * 랜덤 연습 순서: 전체 순서(0 ~ total-1) 중 겹치지 않게 size개를 고른다.
 * 시작할 때 한 번만 만들어 흐름 상태에 저장하므로, 이전/다음으로 오가도 순서가 바뀌지 않는다.
 */
export function pickRandomOrder(total: number, size = RANDOM_SESSION_SIZE, random: () => number = Math.random): number[] {
  const count = Math.max(0, Math.min(size, total));
  const picked = new Set<number>();
  while (picked.size < count) picked.add(Math.floor(random() * total));
  return [...picked];
}

type SequenceState =
  | { status: "loading" }
  | { status: "ready"; exercise: Exercise }
  | { status: "error"; message: string };

/** 고른 콘텐츠 하나(공통 콘텐츠 상세). 영역 목록과 같은 형식 */
async function fetchContent(id: string): Promise<Exercise> {
  return mapPracticeContent(await studentApi.practiceContent(Number(id)));
}

/**
 * 연습 흐름에서 index번째 세트를 불러온다. 그 세트가 든 페이지만 받고(페이지별로 기억), 경계 가까이에서는 옆 페이지를 미리 받는다.
 * - sequential: 영역 전체 순서의 index번째
 * - random: order[index]번째(전체 순서 기준)
 * - retry: 다시 연습 목록의 index번째. 흐름을 시작할 때 받은 순서를 그대로 쓴다(연습하면 목록 순서가 바뀌어도 흐름은 흔들리지 않는다).
 * - search: 검색 결과의 index번째(같은 영역·같은 검색어)
 * - selected: 고른 콘텐츠의 index번째(고른 순서). 콘텐츠마다 하나씩 받는다.
 */
export function usePracticeSequence({ categoryId, color, mode, index, order, selection, keyword = "" }: {
  categoryId: string; color?: string; mode: PracticeRunMode; index: number; order?: number[]; selection?: PracticeSelection[]; keyword?: string;
}) {
  const source: PracticeSource = mode === "retry" ? "practiced" : mode === "search" ? "search" : "all";
  const contents = useRef(new Map<string, Promise<Exercise>>());
  const pages = useRef(new Map<number, Promise<PracticePage>>());
  const [total, setTotal] = useState<number | null>(null);
  const [state, setState] = useState<SequenceState>({ status: "loading" });
  const [retryKey, setRetryKey] = useState(0);

  const pageAt = useCallback((page: number) => {
    let request = pages.current.get(page);
    if (!request) {
      request = fetchPracticePage(categoryId, source, page, color, keyword);
      pages.current.set(page, request);
      request.catch(() => pages.current.delete(page)); // 실패한 페이지는 다시 받을 수 있게 지운다
    }
    return request;
  }, [categoryId, source, color, keyword]);

  const contentAt = useCallback((id: string) => {
    let request = contents.current.get(id);
    if (!request) {
      request = fetchContent(id);
      contents.current.set(id, request);
      request.catch(() => contents.current.delete(id));
    }
    return request;
  }, []);

  const position = mode === "random" ? order?.[index] : index;
  const selectedId = mode === "selected" ? selection?.[index]?.id : undefined;

  useEffect(() => {
    let active = true;
    if (mode === "selected") {
      if (!selectedId) {
        Promise.resolve().then(() => { if (active) setState({ status: "error", message: "연습할 세트를 찾지 못했어요." }); });
        return () => { active = false; };
      }
      Promise.resolve().then(() => { if (active) setState({ status: "loading" }); });
      contentAt(selectedId).then((exercise) => { if (active) setState({ status: "ready", exercise }); })
        .catch((cause: unknown) => { if (active) setState({ status: "error", message: errorMessage(cause, "연습을 불러오지 못했어요.") }); });
      // 다음 콘텐츠를 미리 받아 둔다.
      const nextId = selection?.[index + 1]?.id;
      if (nextId) void contentAt(nextId).catch(() => undefined);
      return () => { active = false; };
    }
    if (position === undefined || position < 0) {
      Promise.resolve().then(() => { if (active) setState({ status: "error", message: "연습할 세트를 찾지 못했어요." }); });
      return () => { active = false; };
    }
    const page = Math.floor(position / PRACTICE_PAGE_SIZE);
    Promise.resolve().then(() => { if (active) setState({ status: "loading" }); });
    pageAt(page).then((result) => {
      if (!active) return;
      setTotal(result.total);
      const exercise = result.exercises[position % PRACTICE_PAGE_SIZE];
      setState(exercise ? { status: "ready", exercise } : { status: "error", message: "연습할 세트를 찾지 못했어요. 목록으로 돌아가 다시 골라 주세요." });
      const offset = position % PRACTICE_PAGE_SIZE;
      if (offset >= PRACTICE_PAGE_SIZE - 2 && (page + 1) * PRACTICE_PAGE_SIZE < result.total) void pageAt(page + 1).catch(() => undefined);
      if (offset <= 1 && page > 0) void pageAt(page - 1).catch(() => undefined);
    }).catch((cause: unknown) => {
      if (active) setState({ status: "error", message: errorMessage(cause, "연습을 불러오지 못했어요.") });
    });
    return () => { active = false; };
  }, [mode, selectedId, selection, index, contentAt, position, pageAt, retryKey]);

  /** 이 흐름의 전체 길이: 랜덤·선택은 고른 개수, 나머지는 서버가 알려 준 전체 개수 */
  const length = mode === "random" ? order?.length ?? 0 : mode === "selected" ? selection?.length ?? 0 : total;
  return { state, length, retry: () => setRetryKey((value) => value + 1) };
}
