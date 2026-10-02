import type { ElementType, ReactNode } from 'react'
import { ChevronRight } from 'lucide-react'
import { cardClassName } from '@/shared/components/card/Card'

type MenuCardProps = {
  icon: ElementType
  color: string
  title: ReactNode
  description: ReactNode
  onClick: () => void
  muted?: boolean
}

/** 아이콘 · 제목 · 설명 · 화살표로 구성된 이동용 카드 (학생/선생님 홈, 연습 목록 등) */
export default function MenuCard({ icon: Icon, color, title, description, onClick, muted = false }: MenuCardProps) {
  return (
    <button type="button" onClick={onClick}
      className={cardClassName({ tone: muted ? 'muted' : 'default', interactive: !muted, className: `flex items-center gap-4 ${muted ? 'w-full text-left opacity-60' : ''}` })}>
      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full" style={{ backgroundColor: color }}>
        <Icon size={20} color="white" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className={`block text-base font-bold ${muted ? 'text-gray-600' : 'text-gray-900'}`}>{title}</span>
        <span className="block text-sm text-gray-400">{description}</span>
      </span>
      {!muted && <ChevronRight size={18} className="shrink-0 text-gray-300" aria-hidden="true" />}
    </button>
  )
}
