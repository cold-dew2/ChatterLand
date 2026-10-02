import type { ButtonHTMLAttributes, ReactNode } from 'react'
import Spinner from '@/shared/components/spinner/Spinner'

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'danger' | 'neutral' | 'line' | 'ghost'
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

const base = 'inline-flex items-center justify-center gap-2 font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-50 active:translate-y-px'

const variantClassName: Record<ButtonVariant, string> = {
  primary: 'bg-[var(--brand-primary)] text-white hover:enabled:bg-[var(--brand-primary-hover)]',
  secondary: 'bg-[var(--brand-surface)] text-[var(--brand-primary)] hover:enabled:bg-blue-100',
  outline: 'border-2 border-[var(--brand-primary)] bg-white text-[var(--brand-primary)] hover:enabled:bg-blue-50',
  danger: 'bg-[var(--brand-danger)] text-white hover:enabled:bg-red-600',
  neutral: 'bg-gray-100 text-gray-600 hover:enabled:bg-gray-200',
  line: 'border border-gray-200 bg-white text-gray-500 hover:enabled:bg-gray-50',
  ghost: 'bg-transparent text-gray-400 hover:enabled:bg-gray-100 hover:enabled:text-gray-600',
}

const sizeClassName: Record<ButtonSize, string> = {
  sm: 'rounded-xl px-3 py-2 text-xs',
  md: 'rounded-xl px-4 py-3 text-sm',
  lg: 'rounded-2xl px-5 py-4 text-base',
  icon: 'rounded-xl p-2',
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
