import type { ReactNode } from 'react'

export type TabItem<T extends string | number> = { value: T; label: ReactNode }

type TabsProps<T extends string | number> = {
  items: TabItem<T>[]
  value: T
  onChange: (value: T) => void
  ariaLabel: string
  /** pill: 채워진 필터 · outline: 역할 선택(세그먼트) · underline: 화면 탭 · chip: 가로 스크롤 필터 */
  variant?: 'pill' | 'outline' | 'underline' | 'chip'
  className?: string
}

const containerClassName = {
  pill: 'flex gap-1.5',
  outline: 'grid auto-cols-fr grid-flow-col gap-1 rounded-[var(--radius-xl)] bg-[var(--ink-100)] p-1',
  underline: 'flex border-b border-[var(--line-soft)] bg-white',
  chip: 'flex gap-1.5 overflow-x-auto pb-1 [scrollbar-width:none]',
}

const itemClassName = {
  pill: (active: boolean) => `flex min-h-10 flex-1 items-center justify-center rounded-full px-3 text-sm font-semibold transition-colors ${active ? 'bg-[var(--brand-primary)] text-white' : 'border border-[var(--line-soft)] bg-white text-[var(--ink-600)] hover:border-[var(--meadow-200)]'}`,
  outline: (active: boolean) => `flex min-h-11 items-center justify-center gap-1.5 rounded-[var(--radius-md)] text-[15px] transition-all ${active ? 'bg-white font-bold text-[var(--ink-900)] shadow-[var(--shadow-frame-inner)]' : 'font-medium text-[var(--ink-600)] hover:text-[var(--ink-900)]'}`,
  underline: (active: boolean) => `min-h-12 flex-1 border-b-2 px-2 text-sm transition-colors ${active ? 'border-[var(--brand-primary)] font-bold text-[var(--meadow-900)]' : 'border-transparent font-medium text-[var(--ink-500)] hover:text-[var(--ink-800)]'}`,
  // 긴 이름(예: 학생 이름 필터)도 칩 하나가 화면을 넘지 않도록 최대 폭을 두고 말줄임한다. 전체 이름은 title로 보인다.
  chip: (active: boolean) => `max-w-[12rem] min-h-9 shrink-0 truncate rounded-full border px-3.5 py-1.5 text-[13px] transition-colors ${active ? 'border-[var(--ink-950)] bg-[var(--ink-950)] font-semibold text-white' : 'border-[var(--line-soft)] bg-white font-medium text-[var(--ink-700)] hover:border-[var(--ink-300)]'}`,
}

/** 선택형 탭/필터. 키보드 좌우 화살표로 이동할 수 있다. */
export default function Tabs<T extends string | number>({ items, value, onChange, ariaLabel, variant = 'pill', className = '' }: TabsProps<T>) {
  const move = (index: number) => {
    const next = items[(index + items.length) % items.length]
    if (next) onChange(next.value)
  }
  return (
    <div role="tablist" aria-label={ariaLabel} className={`${containerClassName[variant]} ${className}`.trim()}>
      {items.map((item, index) => {
        const active = item.value === value
        return (
          <button key={String(item.value)} type="button" role="tab" aria-selected={active} tabIndex={active ? 0 : -1}
            title={typeof item.label === 'string' ? item.label : undefined}
            onClick={() => onChange(item.value)}
            onKeyDown={(event) => {
              if (event.key === 'ArrowRight') { event.preventDefault(); move(index + 1) }
              if (event.key === 'ArrowLeft') { event.preventDefault(); move(index - 1) }
            }}
            className={itemClassName[variant](active)}>
            {item.label}
          </button>
        )
      })}
    </div>
  )
}
