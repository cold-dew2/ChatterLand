type SpinnerProps = {
  size?: 'sm' | 'md' | 'lg'
  className?: string
  label?: string
}

const sizeClassName = { sm: 'h-4 w-4', md: 'h-6 w-6', lg: 'h-10 w-10' }

export default function Spinner({ size = 'sm', className = '', label }: SpinnerProps) {
  return (
    <svg className={`animate-spin ${sizeClassName[size]} ${className}`.trim()} viewBox="0 0 24 24" fill="none"
      role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}>
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
    </svg>
  )
}
