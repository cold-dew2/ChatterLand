type ProgressBarProps = {
  /** 0~100 */
  value: number
  label: string
  size?: 'sm' | 'md'
  color?: string
  className?: string
}

export default function ProgressBar({ value, label, size = 'sm', color = 'var(--brand-primary-hover)', className = '' }: ProgressBarProps) {
  const safe = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0
  return (
    <div role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(safe)}
      className={`w-full overflow-hidden rounded-full bg-gray-100 ${size === 'md' ? 'h-2.5' : 'h-1.5'} ${className}`.trim()}>
      <div className="h-full rounded-full transition-all duration-500" style={{ width: `${safe}%`, backgroundColor: color }} />
    </div>
  )
}
