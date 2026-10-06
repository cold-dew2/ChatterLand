import type { ElementType, ReactNode } from 'react'
import { ChevronRight } from 'lucide-react'
import { cardClassName } from '@/shared/components/card/Card'

type MenuCardProps = {
  icon: ElementType
  /** 아이콘 색. 바탕은 같은 색을 옅게 섞어 파스텔로 만든다(서버가 준 영역 색도 그대로 받는다). */
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
      className={cardClassName({ tone: muted ? 'muted' : 'default', interactive: !muted, className: `flex min-h-[76px] items-center gap-4 ${muted ? 'w-full text-left' : ''}` })}>
      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full"
        style={{ backgroundColor: `color-mix(in srgb, ${color} 20%, white)`, color: `color-mix(in srgb, ${color} 70%, var(--ink-950))` }}>
        <Icon size={22} aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className={`block text-base font-bold ${muted ? 'text-[var(--ink-600)]' : 'text-[var(--ink-900)]'}`}>{title}</span>
        <span className="block break-keep text-[13px] text-[var(--ink-500)]">{description}</span>
      </span>
      {!muted && <ChevronRight size={18} className="shrink-0 text-[var(--ink-300)]" aria-hidden="true" />}
    </button>
  )
}
