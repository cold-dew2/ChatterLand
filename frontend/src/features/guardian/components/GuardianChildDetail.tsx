"use client";

import { useEffect, useState } from "react";
import { consentLabels, type ConsentType } from "@/features/consent/consentPolicy";
import { guardianApi, type ChildConsent, type GuardianChild } from "@/features/guardian/api/guardianApi";
import LearningReportView from "@/features/learningReport/components/LearningReportView";
import type { HistoryItem } from "@/features/student/types";
import { mapHistoryItem } from "@/features/student/utils/mappers";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import ConfirmDialog from "@/shared/components/feedback/ConfirmDialog";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

const historyTypeLabel: Record<HistoryItem["type"], string> = { ai: "AI 대화", word: "말하기 연습", hw: "숙제" };

/**
 * 연결된 자녀 한 명(보기 전용): 연결 상태·자녀 계정의 동의 상태·누적 학습 리포트·학습 기록, 연결 해제.
 * 서버가 이 보호자와 ACTIVE로 연결된 자녀인지 매번 확인한다(아니면 404 → 찾을 수 없음).
 */
export default function GuardianChildDetail({ studentId, onBack, onUnlinked }: { studentId: number; onBack: () => void; onUnlinked: () => void }) {
  const [child, setChild] = useState<(GuardianChild & { consents: ChildConsent[] }) | null>(null);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [history, setHistory] = useState<{ items: HistoryItem[]; total: number; page: number } | null>(null);
  const [historyError, setHistoryError] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [unlinking, setUnlinking] = useState(false);
  const [unlinkError, setUnlinkError] = useState("");

  useEffect(() => {
    let active = true;
    guardianApi.child(studentId)
      .then((value) => { if (active) { setChild(value); setError(""); } })
      .catch((cause: unknown) => { if (active) setError(errorMessage(cause, "자녀 정보를 불러오지 못했어요.")); });
    return () => { active = false; };
  }, [studentId, retryKey]);

  // 학습 기록: 자녀를 확인한 뒤 쪽 단위로 이어 붙인다(더 보기·다시 시도는 쪽 번호·시도 횟수만 바꾼다)
  const [historyPage, setHistoryPage] = useState(0);
  const [historyRetry, setHistoryRetry] = useState(0);
  const childId = child?.studentId;
  useEffect(() => {
    if (childId === undefined) return;
    let active = true;
    guardianApi.history(childId, historyPage, 10)
      .then((row) => {
        if (!active) return;
        const items = (Array.isArray(row.content) ? row.content as Record<string, unknown>[] : []).map(mapHistoryItem);
        setHistory((current) => ({ items: historyPage === 0 || !current ? items : [...current.items, ...items], total: Number(row.totalElements ?? 0) || 0, page: historyPage }));
        setHistoryError("");
      })
      .catch((cause: unknown) => { if (active) setHistoryError(errorMessage(cause, "학습 기록을 불러오지 못했어요.")); });
    return () => { active = false; };
  }, [childId, historyPage, historyRetry]);

  const unlink = async () => {
    setUnlinking(true); setUnlinkError("");
    try { await guardianApi.unlink(studentId); onUnlinked(); }
    catch (cause) { setUnlinkError(errorMessage(cause, "연결을 해제하지 못했어요.")); setConfirming(false); }
    finally { setUnlinking(false); }
  };

  return (
    <div className="pb-10">
      <PageHeader tone="bar" title={child ? `${child.name} 학습 리포트` : "자녀 학습 리포트"} onBack={onBack} backLabel="자녀 목록으로" />
      {confirming && child && (
        <ConfirmDialog title="자녀 연결을 해제할까요?" confirmLabel="연결 해제" pending={unlinking}
          description={`해제하면 ${child.name}의 학습 기록을 바로 볼 수 없어요. 다시 보려면 자녀 계정에서 새 연결 코드를 만들어야 해요.`}
          onCancel={() => setConfirming(false)} onConfirm={() => void unlink()} />
      )}
      <div className="space-y-5 px-4 pt-4 sm:px-6">
        {!child && !error && <LoadingState label="자녀 정보를 불러오고 있어요…" />}
        {error && <ErrorState message={error} onRetry={() => { setError(""); setRetryKey((value) => value + 1); }} />}
        {child && <>
          <div className="space-y-4 lg:grid lg:grid-cols-2 lg:items-start lg:gap-5 lg:space-y-0">
            <Card as="section" padding="lg" aria-labelledby="child-info-title" className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <h2 id="child-info-title" className="text-lg font-bold text-[var(--ink-900)]">{child.name}</h2>
                <Badge tone="success">연결됨</Badge>
              </div>
              <dl className="grid grid-cols-[5rem_1fr] gap-y-1.5 text-sm">
                <dt className="text-[var(--ink-500)]">나이</dt><dd className="font-semibold text-[var(--ink-900)]">{child.age ? `${child.age}세` : "-"}</dd>
                <dt className="text-[var(--ink-500)]">센터</dt><dd className="font-semibold text-[var(--ink-900)]">{child.centerName || "-"}</dd>
                <dt className="text-[var(--ink-500)]">연결한 날</dt><dd className="font-semibold text-[var(--ink-900)]">{child.linkedOn}</dd>
              </dl>
              {unlinkError && <Notice tone="error">{unlinkError}</Notice>}
              <Button variant="line" size="sm" onClick={() => setConfirming(true)}>연결 해제</Button>
            </Card>
            <Card as="section" padding="lg" aria-labelledby="child-consent-title" className="space-y-3">
              <h2 id="child-consent-title" className="text-[15px] font-bold text-[var(--ink-900)]">자녀 계정의 동의 상태</h2>
              <ul className="space-y-2">
                {child.consents.map((consent) => (
                  <li key={consent.type} className="flex items-center justify-between gap-2 text-sm">
                    <span className="min-w-0 text-[var(--ink-800)]">{consentLabels[consent.type as ConsentType] ?? consent.type}</span>
                    <Badge tone={consent.agreed ? "success" : "neutral"}>{consent.agreed ? "동의" : "미동의"}</Badge>
                  </li>
                ))}
              </ul>
              <p className="text-xs leading-relaxed text-[var(--ink-500)]">
                보기만 할 수 있어요. 동의를 바꾸려면 자녀 계정의 마이페이지 › 동의 관리에서 해 주세요. 이 화면의 연결은 가입 때 남긴 법정대리인 동의 기록과는 별개예요.
              </p>
            </Card>
          </div>

          <section aria-labelledby="child-report-title" className="space-y-3">
            <h2 id="child-report-title" className="text-base font-bold text-[var(--ink-900)]">누적 학습 리포트</h2>
            <LearningReportView load={(period) => guardianApi.report(studentId, period)} reloadKey={studentId}
              emptyDescription="자녀가 연습을 녹음해 저장하면 여기에 쌓여요." />
          </section>

          <Card as="section" padding="none" aria-labelledby="child-history-title" className="overflow-hidden">
            <h2 id="child-history-title" className="px-4 pt-4 pb-2 text-[15px] font-bold text-[var(--ink-900)]">학습 기록</h2>
            {!history && !historyError && <LoadingState label="학습 기록을 불러오고 있어요…" />}
            {historyError && <div className="px-4 pb-4"><ErrorState message={historyError} onRetry={() => { setHistoryError(""); setHistoryRetry((value) => value + 1); }} /></div>}
            {history && history.items.length === 0 && <p className="px-4 pb-4 text-sm text-[var(--ink-500)]">아직 학습 기록이 없어요.</p>}
            {history && history.items.length > 0 && <>
              <ul className="divide-y divide-[var(--ink-100)]">
                {history.items.map((item) => (
                  <li key={`${item.type}-${item.id}`} className="flex min-h-14 items-center gap-3 px-4 py-2.5">
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold text-[var(--ink-900)]">{item.title}</span>
                      <span className="block text-xs text-[var(--ink-500)]">{historyTypeLabel[item.type]} · {item.date}{item.time ? ` ${item.time}` : ""}</span>
                    </span>
                  </li>
                ))}
              </ul>
              {history.items.length < history.total && (
                <div className="p-4"><Button variant="line" fullWidth onClick={() => setHistoryPage(history.page + 1)}>더 보기 ({history.items.length} / {history.total})</Button></div>
              )}
            </>}
          </Card>
        </>}
      </div>
    </div>
  );
}
