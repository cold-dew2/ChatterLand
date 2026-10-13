"use client";

import { useEffect, useState } from "react";
import { attemptTypeLabel, changeText, chartDays, mapLearningReport, PERIOD_LABELS, type LearningReport, type ReportPeriod } from "@/features/learningReport/utils/learningReport";
import { errorMessage } from "@/shared/api/client";
import Card from "@/shared/components/card/Card";
import CardGrid from "@/shared/components/cardGrid/CardGrid";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import ProgressBar from "@/shared/components/progressBar/ProgressBar";
import Stat from "@/shared/components/stat/Stat";
import Tabs from "@/shared/components/tabs/Tabs";

const PERIODS: ReportPeriod[] = ["7d", "30d", "all"];

type Props = {
  /** 기간별 리포트 불러오기(학생 본인·선생님 담당 학생·보호자 연결 자녀 API 중 하나) */
  load: (period: ReportPeriod) => Promise<Record<string, unknown>>;
  /** 바뀌면 다시 불러온다(예: 선생님 화면에서 학생을 바꿨을 때) */
  reloadKey?: string | number | null;
  initialPeriod?: ReportPeriod;
  /** 기록이 없을 때 안내(학생: 연습해 보라는 말, 선생님·보호자: 기록이 없다는 말) */
  emptyDescription?: string;
};

/**
 * 누적 학습 리포트 화면(학생·선생님·보호자 공통). 서버가 센 숫자를 그대로 보여 주고 점수·정확도·진단을 만들지 않는다.
 * 이전 기간 비교는 서버가 비교값을 줄 때(이전 기간에 기록이 있을 때)만 보여 준다.
 */
export default function LearningReportView({ load, reloadKey = null, initialPeriod = "30d", emptyDescription = "연습하고 녹음을 저장하면 여기에 쌓여요." }: Props) {
  const [period, setPeriod] = useState<ReportPeriod>(initialPeriod);
  const [report, setReport] = useState<LearningReport | null>(null);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    load(period)
      .then((row) => { if (active) { setReport(mapLearningReport(row)); setError(""); } })
      .catch((cause: unknown) => { if (active) { setReport(null); setError(errorMessage(cause, "학습 리포트를 불러오지 못했어요.")); } });
    return () => { active = false; };
    // load는 화면마다 새로 만든 함수라 의존성에서 빼고, 학생·기간·다시 시도로만 다시 부른다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period, reloadKey, retryKey]);

  const changePeriod = (next: ReportPeriod) => { setReport(null); setError(""); setPeriod(next); };
  const loading = !report && !error;
  const shown = report && report.period === period ? report : null;
  return (
    <div className="space-y-4">
      <Tabs ariaLabel="리포트 기간" variant="chip" value={period} onChange={changePeriod} items={PERIODS.map((value) => ({ value, label: PERIOD_LABELS[value] }))} />
      {loading && <LoadingState label="학습 리포트를 불러오고 있어요…" />}
      {error && <ErrorState message={error} onRetry={() => { setError(""); setRetryKey((value) => value + 1); }} />}
      {shown && <ReportBody report={shown} emptyDescription={emptyDescription} />}
    </div>
  );
}

