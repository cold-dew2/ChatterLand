"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronRight } from "lucide-react";
import { useLogout } from "@/features/auth/hooks/useLogout";
import { guardianApi, type GuardianChild } from "@/features/guardian/api/guardianApi";
import GuardianChildDetail from "@/features/guardian/components/GuardianChildDetail";
import LinkChildForm from "@/features/guardian/components/LinkChildForm";
import AppTopBar from "@/layouts/components/AppTopBar";
import PageLayout from "@/layouts/components/PageLayout";
import { paths } from "@/routes/path/paths";
import { errorMessage } from "@/shared/api/client";
import Card, { cardClassName } from "@/shared/components/card/Card";
import CardGrid from "@/shared/components/cardGrid/CardGrid";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";

/** 보호자 화면: 연결된 자녀 목록·자녀 연결(코드) / 자녀 한 명의 리포트(studentId가 있을 때). 보기 전용. */
export default function GuardianPage({ studentId }: { studentId?: number }) {
  const router = useRouter();
  const { logout, loggingOut } = useLogout();
  const topBar = <AppTopBar roleLabel="보호자" onLogout={() => void logout()} loggingOut={loggingOut} />;
  return (
    <PageLayout variant="teacher" topBar={topBar}>
      <main className="flex-1">
        {studentId !== undefined
          ? <GuardianChildDetail studentId={studentId} onBack={() => router.push(paths.guardian.home)} onUnlinked={() => router.push(paths.guardian.home)} />
          : <ChildrenHome onOpen={(id) => router.push(`${paths.guardian.child}/${id}`)} />}
      </main>
    </PageLayout>
  );
}

function ChildrenHome({ onOpen }: { onOpen: (studentId: number) => void }) {
  const [children, setChildren] = useState<GuardianChild[] | null>(null);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [linked, setLinked] = useState("");

  useEffect(() => {
    let active = true;
    guardianApi.children()
      .then((list) => { if (active) { setChildren(list); setError(""); } })
      .catch((cause: unknown) => { if (active) setError(errorMessage(cause, "자녀 목록을 불러오지 못했어요.")); });
    return () => { active = false; };
  }, [retryKey]);

  return (
    <div className="space-y-5 px-4 pt-6 pb-10 sm:px-6">
      <PageTitle title="우리 아이 학습" description="연결한 자녀의 연습 기록을 볼 수 있어요. 기록을 바꾸거나 연습을 대신할 수는 없어요." />
      {linked && <Notice tone="success">{linked} 학생과 연결했어요.</Notice>}
      <section aria-labelledby="children-title" className="space-y-3">
        <h2 id="children-title" className="text-base font-bold text-[var(--ink-900)]">연결된 자녀</h2>
        {!children && !error && <LoadingState label="자녀 목록을 불러오고 있어요…" />}
        {error && <ErrorState message={error} onRetry={() => { setError(""); setRetryKey((value) => value + 1); }} />}
        {children && children.length === 0 && <EmptyState title="아직 연결된 자녀가 없어요" description="아래에서 자녀 계정의 연결 코드를 입력해 주세요." />}
        {children && children.length > 0 && (
          <CardGrid as="ul" gap="md" aria-label="연결된 자녀 목록">
            {children.map((child) => (
              <li key={child.studentId}>
                <button type="button" onClick={() => onOpen(child.studentId)} aria-label={`${child.name} 학습 리포트 보기`}
                  className={cardClassName({ interactive: true, className: "flex min-h-[72px] items-center gap-3" })}>
                  <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[var(--sky-100)] text-[15px] font-extrabold text-[var(--ink-900)]" aria-hidden="true">{child.name.slice(0, 1)}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-bold text-[var(--ink-900)]">{child.name}{child.age ? ` (${child.age}세)` : ""}</span>
                    <span className="block truncate text-xs text-[var(--ink-500)]">{child.centerName} · {child.linkedOn} 연결</span>
                  </span>
                  <ChevronRight size={18} className="shrink-0 text-[var(--ink-300)]" aria-hidden="true" />
                </button>
              </li>
            ))}
          </CardGrid>
        )}
      </section>
      <Card as="section" padding="lg" aria-labelledby="link-child-title" className="space-y-3">
        <h2 id="link-child-title" className="text-base font-bold text-[var(--ink-900)]">자녀 연결하기</h2>
        <LinkChildForm onLinked={(child) => { setLinked(child.name); setRetryKey((value) => value + 1); }} />
      </Card>
    </div>
  );
}
