import { ChevronLeft, ChevronRight } from 'lucide-react'

type PaginationProps = {
  /** 현재 쪽(0부터) */
  page: number
  totalPages: number
  onChange: (page: number) => void
  ariaLabel: string
  className?: string
}

/** 보여 줄 쪽 번호: 처음·끝과 현재 쪽 주변만 보이고 나머지는 줄임표 */
export function visiblePages(page: number, totalPages: number): (number | 'gap')[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i)
  const pages = new Set([0, totalPages - 1, page - 1, page, page + 1].filter((p) => p >= 0 && p < totalPages))
  const sorted = [...pages].sort((a, b) => a - b)
  const out: (number | 'gap')[] = []
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) out.push('gap')
    out.push(p)
  })
  return out
}

const cell = 'flex h-10 min-w-10 items-center justify-center rounded-[var(--radius-md)] px-2 text-sm font-semibold transition-colors'

/** 쪽 이동. 긴 목록에서 '더 보기'를 여러 번 누르지 않고 원하는 쪽(맨 끝 포함)으로 바로 간다. */
export default function Pagination({ page, totalPages, onChange, ariaLabel, className = '' }: PaginationProps) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label={ariaLabel} className={`flex flex-wrap items-center justify-center gap-1 ${className}`.trim()}>
      <button type="button" onClick={() => onChange(page - 1)} disabled={page <= 0} aria-label="이전 쪽"
        className={`${cell} text-[var(--ink-600)] hover:enabled:bg-[var(--ink-100)] disabled:opacity-40`}><ChevronLeft size={18} aria-hidden="true" /></button>
      {visiblePages(page, totalPages).map((p, i) => p === 'gap'
        ? <span key={`gap-${i}`} className="px-1 text-[var(--ink-400)]" aria-hidden="true">…</span>
        : <button type="button" key={p} onClick={() => onChange(p)} aria-label={`${p + 1}쪽`} aria-current={p === page ? 'page' : undefined}
            className={`${cell} ${p === page ? 'bg-[var(--brand-primary)] text-white' : 'text-[var(--ink-700)] hover:bg-[var(--ink-100)]'}`}>{p + 1}</button>)}
      <button type="button" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1} aria-label="다음 쪽"
        className={`${cell} text-[var(--ink-600)] hover:enabled:bg-[var(--ink-100)] disabled:opacity-40`}><ChevronRight size={18} aria-hidden="true" /></button>
    </nav>
  )
}
