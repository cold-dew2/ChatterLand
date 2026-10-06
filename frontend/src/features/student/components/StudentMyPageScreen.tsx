"use client";

import { useEffect, useState } from "react";
import { Bell, MessageSquare } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
import ChangePasswordForm from "@/features/auth/components/ChangePasswordForm";
import ConsentManager from "@/features/consent/components/ConsentManager";
import type { StudentSummary } from "@/features/student/types";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import Mascot from "@/shared/components/mascot/Mascot";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";

export default function StudentMyPageScreen({ onLogout, student, loggingOut }: { onLogout: () => void; student: StudentSummary; loggingOut: boolean }) {
  const [accountName, setAccountName] = useState(student.name);
  const [accountEmail, setAccountEmail] = useState("");
  useEffect(() => {
    authApi.me().then((user) => { setAccountName(user.name); setAccountEmail(user.email); }).catch(() => undefined);
  }, []);
  return (
    <div className="space-y-4 px-5 pt-8 pb-6">
      <PageTitle title="마이페이지" size="lg" />

      <div className="relative flex items-center gap-4 overflow-hidden rounded-[var(--radius-card-lg)] bg-[var(--sky-100)] p-5">
        <span className="absolute -right-4 -bottom-12 h-32 w-32 rounded-full bg-white/40" aria-hidden="true" />
        <Mascot size={64} className="relative" />
        <div className="relative min-w-0">
          <p className="font-display text-[22px] leading-tight text-[var(--ink-900)]">{accountName}</p>
          <p className="text-sm text-[var(--sky-800)]">{student.grade}</p>
          {accountEmail && <p className="mt-0.5 truncate text-xs text-[var(--ink-600)]">{accountEmail}</p>}
        </div>
      </div>

      <Card padding="none" className="overflow-hidden">
        {[
          { label: "알림 설정", icon: Bell },
          { label: "언어설정", icon: MessageSquare },
        ].map((item, i) => (
          <div key={item.label}>
            {i > 0 && <div className="mx-4 h-px bg-[var(--ink-100)]" />}
            {/* 아직 연결된 화면이 없어 '준비 중'으로 표시한다. */}
            <div className="flex min-h-14 w-full items-center justify-between px-4 py-3" aria-disabled="true">
              <div className="flex items-center gap-3">
                <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--ink-100)] text-[var(--ink-500)]" aria-hidden="true"><item.icon size={17} /></span>
                <span className="text-[15px] font-medium text-[var(--ink-700)]">{item.label}</span>
              </div>
              <Badge tone="neutral">준비 중</Badge>
            </div>
          </div>
        ))}
      </Card>

      <ConsentManager />

      <Card padding="lg" className="space-y-4">
        <h2 className="text-base font-bold text-[var(--ink-900)]">비밀번호 변경</h2>
        <ChangePasswordForm />
      </Card>

      <Button variant="line" size="lg" fullWidth loading={loggingOut} loadingLabel="로그아웃 중…" onClick={onLogout}>
        로그아웃
      </Button>
    </div>
  );
}
