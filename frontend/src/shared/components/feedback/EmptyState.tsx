import type { ReactNode } from 'react'

type EmptyStateProps = {
  title: string
  description?: string
  variant?: 'card' | 'plain'
  /** 아이콘이나 그림(학생 화면은 마스코트, 선생님 화면은 아이콘) */
  icon?: ReactNode
  action?: ReactNode
}

export default function EmptyState({ title, description, variant = 'card', icon, action }: EmptyStateProps) {
  const className = variant === 'plain'
    ? 'flex flex-col items-center px-4 py-10 text-center'
    : 'flex flex-col items-center rounded-[var(--radius-card)] border border-dashed border-[var(--line-control)] bg-white/70 px-5 py-6 text-center'

  return <div className={className}>
    {icon && <div className="mb-3 flex justify-center">{icon}</div>}
    <p className="text-[15px] font-bold text-[var(--ink-800)]">{title}</p>
    {description && <p className="mt-1 max-w-[18rem] text-[13px] leading-relaxed text-[var(--ink-500)]">{description}</p>}
    {action && <div className="mt-4">{action}</div>}
  </div>
}
