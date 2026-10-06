import type { HTMLAttributes, ReactNode } from 'react'

/**
 * default: 흰 카드 · muted: 바탕보다 살짝 짙은 보조 영역 · dashed: 빈 상태
 * raised: 학생 화면의 말랑한 강조 카드(바닥 그림자) · butter/sky/meadow: 색이 있는 안내 카드
 */
export type CardTone = 'default' | 'muted' | 'dashed' | 'raised' | 'butter' | 'sky' | 'meadow'
type CardPadding = 'none' | 'sm' | 'md' | 'lg'

type CardStyleOptions = { tone?: CardTone; padding?: CardPadding; interactive?: boolean; className?: string }

type CardProps = HTMLAttributes<HTMLElement> & CardStyleOptions & {
  as?: 'div' | 'section' | 'article' | 'li'
  children: ReactNode
}

const toneClassName: Record<CardTone, string> = {
  default: 'border border-[var(--line-soft)] bg-white shadow-[var(--shadow-card)]',
  muted: 'border border-transparent bg-[var(--surface-sunken)]',
  dashed: 'border border-dashed border-[var(--line-control)] bg-white/70',
  raised: 'border-[1.5px] border-[var(--line-puffy)] bg-white shadow-[var(--shadow-puffy)]',
  butter: 'border border-transparent bg-[var(--butter-50)]',
  sky: 'border border-transparent bg-[var(--sky-50)]',
  meadow: 'border border-transparent bg-[var(--meadow-50)]',
}
const paddingClassName: Record<CardPadding, string> = { none: '', sm: 'p-3.5', md: 'p-4', lg: 'p-5' }

/** 버튼처럼 클릭 가능한 카드 등 다른 요소에 같은 카드 스타일을 적용할 때 사용한다. */
export function cardClassName({ tone = 'default', padding = 'md', interactive = false, className = '' }: CardStyleOptions = {}) {
  const interaction = interactive
    ? 'w-full text-left transition-[box-shadow,border-color,transform] duration-150 hover:border-[var(--meadow-200)] hover:shadow-[var(--shadow-card-lg)] active:translate-y-px'
    : ''
  return `rounded-[var(--radius-card)] ${toneClassName[tone]} ${paddingClassName[padding]} ${interaction} ${className}`.replace(/\s+/g, ' ').trim()
}

export default function Card({ as: Tag = 'div', tone, padding, interactive, className, children, ...props }: CardProps) {
  return <Tag className={cardClassName({ tone, padding, interactive, className })} {...props}>{children}</Tag>
}
