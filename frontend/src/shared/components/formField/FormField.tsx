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
      <label htmlFor={htmlFor} className={hideLabel ? 'sr-only' : 'mb-1.5 block text-sm font-semibold text-gray-700'}>
        {label}{required && <span className="ml-0.5 text-red-500" aria-hidden="true">*</span>}
      </label>
      {children}
      {hint && !error && <p id={hintId} className="mt-1.5 text-xs text-gray-400">{hint}</p>}
      {error && <p id={errorId} role="alert" className="mt-1.5 text-xs text-red-600">{error}</p>}
    </div>
  )
}

export type ControlSize = 'sm' | 'md'

/** Input · Select · Textarea가 공유하는 입력 요소 스타일 */
export function controlClassName(size: ControlSize, invalid: boolean, extra = '') {
  const sizing = size === 'sm' ? 'px-3 py-2.5 text-xs' : 'px-4 py-3.5 text-sm'
  const state = invalid
    ? 'border-red-300 focus:border-red-400 focus:ring-red-100'
    : 'border-gray-200 focus:border-[var(--brand-primary)] focus:ring-blue-100'
  return `w-full rounded-xl border bg-white text-gray-900 placeholder:text-gray-300 transition-all focus:outline-none focus:ring-2 disabled:bg-gray-50 disabled:text-gray-400 ${sizing} ${state} ${extra}`.replace(/\s+/g, ' ').trim()
}
