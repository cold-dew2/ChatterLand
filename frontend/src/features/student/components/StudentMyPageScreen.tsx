"use client";

import { useEffect, useState } from "react";
import { Bell, MessageSquare, User } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
import ChangePasswordForm from "@/features/auth/components/ChangePasswordForm";
import ConsentManager from "@/features/consent/components/ConsentManager";
import type { StudentSummary } from "@/features/student/types";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";

export default function StudentMyPageScreen({ onLogout, student, loggingOut }: { onLogout: () => void; student: StudentSummary; loggingOut: boolean }) {
  const [accountName, setAccountName] = useState(student.name);
  const [accountEmail, setAccountEmail] = useState("");
  useEffect(() => {
    authApi.me().then((user) => { setAccountName(user.name); setAccountEmail(user.email); }).catch(() => undefined);
  }, []);
  return (
    <div className="px-5 py-6 space-y-4">
      <PageTitle title="마이페이지" />

      <Card padding="lg" className="flex items-center gap-4">
        <div className="w-14 h-14 rounded-full bg-gray-100 flex items-center justify-center" aria-hidden="true">
          <User size={26} className="text-gray-400" />
        </div>
        <div>
          <p className="text-base font-bold text-gray-900">{accountName}</p>
          <p className="text-sm text-gray-400">{student.grade}</p>
          {accountEmail && <p className="text-xs text-gray-400 mt-0.5">{accountEmail}</p>}
        </div>
      </Card>

      <Card padding="none" className="overflow-hidden">
        {[
          { label: "알림 설정", icon: Bell },
          { label: "언어설정", icon: MessageSquare },
        ].map((item, i) => (
          <div key={item.label}>
            {i > 0 && <div className="h-px bg-gray-100 mx-4" />}
            {/* 아직 연결된 화면이 없어 '준비 중'으로 표시한다. */}
            <div className="w-full px-5 py-4 flex items-center justify-between" aria-disabled="true">
              <div className="flex items-center gap-3">
                <item.icon size={17} className="text-gray-400" aria-hidden="true" />
                <span className="text-sm font-medium text-gray-700">{item.label}</span>
              </div>
              <Badge tone="neutral">준비 중</Badge>
            </div>
          </div>
        ))}
      </Card>

      <ConsentManager />

      <Card padding="lg" className="space-y-3">
        <h2 className="text-sm font-bold text-gray-800">비밀번호 변경</h2>
        <ChangePasswordForm />
      </Card>

      <Button variant="line" fullWidth loading={loggingOut} loadingLabel="로그아웃 중…" onClick={onLogout} className="py-3.5 rounded-2xl">
        로그아웃
      </Button>
    </div>
  );
}
