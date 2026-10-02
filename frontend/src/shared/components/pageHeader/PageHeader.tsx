import type { ReactNode } from 'react'
import { ChevronLeft } from 'lucide-react'

type PageHeaderProps = {
  title: ReactNode
  subtitle?: ReactNode
  onBack?: () => void
  backLabel?: string
  action?: ReactNode
  className?: string
}

/** 뒤로 가기 · 제목 · 보조 설명 · 오른쪽 액션으로 구성된 화면 상단 영역 */
export default function PageHeader({ title, subtitle, onBack, backLabel = '뒤로 가기', action, className = '' }: PageHeaderProps) {
  return (
    <header className={`flex shrink-0 items-center gap-3 border-b border-gray-100 px-4 py-4 ${className}`.trim()}>
      {onBack && (
        <button type="button" onClick={onBack} aria-label={backLabel} className="rounded-xl p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600">
          <ChevronLeft size={20} />
        </button>
      )}
      <div className="min-w-0 flex-1">
        <h2 className="truncate text-base font-bold text-gray-900">{title}</h2>
        {subtitle && <p className="truncate text-xs text-gray-400">{subtitle}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </header>
  )
}

/** 본문 상단의 큰 제목과 설명 */
export function PageTitle({ title, description, size = 'md', className = '' }: { title: ReactNode; description?: ReactNode; size?: 'md' | 'lg'; className?: string }) {
  return (
    <div className={className}>
      <h2 className={`${size === 'lg' ? 'text-2xl' : 'text-xl'} font-bold text-gray-900`}>{title}</h2>
      {description && <p className="mt-1 text-sm text-gray-400">{description}</p>}
    </div>
  )
}
