type SpinnerProps = {
  size?: 'sm' | 'md' | 'lg'
  className?: string
  label?: string
}

const sizeClassName = { sm: 'h-4 w-4', md: 'h-5 w-5', lg: 'h-10 w-10' }

/** 둥근 고리 모양 로딩 표시. 색은 currentColor를 따른다. */
export default function Spinner({ size = 'sm', className = '', label }: SpinnerProps) {
  return (
    <svg className={`animate-spin ${sizeClassName[size]} ${className}`.trim()} viewBox="0 0 24 24" fill="none"
      role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}>
      <circle className="opacity-20" cx="12" cy="12" r="9.5" stroke="currentColor" strokeWidth="3.5" />
      <path d="M12 2.5a9.5 9.5 0 0 1 9.5 9.5" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" />
    </svg>
  )
}
