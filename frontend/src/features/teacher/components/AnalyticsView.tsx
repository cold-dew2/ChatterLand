"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { teacherApi } from "@/features/teacher/api/teacherApi";
import PentagonRadar from "@/features/teacher/components/PentagonRadar";
import type { Student } from "@/features/teacher/types";
import { numberOrNull } from "@/features/teacher/utils/display";
import { localDateInput } from "@/features/teacher/utils/mappers";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import ProgressBar from "@/shared/components/progressBar/ProgressBar";
import Select from "@/shared/components/select/Select";
import Tabs from "@/shared/components/tabs/Tabs";

export type AnalyticsTab = "종합 분석" | "영역별 분석" | "상세 리포트";
const ANALYTICS_TABS: AnalyticsTab[] = ["종합 분석", "영역별 분석", "상세 리포트"];

export default function AnalyticsView({ students, onBack, initialStudentId, initialTab = "종합 분석" }: { students: Student[]; onBack: () => void; initialStudentId?: number | null; initialTab?: AnalyticsTab }) {
  const router = useRouter();
  const [selectedId, setSelectedId] = useState<number | null>(initialStudentId ?? students[0]?.id ?? null);
  const [startDate, setStartDate] = useState(() => { const date = new Date(); date.setDate(date.getDate() - 30); return localDateInput(date); });
  const [endDate, setEndDate] = useState(() => localDateInput(new Date()));
  const [activeTab, setActiveTab] = useState<AnalyticsTab>(initialTab);
  const [analysis, setAnalysis] = useState<Record<string, unknown> | null>(null);
  const [reportData, setReportData] = useState<Record<string, unknown> | null>(null);
  const [analysisError, setAnalysisError] = useState("");
  const [reportError, setReportError] = useState("");
  const [analyticsLoading, setAnalyticsLoading] = useState(true);
  const [analyticsRevision, setAnalyticsRevision] = useState(0);
  const effectiveId = selectedId ?? students[0]?.id ?? null;

  useEffect(() => {
    if (effectiveId === null) return;
    let active = true;
    teacherApi.analytics(effectiveId, startDate, endDate).then((result) => {
      if (!active) return;
      setAnalysis(result);
      setAnalysisError("");
    }).catch((error: unknown) => { if (active) setAnalysisError(errorMessage(error, "분석 자료를 불러오지 못했어요.")); })
      .finally(() => { if (active) setAnalyticsLoading(false); });
    teacherApi.report(effectiveId, startDate, endDate).then((result) => {
      if (active) { setReportData(result); setReportError(""); }
    }).catch((error: unknown) => {
      if (active) { setReportData(null); setReportError(errorMessage(error, "상세 리포트를 불러오지 못했어요.")); }
    });
    return () => { active = false; };
  }, [effectiveId, startDate, endDate, analyticsRevision]);

  const refreshAnalytics = (update: () => void) => {
    setAnalyticsLoading(true); setAnalysis(null); setReportData(null); setAnalysisError(""); setReportError(""); update();
  };
  const retryAnalytics = () => refreshAnalytics(() => setAnalyticsRevision((value) => value + 1));

  if (students.length === 0 || effectiveId === null) {
    return <div><PageHeader title="상세 분석 및 리포트" onBack={onBack} /><div className="px-5 py-6"><EmptyState title="분석할 담당 학생이 없어요" description="제자를 먼저 등록해 주세요." /></div></div>;
  }

  const student = students.find((s) => s.id === effectiveId) ?? students[0];
  const areaRows = Array.isArray(analysis?.areaScores) ? analysis.areaScores as Record<string, unknown>[] : [];
  const areas = areaRows.map((row) => ({ metric: String(row.label ?? "영역"), matchRate: numberOrNull(row.matchRate), score: numberOrNull(row.score), attempts: Number(row.attempts ?? 0) }));
  const radar = areas.filter((area) => area.matchRate !== null).map((area) => ({ metric: area.metric, value: area.matchRate as number }));
  const trendRows = Array.isArray(analysis?.scoreTrend) ? analysis.scoreTrend as Record<string, unknown>[] : [];
  const trend = trendRows.map((row) => ({ date: String(row.date ?? "").slice(5), matchRate: numberOrNull(row.matchRate), attempts: Number(row.attempts ?? 0) })).filter((row) => row.matchRate !== null);
  const totalAttempts = Number(analysis?.totalAttempts ?? 0);
  const averageMatchRate = numberOrNull(analysis?.averageMatchRate);
  const scoredAttempts = Number(analysis?.scoredAttempts ?? 0);
  const externalScore = scoredAttempts > 0 ? numberOrNull(analysis?.overallScore) : null;
  const pendingReviews = Number(analysis?.pendingReviews ?? 0);
  const homeworkTotal = Number(analysis?.homeworkTotal ?? 0);
  const homeworkCompleted = Number(analysis?.homeworkCompleted ?? 0);
  const previousHomeworkTotal = Number((analysis?.previousPeriod as Record<string, unknown> | undefined)?.homeworkTotal ?? 0);
  const previousHomeworkCompleted = Number((analysis?.previousPeriod as Record<string, unknown> | undefined)?.homeworkCompleted ?? 0);
  const homeworkRate = homeworkTotal > 0 ? Math.round((homeworkCompleted / homeworkTotal) * 100) : null;
  const previousHomeworkRate = previousHomeworkTotal > 0 ? Math.round((previousHomeworkCompleted / previousHomeworkTotal) * 100) : null;
  const previousPeriod = analysis?.previousPeriod as Record<string, unknown> | undefined;
  const previousRate = numberOrNull(previousPeriod?.averageMatchRate);
  const rateChange = averageMatchRate !== null && previousRate !== null ? averageMatchRate - previousRate : null;
  const period = `${startDate} ~ ${endDate}`;

  return (
    <div className="flex flex-col min-h-screen">
      <PageHeader title="상세 분석 및 리포트" onBack={onBack} className="print:hidden" />

      <div className="px-4 py-3 space-y-2 border-b border-gray-100 shrink-0 print:hidden">
        <Select label="학생 선택" hideLabel size="sm" value={String(effectiveId)} options={students.map((s) => ({ value: String(s.id), label: s.name }))}
          onChange={(e) => refreshAnalytics(() => setSelectedId(Number(e.target.value)))} />
        <div className="flex items-end gap-2">
          <Input label="시작일" hideLabel size="sm" type="date" value={startDate} max={endDate} fieldClassName="flex-1" onChange={(event) => refreshAnalytics(() => setStartDate(event.target.value))} />
          <span className="pb-2.5 text-gray-300" aria-hidden="true">~</span>
          <Input label="종료일" hideLabel size="sm" type="date" value={endDate} min={startDate} fieldClassName="flex-1" onChange={(event) => refreshAnalytics(() => setEndDate(event.target.value))} />
        </div>
      </div>

      <Tabs ariaLabel="분석 탭" variant="underline" value={activeTab} onChange={setActiveTab} className="shrink-0 print:hidden"
        items={ANALYTICS_TABS.map((tab) => ({ value: tab, label: tab }))} />

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4 [scrollbar-width:none]">
        {analyticsLoading ? <LoadingState label="학습 분석을 불러오고 있어요…" /> : analysisError ? <ErrorState message={analysisError} onRetry={retryAnalytics} /> : <>
          {totalAttempts === 0 && <Notice tone="info">선택한 기간에 저장된 연습 기록이 없어요. 기록이 없으면 통계를 만들지 않아요.</Notice>}

          {activeTab === "종합 분석" && (<>
            <Card padding="none" className="overflow-hidden">
              <div className="flex items-center">
                <div className="flex-1 p-4 border-r border-gray-100">
                  <p className="text-xs text-gray-400 mb-1">평균 문장 일치도</p>
                  <p className="text-5xl font-black text-gray-900 leading-none">{averageMatchRate ?? "N/A"}{averageMatchRate !== null && <span className="ml-1 text-base font-normal text-gray-400">%</span>}</p>
                  <p className="text-xs mt-2 text-gray-400">조회 기간 기록 {totalAttempts}건</p>
                  {rateChange !== null && <p className={`mt-1 text-xs font-semibold ${rateChange > 0 ? "text-green-600" : rateChange < 0 ? "text-red-500" : "text-gray-400"}`}>
                    이전 기간 대비 {rateChange > 0 ? "+" : ""}{rateChange}%p
                  </p>}
                  {pendingReviews > 0 && <div className="mt-2"><Badge tone="warning">음성 검토 대기 {pendingReviews}건</Badge></div>}
                </div>
                <div className="flex items-center justify-center p-2">
                  {radar.length >= 3 ? <PentagonRadar data={radar} /> : <p className="w-32 text-center text-xs text-gray-400">영역 3개 이상 기록이 쌓이면 그래프를 보여줘요.</p>}
                </div>
              </div>
            </Card>

            <Card className="space-y-2">
              <div className="flex items-center justify-between">
                <p className="text-sm font-bold text-gray-700">숙제 수행 현황</p>
                <span className="text-xs text-gray-400">마감일 기준</span>
              </div>
              {homeworkRate === null ? <p className="py-2 text-xs text-gray-400">선택 기간에 마감인 숙제가 없어요.</p> : <>
                <div className="flex items-center gap-3">
                  <ProgressBar value={homeworkRate} label={`숙제 완료율 ${homeworkRate}%`} size="md" color="var(--brand-success)" />
                  <span className="w-24 shrink-0 text-right text-sm font-bold text-gray-700">{homeworkCompleted}/{homeworkTotal}건 · {homeworkRate}%</span>
                </div>
                {previousHomeworkRate !== null && <p className="text-xs text-gray-500">이전 기간 완료율 {previousHomeworkRate}% ({previousHomeworkCompleted}/{previousHomeworkTotal}건)</p>}
              </>}
            </Card>

            <Card>
              <p className="text-sm font-bold text-gray-700 mb-3">문장 일치도 추이</p>
              {trend.length ? <ResponsiveContainer width="100%" height={110}>
                <LineChart data={trend}>
                  <XAxis dataKey="date" tick={{ fontSize: 10, fill: "#9CA3AF" }} axisLine={false} tickLine={false} />
                  <YAxis domain={[0, 100]} ticks={[0, 20, 40, 60, 80, 100]} tick={{ fontSize: 9, fill: "#d1d5db" }} axisLine={false} tickLine={false} width={24} />
                  <Tooltip contentStyle={{ fontSize: 11, borderRadius: 8, border: "1px solid #e5e7eb" }} formatter={(v: number) => [`${v}%`, "문장 일치도"]} />
                  <Line dataKey="matchRate" stroke="var(--brand-primary)" strokeWidth={2} dot={{ r: 3, fill: "var(--brand-primary)", strokeWidth: 0 }} activeDot={{ r: 5, fill: "var(--brand-primary)" }} />
                </LineChart>
              </ResponsiveContainer> : <p className="py-6 text-center text-xs text-gray-400">선택 기간에 문장 일치도 기록이 없어요.</p>}
            </Card>
            <p className="text-xs leading-relaxed text-gray-400">문장 일치도는 음성 인식 결과와 목표 문장의 글자 일치 정도이며 발음 정확도 점수가 아니에요. 언어재활 학습자의 녹음은 일치도를 계산하지 않고 선생님 검토로 관리해요.</p>
          </>)}

          {activeTab === "영역별 분석" && (
            <Card className="space-y-3">
              <p className="text-sm font-bold text-gray-700">영역별 문장 일치도</p>
              {areas.map((area) => (
                <div key={area.metric} className="flex items-center gap-3">
                  <span className="text-xs text-gray-500 w-16 shrink-0">{area.metric}</span>
                  {area.matchRate !== null
                    ? <><ProgressBar value={area.matchRate} label={`${area.metric} 문장 일치도 ${area.matchRate}%`} size="md" color="var(--brand-primary)" /><span className="text-sm font-bold text-gray-700 w-10 text-right">{area.matchRate}%</span></>
                    : <span className="flex-1 text-xs text-gray-400">{area.attempts > 0 ? `연습 ${area.attempts}회 · 일치도 미계산` : "기록 없음"}</span>}
                </div>
              ))}
              {areas.length === 0 && <p className="py-6 text-center text-xs text-gray-400">영역별 기록이 없어요.</p>}
            </Card>
          )}

          {activeTab === "상세 리포트" && (
            <Card className="space-y-3">
              <p className="text-sm font-bold text-gray-700">상세 평가 리포트</p>
              <p className="text-sm text-gray-500 leading-relaxed">{student?.name} 학생의 이번 기간({period}) 학습 결과입니다.</p>
              <dl>
                {[
                  { label: "연습 기록", value: `${totalAttempts}건` },
                  { label: "평균 문장 일치도", value: averageMatchRate === null ? "기록 없음" : `${averageMatchRate}%` },
                  { label: "발음 평가 점수", value: externalScore === null ? "미평가" : `${externalScore}점 (외부 제공자)` },
                  { label: "음성 검토 대기", value: `${pendingReviews}건` },
                  { label: "숙제 수행", value: homeworkTotal ? `${homeworkCompleted}/${homeworkTotal}건 완료` : "마감 숙제 없음" },
                  { label: "완료 세션", value: `${analysis?.completedSessions ?? 0}회` },
                  { label: "강점 영역", value: String(reportData?.strength ?? "분석 기록 없음") },
                  { label: "연습 영역", value: String(reportData?.practiceFocus ?? "분석 기록 없음") },
                ].map((row) => (
                  <div key={row.label} className="flex items-center justify-between py-2 border-t border-gray-100">
                    <dt className="text-sm text-gray-400">{row.label}</dt>
                    <dd className="text-sm font-semibold text-gray-800">{row.value}</dd>
                  </div>
                ))}
              </dl>
              <Notice tone="info">조회 기간에 저장된 실제 연습 기록만 표시합니다. 강점/연습 영역은 외부 분석 점수가 있을 때만 계산돼요.</Notice>
              {reportError && <ErrorState message={reportError} onRetry={retryAnalytics} />}
            </Card>
          )}
        </>}
      </div>

      <div className="px-4 pb-6 pt-3 border-t border-gray-100 flex gap-2 shrink-0 print:hidden">
        <Button variant="outline" fullWidth className="rounded-2xl"
          onClick={() => router.push(`/teacher/reports/${student?.id}/download?startDate=${startDate}&endDate=${endDate}`)}>
          리포트 다운로드
        </Button>
        <Button fullWidth onClick={() => window.print()} className="rounded-2xl">인쇄</Button>
      </div>
    </div>
  );
}
