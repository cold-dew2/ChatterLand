import type { ReactNode } from 'react'

type CardGridProps = {
  children: ReactNode
  /** 목록 의미가 있으면 ul/ol을 쓴다(자식은 li) */
  as?: 'div' | 'ul' | 'ol'
  /** 넓은 공간에서 최대 열 수 */
  columns?: 2 | 3
  gap?: 'sm' | 'md' | 'lg'
  className?: string
  'aria-label'?: string
}

const gapClassName = { sm: 'gap-2', md: 'gap-2.5', lg: 'gap-3' } as const
// 화면 폭이 아니라 이 목록이 놓인 공간의 폭으로 열 수를 정한다(모바일·좁은 모달은 한 열, 태블릿·PC는 여러 열).
const columnClassName = { 2: '@2xl:grid-cols-2', 3: '@2xl:grid-cols-2 @4xl:grid-cols-3' } as const

/** 카드 목록 공통 배치. 좁은 곳에서는 기존처럼 한 줄에 하나, 넓은 곳에서는 여러 열로 나란히 놓는다. */
export default function CardGrid({ children, as: Tag = 'div', columns = 2, gap = 'lg', className = '', 'aria-label': ariaLabel }: CardGridProps) {
  return (
    <div className="@container">
      {/* grid-cols-1(minmax(0,1fr))·min-w-0: 긴 제목이 칸을 밀어 화면 밖으로 넘치지 않게 칸 폭을 공간에 맞춘다 */}
      <Tag aria-label={ariaLabel} className={`grid grid-cols-1 items-start ${gapClassName[gap]} ${columnClassName[columns]} [&>*]:min-w-0 ${className}`.trim()}>{children}</Tag>
    </div>
  )
}
