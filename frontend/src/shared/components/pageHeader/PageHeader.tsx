import type { ReactNode } from 'react'
import BackButton from '@/shared/components/backButton/BackButton'

type PageHeaderProps = {
  title: ReactNode
  subtitle?: ReactNode
  onBack?: () => void
  backLabel?: string
  action?: ReactNode
  /** soft: 학생 화면(바탕 위 큰 제목) · bar: 선생님 화면(흰 상단 막대) */
  tone?: 'soft' | 'bar'
  className?: string
}

/** 뒤로 가기 · 제목 · 보조 설명 · 오른쪽 액션으로 구성된 화면 상단 영역 */
export default function PageHeader({ title, subtitle, onBack, backLabel = '뒤로 가기', action, tone = 'soft', className = '' }: PageHeaderProps) {
  const bar = tone === 'bar'
  return (
    <header className={`flex shrink-0 items-center gap-3 ${bar ? 'border-b border-[var(--line-soft)] bg-white px-3 py-3 sm:px-5' : 'px-5 pt-6 pb-3'} ${className}`.trim()}>
      {onBack && <BackButton label={backLabel} onClick={onBack} tone={bar ? 'plain' : 'soft'} />}
      <div className="min-w-0 flex-1">
        <h2 className={`truncate ${bar ? 'text-[17px] font-bold' : 'font-display text-lg leading-snug'} text-[var(--ink-900)]`}>{title}</h2>
        {subtitle && <p className="truncate text-xs text-[var(--ink-500)]">{subtitle}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </header>
  )
}

/** 본문 상단의 큰 제목과 설명 */
export function PageTitle({ title, description, size = 'md', className = '' }: { title: ReactNode; description?: ReactNode; size?: 'md' | 'lg'; className?: string }) {
  return (
    <div className={className}>
      <h2 className={`${size === 'lg' ? 'text-[30px]' : 'text-2xl'} font-display leading-tight tracking-[-0.02em] text-[var(--ink-900)]`}>{title}</h2>
      {description && <p className="mt-1.5 text-sm leading-relaxed text-[var(--ink-600)]">{description}</p>}
    </div>
  )
}
