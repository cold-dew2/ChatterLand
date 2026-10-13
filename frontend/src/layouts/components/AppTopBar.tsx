"use client"

import { useState } from 'react'
import { KeyRound, LogOut } from 'lucide-react'
import ChangePasswordForm from '@/features/auth/components/ChangePasswordForm'
import Badge from '@/shared/components/badge/Badge'
import Button from '@/shared/components/button/Button'
import Modal from '@/shared/components/modal/Modal'

/** 선생님·보호자 화면 상단 막대(로고·역할·비밀번호 변경·로그아웃). PageLayout의 topBar로 넣는다. */
export default function AppTopBar({ roleLabel, onLogout, loggingOut }: { roleLabel: string; onLogout: () => void; loggingOut: boolean }) {
  const [showPassword, setShowPassword] = useState(false)
  return (
    <>
      {showPassword && <Modal title="비밀번호 변경" onClose={() => setShowPassword(false)}><ChangePasswordForm /></Modal>}
      <header className="flex items-center justify-between px-4 py-2.5 sm:px-6">
        <div className="flex items-center gap-2">
          <span className="h-6 w-6 rounded-full bg-[var(--brand-primary)]" aria-hidden="true" />
          <p className="text-base font-extrabold text-[var(--ink-900)]">체터랜드</p>
          <Badge tone="primary" className="rounded-md">{roleLabel}</Badge>
        </div>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="icon" onClick={() => setShowPassword(true)} aria-label="비밀번호 변경">
            <KeyRound size={18} />
          </Button>
          <Button variant="ghost" size="icon" onClick={onLogout} disabled={loggingOut} aria-label="로그아웃">
            <LogOut size={18} />
          </Button>
        </div>
      </header>
    </>
  )
}
