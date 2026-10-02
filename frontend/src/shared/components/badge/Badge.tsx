import type { ReactNode } from 'react'

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info'

const toneClassName: Record<BadgeTone, string> = {
  neutral: 'border-gray-200 bg-gray-50 text-gray-500',
  primary: 'border-blue-100 bg-[var(--brand-surface)] text-[var(--brand-primary)]',
  success: 'border-green-100 bg-green-50 text-green-700',
  warning: 'border-amber-100 bg-amber-50 text-amber-700',
  danger: 'border-red-100 bg-red-50 text-red-600',
  info: 'border-blue-100 bg-blue-50 text-blue-600',
}

export default function Badge({ tone = 'neutral', className = '', children }: { tone?: BadgeTone; className?: string; children: ReactNode }) {
  return <span className={`inline-flex shrink-0 items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium ${toneClassName[tone]} ${className}`.trim()}>{children}</span>
}
