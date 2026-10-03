import type { ReactNode } from 'react'

export type TabItem<T extends string | number> = { value: T; label: ReactNode }

type TabsProps<T extends string | number> = {
  items: TabItem<T>[]
  value: T
  onChange: (value: T) => void
  ariaLabel: string
  /** pill: 채워진 필터 · outline: 역할 선택 · underline: 화면 탭 · chip: 가로 스크롤 필터 */
  variant?: 'pill' | 'outline' | 'underline' | 'chip'
  className?: string
}

const containerClassName = {
  pill: 'flex gap-1',
  outline: 'flex gap-2',
  underline: 'flex border-b border-gray-100',
  chip: 'flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none]',
}

const itemClassName = {
  pill: (active: boolean) => `flex-1 rounded-xl py-2 text-xs font-semibold transition-all ${active ? 'bg-[var(--brand-primary)] text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`,
  outline: (active: boolean) => `flex flex-1 items-center justify-center gap-1.5 rounded-xl border-2 py-2 text-sm font-semibold transition-all ${active ? 'border-[var(--brand-primary)] bg-blue-50 text-blue-800' : 'border-gray-200 text-gray-400 hover:border-blue-200'}`,
  underline: (active: boolean) => `flex-1 border-b-2 py-3 text-sm font-semibold transition-colors ${active ? 'border-[var(--brand-primary)] text-[var(--brand-primary)]' : 'border-transparent text-gray-400 hover:text-gray-600'}`,
  // 긴 이름(예: 학생 이름 필터)도 칩 하나가 화면을 넘지 않도록 최대 폭을 두고 말줄임한다. 전체 이름은 title로 보인다.
  chip: (active: boolean) => `max-w-[12rem] shrink-0 truncate rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors ${active ? 'border-gray-700 bg-gray-700 text-white' : 'border-gray-200 text-gray-500 hover:border-gray-400'}`,
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
