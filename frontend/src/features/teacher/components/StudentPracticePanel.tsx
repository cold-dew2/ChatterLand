"use client";

import { useEffect, useState } from "react";
import TeacherAiFeedback from "@/features/teacher/components/TeacherAiFeedback";
import { teacherApi, type StudentAttempt, type StudentAttemptSummary } from "@/features/teacher/api/teacherApi";
import { attemptTypeLabel, contentTypeLabel, ruleLabel } from "@/features/student/utils/practiceLabels";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Tabs from "@/shared/components/tabs/Tabs";

type Filter = "ALL" | "SELF" | "LESSON" | "HOMEWORK" | "PRACTICE";
const statusLabel: Record<string, string> = { COMPLETED: "분석 완료", PROCESSING: "분석 중", FAILED: "분석 실패", PENDING: "대기" };

/**
 * 담당 학생의 연습 기록(학생이 따로 제출하지 않아도 저장된 기록). 자율·수업·숙제 연습을 구분해 보여 준다.
 * '이전 기록'은 유형을 구분하기 전에 저장되어 자율/수업 출처를 알 수 없는 기록이다(임의로 재분류하지 않음).
 * 텍스트 일치율은 음성 인식 글자 비교라 발음 정확도가 아니므로 통계로 쓰지 않고 기록마다 참고로만 표시한다.
 */
export default function StudentPracticePanel({ studentId }: { studentId: number }) {
  const [filter, setFilter] = useState<Filter>("ALL");
  const [summary, setSummary] = useState<StudentAttemptSummary | null>(null);
  const [rows, setRows] = useState<StudentAttempt[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let active = true;
    const type = filter === "ALL" ? undefined : filter;
    Promise.all([teacherApi.studentAttemptSummary(studentId), teacherApi.studentAttempts(studentId, type, 0, 20)]).then(([nextSummary, pageData]) => {
      if (!active) return;
      setSummary(nextSummary); setRows(pageData.content); setTotal(pageData.totalElements); setPage(0); setState("ready");
    }).catch((cause: unknown) => { if (active) { setError(errorMessage(cause, "연습 기록을 불러오지 못했어요.")); setState("error"); } });
    return () => { active = false; };
  }, [studentId, filter, retryKey]);

  const loadMore = async () => {
    if (loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await teacherApi.studentAttempts(studentId, filter === "ALL" ? undefined : filter, page + 1, 20);
      setRows((current) => [...current, ...next.content]); setPage(page + 1);
    } catch (cause) {
      setError(errorMessage(cause, "더 불러오지 못했어요."));
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <section aria-label="연습 기록" className="space-y-3">
      <h3 className="text-base font-bold text-[var(--ink-900)]">연습 기록</h3>
      {summary && (
        <dl className="grid grid-cols-2 gap-2 text-center sm:grid-cols-4">
          {[["전체", summary.totalCount], ["자율 연습", summary.selfCount ?? 0], ["수업 연습", summary.lessonCount ?? 0], ["숙제 연습", summary.homeworkCount]].map(([label, value]) => (
            <Card key={label} padding="sm"><dt className="text-xs font-medium text-[var(--ink-500)]">{label}</dt><dd className="mt-0.5 font-number text-xl font-black text-[var(--ink-900)]">{value}회</dd></Card>
          ))}
        </dl>
      )}
      {summary?.lastPracticedAt && <p className="text-xs text-[var(--ink-500)]">최근 연습 {summary.lastPracticedAt} · 연습한 세트 {summary.exerciseCount}개
        {summary.unclassifiedCount ? ` · 이전 기록 ${summary.unclassifiedCount}회(유형 구분 전)` : ""}</p>}
      <Tabs ariaLabel="연습 기록 종류" variant="chip" value={filter} onChange={(value) => { setFilter(value); setState("loading"); }}
        items={[{ value: "ALL" as const, label: "전체" }, { value: "SELF" as const, label: "자율 연습" }, { value: "LESSON" as const, label: "수업 연습" },
          { value: "HOMEWORK" as const, label: "숙제 연습" }, ...(summary?.unclassifiedCount ? [{ value: "PRACTICE" as const, label: "이전 기록" }] : [])]} />
      {state === "loading" && <LoadingState label="연습 기록을 불러오고 있어요…" />}
      {state === "error" && <ErrorState message={error} onRetry={() => { setState("loading"); setRetryKey((value) => value + 1); }} />}
      {state === "ready" && rows.length === 0 && <EmptyState title={filter === "ALL" ? "아직 연습 기록이 없어요" : "이 종류의 연습 기록이 없어요"} variant="plain" />}
      {state === "ready" && rows.map((row) => (
        <Card as="article" key={row.attemptId} className="space-y-2">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge tone={row.attemptType === "HOMEWORK" ? "info" : "neutral"}>{attemptTypeLabel[row.attemptType] ?? row.attemptType}</Badge>
            {row.pronunciationRule && <Badge tone="neutral">{ruleLabel[row.pronunciationRule]}</Badge>}
            {row.contentType && <Badge tone="neutral">{contentTypeLabel[row.contentType]}</Badge>}
            {row.analysisStatus && <Badge tone={row.analysisStatus === "COMPLETED" ? "success" : row.analysisStatus === "FAILED" ? "danger" : "neutral"}>{statusLabel[row.analysisStatus] ?? row.analysisStatus}</Badge>}
            {row.assessmentStatus === "HOLD" && <Badge tone="warning">판정 보류</Badge>}
            <span className="ml-auto text-xs text-gray-400">{row.createdAt}</span>
          </div>
          <p className="text-[15px] font-bold text-[var(--ink-900)]">{row.exerciseTitle}{row.homeworkTitle ? ` · 숙제 "${row.homeworkTitle}"` : ""}</p>
          <dl className="space-y-1 rounded-[var(--radius-xl)] bg-[var(--surface-sunken)] px-3.5 py-3 text-sm">
            <div className="flex gap-2"><dt className="w-20 shrink-0 pt-px text-xs text-[var(--ink-500)]">목표</dt><dd className="text-gray-800">{row.targetText ?? "-"}</dd></div>
            <div className="flex gap-2"><dt className="w-20 shrink-0 pt-px text-xs text-[var(--ink-500)]">인식 결과</dt><dd className="text-gray-800">{row.transcript || "-"}</dd></div>
            {typeof row.textMatchRate === "number" && <div className="flex gap-2"><dt className="w-20 shrink-0 pt-px text-xs text-[var(--ink-500)]">텍스트 일치율</dt><dd className="text-gray-800">{Math.round(row.textMatchRate)}% <span className="text-xs text-gray-400">(발음 점수 아님)</span></dd></div>}
            <div className="flex gap-2"><dt className="w-20 shrink-0 pt-px text-xs text-[var(--ink-500)]">발음 평가</dt><dd><Badge tone="neutral">미평가</Badge></dd></div>
          </dl>
          {row.analysisId && row.analysisStatus === "COMPLETED" && <TeacherAiFeedback analysisId={row.analysisId} />}
        </Card>
      ))}
      {state === "ready" && rows.length < total && <Button variant="line" fullWidth loading={loadingMore} loadingLabel="불러오는 중…" onClick={() => void loadMore()}>더 보기</Button>}
    </section>
  );
}
