"use client";

import { useEffect, useState } from "react";
import type { ElementType } from "react";
import { BarChart3, Bell, ChevronRight, HeartHandshake, Languages, ShieldCheck, UserRound } from "lucide-react";
import type { NotificationSettings } from "@/features/account/api/accountApi";
import { authApi } from "@/features/auth/api/authApi";
import { LanguageSettingsSheet, NotificationSettingsSheet } from "@/features/student/components/AccountSettingsSheets";
import type { StudentSummary } from "@/features/student/types";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import Mascot from "@/shared/components/mascot/Mascot";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";

function MenuRow({ icon: Icon, label, description, onClick }: { icon: ElementType; label: string; description: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex min-h-16 w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-[var(--ink-25)]">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[var(--meadow-100)] text-[var(--meadow-800)]" aria-hidden="true"><Icon size={17} /></span>
      <span className="min-w-0 flex-1">
        <span className="block text-[15px] font-semibold text-[var(--ink-900)]">{label}</span>
        <span className="block text-xs text-[var(--ink-500)]">{description}</span>
      </span>
      <ChevronRight size={18} className="shrink-0 text-[var(--ink-300)]" aria-hidden="true" />
    </button>
  );
}

/**
 * 학생 마이페이지. 학습 형태(센터·담당 선생님 또는 개인 학습)는 실제 계정 정보만 보여 준다.
 * 내 정보 수정·동의 관리는 각각 별도 화면으로 가고, 알림·언어 설정은 같은 화면 위 시트로 연다.
 * 비밀번호 변경은 내 정보 수정 화면에서 한다.
 */
export default function StudentMyPageScreen({ onLogout, student, loggingOut, onOpenLearningStatus, onOpenProfile, onOpenConsents, onOpenGuardians, onNotificationSettingsSaved }: {
  onLogout: () => void; student: StudentSummary; loggingOut: boolean;
  onOpenLearningStatus?: () => void;
  /** 내 정보 수정 화면으로 */
  onOpenProfile?: () => void;
  /** 동의 관리 화면으로 */
  onOpenConsents?: () => void;
  /** 보호자 연결 화면으로 */
  onOpenGuardians?: () => void;
  onNotificationSettingsSaved?: (settings: NotificationSettings) => void;
}) {
  const [accountName, setAccountName] = useState(student.name);
  const [centerName, setCenterName] = useState("");
  const [sheet, setSheet] = useState<"notifications" | "language" | null>(null);

  useEffect(() => {
    authApi.me().then((user) => { setAccountName(user.name); setCenterName(user.centerName ?? ""); }).catch(() => undefined);
  }, []);

  const teachers = student.teachers ?? [];
  return (
    <div className="space-y-4 px-5 pt-8 pb-6 md:px-8">
      {sheet === "notifications" && <NotificationSettingsSheet onClose={() => setSheet(null)} onSaved={(settings) => onNotificationSettingsSaved?.(settings)} />}
      {sheet === "language" && <LanguageSettingsSheet onClose={() => setSheet(null)} />}
      <PageTitle title="마이페이지" size="lg" />

      <div className="space-y-4 lg:grid lg:grid-cols-2 lg:items-start lg:gap-6 lg:space-y-0">
        <div className="space-y-4">
          <div className="relative flex items-center gap-4 overflow-hidden rounded-[var(--radius-card-lg)] bg-[var(--sky-100)] p-5">
            <span className="absolute -right-4 -bottom-12 h-32 w-32 rounded-full bg-white/40" aria-hidden="true" />
            <Mascot size={64} className="relative" />
            <div className="relative min-w-0">
              <p className="font-display text-[22px] leading-tight text-[var(--ink-900)]">{accountName}</p>
              {teachers.length > 0 ? <>
                {centerName && <p className="truncate text-sm text-[var(--sky-800)]">{centerName}</p>}
                <p className="truncate text-xs text-[var(--ink-700)]">담당 선생님 {teachers.join(", ")}</p>
              </> : <>
                <p className="mt-0.5"><Badge tone="info">개인 학습</Badge></p>
                {centerName && <p className="mt-0.5 truncate text-xs text-[var(--ink-600)]">가입 센터 {centerName}</p>}
              </>}
            </div>
          </div>

          <Card padding="none" className="divide-y divide-[var(--ink-100)] overflow-hidden">
            {onOpenProfile && <MenuRow icon={UserRound} label="내 정보 수정" description="이름·비밀번호를 바꾸고 계정 정보를 확인해요" onClick={onOpenProfile} />}
            {onOpenConsents && <MenuRow icon={ShieldCheck} label="동의 관리" description="개인정보·음성·AI 기능 동의를 확인하고 바꿔요" onClick={onOpenConsents} />}
            {onOpenGuardians && <MenuRow icon={HeartHandshake} label="보호자 연결" description="보호자 계정이 내 학습 기록을 볼 수 있게 연결해요" onClick={onOpenGuardians} />}
            {onOpenLearningStatus && <MenuRow icon={BarChart3} label="학습 상태" description="연습 횟수와 영역별 학습량을 확인해요" onClick={onOpenLearningStatus} />}
            <MenuRow icon={Bell} label="알림 설정" description="받을 숙제 알림을 골라요" onClick={() => setSheet("notifications")} />
            <MenuRow icon={Languages} label="언어 설정" description="화면 언어: 한국어" onClick={() => setSheet("language")} />
          </Card>
        </div>

        <div className="space-y-4">
          <Button variant="line" size="lg" fullWidth loading={loggingOut} loadingLabel="로그아웃 중…" onClick={onLogout}>
            로그아웃
          </Button>
        </div>
      </div>
    </div>
  );
}
