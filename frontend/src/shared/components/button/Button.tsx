import type { ButtonHTMLAttributes, ReactNode } from 'react'
import Spinner from '@/shared/components/spinner/Spinner'

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'danger' | 'dangerLine' | 'neutral' | 'line' | 'ghost'
export type ButtonSize = 'sm' | 'md' | 'lg' | 'icon'

type ButtonStyleOptions = {
  variant?: ButtonVariant
  size?: ButtonSize
  fullWidth?: boolean
  className?: string
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & ButtonStyleOptions & {
  loading?: boolean
  loadingLabel?: string
  children: ReactNode
}

const base = 'inline-flex items-center justify-center gap-2 font-bold leading-tight transition-[background-color,box-shadow,transform,color] duration-150 select-none disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none'

// primary · danger는 바닥 그림자로 눌리는 버튼처럼 보이게 하고, 누르면 그림자만큼 내려간다.
const variantClassName: Record<ButtonVariant, string> = {
  primary: 'bg-[var(--brand-primary)] text-white shadow-[var(--shadow-button)] hover:enabled:bg-[var(--meadow-800)] active:enabled:translate-y-[3px] active:enabled:shadow-[0_1px_0_var(--meadow-800)]',
  secondary: 'bg-[var(--meadow-100)] text-[var(--meadow-900)] hover:enabled:bg-[var(--meadow-200)] active:enabled:translate-y-px',
  outline: 'border-[1.5px] border-[var(--meadow-200)] bg-white text-[var(--meadow-800)] hover:enabled:bg-[var(--meadow-50)] active:enabled:translate-y-px',
  danger: 'bg-[var(--brand-danger)] text-white shadow-[var(--shadow-button-danger)] hover:enabled:bg-[var(--coral-700)] active:enabled:translate-y-[3px] active:enabled:shadow-[0_1px_0_var(--coral-800)]',
  // 되돌리기 어려운 작업을 여는 버튼(확인 대화상자가 따로 뜸): 흰 바탕 + 코랄 테두리로 덜 강하게
  dangerLine: 'border border-[var(--coral-200)] bg-white text-[var(--coral-600)] hover:enabled:bg-[var(--coral-50)] active:enabled:translate-y-px',
  neutral: 'bg-[var(--ink-100)] text-[var(--ink-700)] hover:enabled:bg-[var(--ink-150)] active:enabled:translate-y-px',
  line: 'border border-[var(--line-control)] bg-white text-[var(--ink-700)] hover:enabled:bg-[var(--ink-50)] active:enabled:translate-y-px',
  ghost: 'bg-transparent text-[var(--ink-500)] hover:enabled:bg-[var(--ink-100)] hover:enabled:text-[var(--ink-800)]',
}

// 학생 화면에서도 누르기 쉬운 크기: sm 40px · md 48px · lg 56px · icon 44px
const sizeClassName: Record<ButtonSize, string> = {
  sm: 'min-h-10 rounded-[var(--radius-control-sm)] px-3.5 py-2 text-[13px]',
  md: 'min-h-12 rounded-[var(--radius-button)] px-4 py-3 text-[15px]',
  lg: 'min-h-14 rounded-[var(--radius-button-lg)] px-5 py-3.5 text-base',
  icon: 'h-11 w-11 shrink-0 rounded-[var(--radius-control-sm)] p-2',
}

/** Next.js Link 등 button이 아닌 요소에 같은 버튼 스타일을 입힐 때 사용한다. */
export function buttonClassName({ variant = 'primary', size = 'md', fullWidth = false, className = '' }: ButtonStyleOptions = {}) {
  return `${base} ${variantClassName[variant]} ${sizeClassName[size]} ${fullWidth ? 'w-full' : ''} ${className}`.replace(/\s+/g, ' ').trim()
}

export default function Button({
  variant = 'primary', size = 'md', fullWidth = false, loading = false, loadingLabel,
  className = '', type = 'button', disabled, children, ...props
}: ButtonProps) {
  return (
    <button type={type} disabled={disabled || loading} aria-busy={loading || undefined}
      className={buttonClassName({ variant, size, fullWidth, className })} {...props}>
      {loading ? <><Spinner />{loadingLabel ?? children}</> : children}
    </button>
  )
}
