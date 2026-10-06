import type { ReactNode } from 'react'

type FormFieldProps = {
  label: string
  htmlFor?: string
  hideLabel?: boolean
  required?: boolean
  hint?: string
  hintId?: string
  error?: string
  errorId?: string
  className?: string
  children: ReactNode
}

/** 레이블 · 입력 요소 · 도움말 · 오류 메시지를 같은 간격과 스타일로 묶는다. */
export default function FormField({ label, htmlFor, hideLabel = false, required = false, hint, hintId, error, errorId, className = '', children }: FormFieldProps) {
  return (
    <div className={className}>
      <label htmlFor={htmlFor} className={hideLabel ? 'sr-only' : 'mb-2 block text-[13px] font-semibold text-[var(--ink-900)]'}>
        {label}{required && <span className="ml-0.5 text-[var(--coral-600)]" aria-hidden="true">*</span>}
      </label>
      {children}
      {hint && !error && <p id={hintId} className="mt-1.5 text-xs leading-relaxed text-[var(--ink-500)]">{hint}</p>}
      {error && <p id={errorId} role="alert" className="mt-1.5 text-xs font-medium leading-relaxed text-[var(--coral-600)]">{error}</p>}
    </div>
  )
}

export type ControlSize = 'sm' | 'md'

/** Input · Select · Textarea가 공유하는 입력 요소 스타일 */
export function controlClassName(size: ControlSize, invalid: boolean, extra = '') {
  // 글자는 16px 이상으로 둔다(iOS Safari는 16px보다 작은 입력창을 누르면 화면을 확대한다).
  const sizing = size === 'sm' ? 'min-h-10 rounded-[var(--radius-control-sm)] px-3.5 py-2 text-base' : 'min-h-[54px] rounded-[var(--radius-control)] px-4 py-3 text-base'
  const state = invalid
    ? 'border-[1.5px] border-[var(--coral-600)] focus:shadow-[var(--focus-ring-danger)]'
    : 'border border-[var(--line-control)] focus:border-[1.5px] focus:border-[var(--meadow-700)] focus:shadow-[var(--focus-ring)]'
  return `w-full bg-white text-[var(--ink-900)] placeholder:text-[var(--ink-400)] transition-[border-color,box-shadow] focus:outline-none focus-visible:outline-none disabled:cursor-not-allowed disabled:bg-[var(--ink-50)] disabled:text-[var(--ink-400)] ${sizing} ${state} ${extra}`.replace(/\s+/g, ' ').trim()
}
