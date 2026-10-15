"use client";

import { useEffect, useState } from "react";
import { studentApi } from "@/features/student/api/studentApi";
import { GUARDIAN_LINK_LIMITS, GUARDIAN_LINK_SCOPE } from "@/features/guardian/utils/linkPolicy";
import { errorMessage } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import ConfirmDialog from "@/shared/components/feedback/ConfirmDialog";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

type Guardian = { guardianId: number; name: string; linkedOn: string };
type LinkCode = { code: string; expiresAt: string; validMinutes: number };

/** "2026-10-13T19:30:00" → "오후 7:30" */
function timeOf(value: string) {
  const [, time = ""] = value.split("T");
  const [hour, minute] = time.split(":").map(Number);
  if (Number.isNaN(hour)) return value;
  return `${hour < 12 ? "오전" : "오후"} ${hour % 12 || 12}:${String(minute).padStart(2, "0")}`;
}

/**
 * 보호자 연결(학생 계정). 연결 코드를 만들어 보호자에게 알려 주면, 보호자가 보호자 계정에서 입력해 연결한다.
 * 연결된 보호자는 내 학습 기록을 볼 수만 있고, 여기에서 언제든 연결을 해제할 수 있다.
 */
export default function StudentGuardianScreen({ onBack }: { onBack: () => void }) {
  const [guardians, setGuardians] = useState<Guardian[] | null>(null);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [code, setCode] = useState<LinkCode | null>(null);
  const [codeError, setCodeError] = useState("");
  const [making, setMaking] = useState(false);
  const [target, setTarget] = useState<Guardian | null>(null);
  const [unlinking, setUnlinking] = useState(false);

  useEffect(() => {
    let active = true;
    studentApi.guardians()
      .then((list) => { if (active) { setGuardians(list); setError(""); } })
      .catch((cause: unknown) => { if (active) setError(errorMessage(cause, "연결된 보호자를 불러오지 못했어요.")); });
    return () => { active = false; };
  }, [retryKey]);

  const makeCode = async () => {
    setMaking(true); setCodeError("");
    try { setCode(await studentApi.createGuardianLinkCode()); }
    catch (cause) { setCodeError(errorMessage(cause, "연결 코드를 만들지 못했어요.")); }
    finally { setMaking(false); }
  };

  const unlink = async () => {
    if (!target) return;
    setUnlinking(true);
    try { await studentApi.unlinkGuardian(target.guardianId); setTarget(null); setRetryKey((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause, "연결을 해제하지 못했어요.")); setTarget(null); }
    finally { setUnlinking(false); }
  };

  return (
    <div className="pb-8">
      <PageHeader title="보호자 연결" subtitle="보호자 계정이 내 학습 기록을 볼 수 있게 연결해요" onBack={onBack} backLabel="마이페이지로 돌아가기" />
      {target && (
        <ConfirmDialog title="보호자 연결을 해제할까요?" confirmLabel="연결 해제" pending={unlinking}
          description={`${target.name}님은 바로 내 학습 기록을 볼 수 없게 돼요.`} onCancel={() => setTarget(null)} onConfirm={() => void unlink()} />
      )}
      <div className="space-y-4 px-5 pt-2">
        <Card as="section" padding="lg" aria-labelledby="link-code-title" className="space-y-3">
          <h3 id="link-code-title" className="text-base font-bold text-[var(--ink-900)]">연결 코드 만들기</h3>
          <p className="text-sm leading-relaxed text-[var(--ink-700)]">코드를 보호자에게 알려 주세요. 보호자가 보호자 계정에서 10분 안에 입력하면 연결돼요. 새 코드를 만들면 이전 코드는 쓸 수 없어요.</p>
          <Notice tone="info">{GUARDIAN_LINK_SCOPE} {GUARDIAN_LINK_LIMITS} 모르는 사람에게는 코드를 알려 주지 마세요.</Notice>
          {code && (
            <div className="rounded-[var(--radius-lg)] bg-[var(--surface-sunken)] px-4 py-3 text-center" aria-live="polite">
              <p className="font-number text-3xl font-black tracking-[0.15em] text-[var(--ink-900)]" aria-label={`연결 코드 ${code.code.split("").join(" ")}`}>{code.code}</p>
              <p className="mt-1 text-xs text-[var(--ink-600)]">{timeOf(code.expiresAt)}까지 한 번만 쓸 수 있어요</p>
            </div>
          )}
          {codeError && <Notice tone="error">{codeError}</Notice>}
          <Button variant={code ? "line" : "primary"} loading={making} loadingLabel="만드는 중…" onClick={() => void makeCode()}>
            {code ? "새 코드 만들기" : "연결 코드 만들기"}
          </Button>
        </Card>

        <Card as="section" padding="lg" aria-labelledby="my-guardians-title" className="space-y-3">
          <h3 id="my-guardians-title" className="text-base font-bold text-[var(--ink-900)]">연결된 보호자</h3>
          {!guardians && !error && <LoadingState label="불러오고 있어요…" />}
          {error && <ErrorState message={error} onRetry={() => { setError(""); setRetryKey((value) => value + 1); }} />}
          {guardians && guardians.length === 0 && <p className="text-sm text-[var(--ink-500)]">아직 연결된 보호자가 없어요.</p>}
          {guardians && guardians.length > 0 && (
            <ul className="divide-y divide-[var(--ink-100)]">
              {guardians.map((guardian) => (
                <li key={guardian.guardianId} className="flex min-h-14 items-center gap-3 py-2">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-semibold text-[var(--ink-900)]">{guardian.name}</span>
                    <span className="block text-xs text-[var(--ink-500)]">{guardian.linkedOn} 연결 · 학습 기록 보기만 가능</span>
                  </span>
                  <Button variant="line" size="sm" onClick={() => setTarget(guardian)} aria-label={`${guardian.name} 연결 해제`}>연결 해제</Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}