function ReportBody({ report, emptyDescription }: { report: LearningReport; emptyDescription: string }) {
  const range = report.period === "all"
    ? (report.startDate ? `처음 연습한 날(${report.startDate})부터 ${report.endDate}까지` : `${report.endDate}까지`)
    : `${report.startDate} ~ ${report.endDate}`;
  if (report.totalAttempts === 0) {
    return <>
      <p className="text-xs text-[var(--ink-500)]">{range}</p>
      <EmptyState title={report.period === "all" ? "아직 저장된 연습 기록이 없어요" : `${PERIOD_LABELS[report.period]} 동안 저장된 연습 기록이 없어요`} description={emptyDescription} />
    </>;
  }
  const comparison = report.comparison;
  const previousLabel = report.period === "7d" ? "이전 7일" : "이전 30일";
  const days = chartDays(report);
  const peak = Math.max(1, ...days.map((day) => day.attempts));
  const coverage = report.activeExercises ? (report.practicedActiveExercises / report.activeExercises) * 100 : 0;
  return <>
    <p className="text-xs text-[var(--ink-500)]">{range} · 녹음해서 저장한 연습만 세요</p>
    <CardGrid columns={3} gap="md">
      <Card padding="none" className="px-4 py-3.5">
        <Stat label="연습 횟수" value={report.totalAttempts} unit="회"
          caption={comparison ? `${previousLabel}보다 ${changeText(comparison.totalAttemptsChange, "회")}` : `숙제 연습 ${report.homeworkAttempts}회 포함`} />
      </Card>
      <Card padding="none" className="px-4 py-3.5">
        <Stat label="연습한 날" value={report.practiceDays} unit="일"
          caption={comparison ? `${previousLabel}보다 ${changeText(comparison.practiceDaysChange, "일")}` : undefined} />
      </Card>
      <Card padding="none" className="px-4 py-3.5">
        <Stat label="연습한 콘텐츠" value={report.practicedExercises} unit="개"
          caption={comparison ? `${previousLabel}보다 ${changeText(comparison.practicedExercisesChange, "개")}` : `반복 연습 ${report.retriedExercises}개`} />
      </Card>
    </CardGrid>
    {report.period !== "all" && !comparison && <p className="text-xs text-[var(--ink-500)]">{previousLabel}에 저장된 기록이 없어 비교하지 않아요.</p>}

    <Card as="section" padding="lg" aria-labelledby="report-coverage-title" className="space-y-2">
      <h3 id="report-coverage-title" className="text-[15px] font-bold text-[var(--ink-900)]">전체 콘텐츠 진행 현황</h3>
      <p className="text-sm text-[var(--ink-700)]">전체 연습 콘텐츠 {report.activeExercises.toLocaleString()}개 중 <b>{report.practicedActiveExercises}개</b>를 연습했어요.</p>
      <ProgressBar value={coverage} label={`전체 연습 콘텐츠 ${report.activeExercises}개 중 ${report.practicedActiveExercises}개 연습`} />
    </Card>

    <CardGrid gap="md">
      <Card as="section" padding="lg" aria-labelledby="report-trend-title" className="space-y-3">
        <h3 id="report-trend-title" className="text-[15px] font-bold text-[var(--ink-900)]">
          {report.period === "all" ? "최근 30일 연습 횟수" : `${PERIOD_LABELS[report.period]} 연습 횟수`}
        </h3>
        <ol className="flex h-28 items-end gap-0.5" aria-label="날짜별 연습 횟수">
          {days.map((day) => (
            <li key={day.key} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end" aria-label={`${day.label} ${day.attempts}회`}>
              <span className="w-full rounded-t-[4px] bg-[var(--meadow-400)]" style={{ height: `${(day.attempts / peak) * 100}%`, minHeight: day.attempts ? 4 : 0 }} aria-hidden="true" />
            </li>
          ))}
        </ol>
        <div className="flex justify-between text-[11px] text-[var(--ink-500)]" aria-hidden="true"><span>{days[0]?.label}</span><span>{days[days.length - 1]?.label}</span></div>
      </Card>

      <Card as="section" padding="lg" aria-labelledby="report-area-title" className="space-y-3">
        <h3 id="report-area-title" className="text-[15px] font-bold text-[var(--ink-900)]">영역별 학습량</h3>
        <ul className="space-y-3">
          {report.categories.map((area) => (
            <li key={area.categoryId} className="space-y-1">
              <div className="flex flex-wrap items-baseline justify-between gap-x-2 text-sm">
                <span className="font-semibold text-[var(--ink-800)]">{area.name}</span>
                <span className="text-xs text-[var(--ink-500)]">{area.attempts === 0 ? "이 기간에 연습하지 않았어요" : `연습 ${area.attempts}회 · 콘텐츠 ${area.practicedExercises} / ${area.totalExercises}개`}</span>
              </div>
              <ProgressBar value={area.totalExercises ? (area.practicedExercises / area.totalExercises) * 100 : 0}
                label={`${area.name} 연습한 콘텐츠 ${area.practicedExercises} / ${area.totalExercises}개`} />
            </li>
          ))}
        </ul>
      </Card>
    </CardGrid>

    <CardGrid gap="md">
      <Card as="section" padding="none" aria-labelledby="report-repeated-title" className="overflow-hidden">
        <h3 id="report-repeated-title" className="px-4 pt-4 pb-2 text-[15px] font-bold text-[var(--ink-900)]">반복 연습한 콘텐츠</h3>
        {report.repeated.length === 0
          ? <p className="px-4 pb-4 text-xs text-[var(--ink-500)]">이 기간에 같은 문항을 다시 녹음하거나 다른 날 다시 연습한 콘텐츠가 없어요.</p>
          : <ul className="divide-y divide-[var(--ink-100)]">
              {report.repeated.map((item) => (
                <li key={item.exerciseId} className="flex min-h-14 items-center gap-3 px-4 py-2.5">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-[var(--ink-900)]">{item.title}</span>
                    <span className="block text-xs text-[var(--ink-500)]">{item.categoryName} · 마지막 {item.lastPracticedOn}</span>
                  </span>
                  <span className="shrink-0 text-xs font-semibold text-[var(--ink-700)]">{item.attempts}회 · {item.days}일</span>
                </li>
              ))}
            </ul>}
      </Card>

      <Card as="section" padding="none" aria-labelledby="report-recent-title" className="overflow-hidden">
        <h3 id="report-recent-title" className="px-4 pt-4 pb-2 text-[15px] font-bold text-[var(--ink-900)]">최근 학습 기록</h3>
        <ul className="divide-y divide-[var(--ink-100)]">
          {report.recentAttempts.map((item) => (
            <li key={item.attemptId} className="flex min-h-14 items-center gap-3 px-4 py-2.5">
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold text-[var(--ink-900)]">{item.title}</span>
                <span className="block text-xs text-[var(--ink-500)]">{item.categoryName} · {item.practicedAt}</span>
              </span>
              <span className="shrink-0 text-xs text-[var(--ink-600)]">{attemptTypeLabel[item.attemptType] ?? "연습"}</span>
            </li>
          ))}
        </ul>
      </Card>
    </CardGrid>
    <p className="text-xs leading-relaxed text-[var(--ink-500)]">
      녹음해서 저장한 문항만 세고, 녹음하지 않고 넘긴 문항은 세지 않아요. 반복 연습은 같은 문항을 다시 녹음했거나 다른 날 다시 연습한 콘텐츠예요.
      발음 점수·정확도는 계산하지 않으며, 연습하지 않은 영역도 부족하다는 뜻은 아니에요.
    </p>
  </>;
}
