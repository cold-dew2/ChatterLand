import type { ReactNode } from 'react'

type NoticeTone = 'error' | 'warning' | 'info' | 'success' | 'neutral'

const toneClassName: Record<NoticeTone, string> = {
  error: 'bg-[var(--coral-100)] text-[var(--coral-700)]',
  warning: 'bg-[var(--butter-100)] text-[var(--butter-800)]',
  info: 'bg-[var(--sky-50)] text-[var(--sky-800)]',
  success: 'bg-[var(--meadow-100)] text-[var(--meadow-900)]',
  neutral: 'bg-[var(--ink-100)] text-[var(--ink-700)]',
}

/** 폼 오류 · 저장 결과 · 안내 문구를 같은 모양으로 표시한다. 오류는 스크린리더에 즉시 읽힌다. */
export default function Notice({ tone = 'info', className = '', children }: { tone?: NoticeTone; className?: string; children: ReactNode }) {
  return (
    <p role={tone === 'error' ? 'alert' : 'status'} className={`rounded-[var(--radius-lg)] px-3.5 py-2.5 text-[13px] leading-relaxed ${toneClassName[tone]} ${className}`.trim()}>
      {children}
    </p>
  )
}
