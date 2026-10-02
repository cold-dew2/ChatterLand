import type { HTMLAttributes, ReactNode } from 'react'

type CardTone = 'default' | 'muted' | 'dashed'
type CardPadding = 'none' | 'sm' | 'md' | 'lg'

type CardStyleOptions = { tone?: CardTone; padding?: CardPadding; interactive?: boolean; className?: string }

type CardProps = HTMLAttributes<HTMLElement> & CardStyleOptions & {
  as?: 'div' | 'section' | 'article' | 'li'
  children: ReactNode
}

const toneClassName: Record<CardTone, string> = {
  default: 'border border-gray-100 bg-white shadow-sm',
  muted: 'border border-gray-100 bg-gray-50',
  dashed: 'border border-dashed border-gray-200 bg-white',
}
const paddingClassName: Record<CardPadding, string> = { none: '', sm: 'p-3', md: 'p-4', lg: 'p-5' }

/** 버튼처럼 클릭 가능한 카드 등 다른 요소에 같은 카드 스타일을 적용할 때 사용한다. */
export function cardClassName({ tone = 'default', padding = 'md', interactive = false, className = '' }: CardStyleOptions = {}) {
  return `rounded-2xl ${toneClassName[tone]} ${paddingClassName[padding]} ${interactive ? 'w-full text-left transition-shadow hover:shadow-md' : ''} ${className}`.replace(/\s+/g, ' ').trim()
}

export default function Card({ as: Tag = 'div', tone, padding, interactive, className, children, ...props }: CardProps) {
  return <Tag className={cardClassName({ tone, padding, interactive, className })} {...props}>{children}</Tag>
}
