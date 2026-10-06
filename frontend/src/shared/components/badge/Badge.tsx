import type { ReactNode } from 'react'

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info'

// 상태는 색만으로 구분하지 않고 항상 글자와 함께 표시한다.
const toneClassName: Record<BadgeTone, string> = {
  neutral: 'bg-[var(--ink-100)] text-[var(--ink-700)]',
  primary: 'bg-[var(--meadow-100)] text-[var(--meadow-900)]',
  success: 'bg-[var(--meadow-100)] text-[var(--meadow-800)]',
  warning: 'bg-[var(--butter-100)] text-[var(--butter-800)]',
  danger: 'bg-[var(--coral-100)] text-[var(--coral-700)]',
  info: 'bg-[var(--sky-100)] text-[var(--sky-800)]',
}

export default function Badge({ tone = 'neutral', className = '', children }: { tone?: BadgeTone; className?: string; children: ReactNode }) {
  return <span className={`inline-flex shrink-0 items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-[11px] font-semibold leading-[18px] ${toneClassName[tone]} ${className}`.trim()}>{children}</span>
}
