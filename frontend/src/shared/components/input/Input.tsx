import { forwardRef, useId } from 'react'
import type { InputHTMLAttributes, ReactNode } from 'react'
import FormField, { controlClassName, type ControlSize } from '@/shared/components/formField/FormField'

type InputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> & {
  label: string
  hideLabel?: boolean
  hint?: string
  error?: string
  size?: ControlSize
  endAdornment?: ReactNode
  fieldClassName?: string
}

const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, hideLabel, hint, error, size = 'md', endAdornment, fieldClassName, className = '', id, required, ...props },
  ref,
) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = hint ? `${inputId}-hint` : undefined
  const errorId = error ? `${inputId}-error` : undefined
  return (
    <FormField label={label} htmlFor={inputId} hideLabel={hideLabel} required={required} hint={hint} hintId={hintId}
      error={error} errorId={errorId} className={fieldClassName}>
      <div className={endAdornment ? 'relative' : undefined}>
        <input ref={ref} id={inputId} required={required} aria-invalid={Boolean(error) || undefined}
          aria-describedby={errorId ?? hintId}
          className={controlClassName(size, Boolean(error), `${endAdornment ? 'pr-12' : ''} ${className}`)} {...props} />
        {endAdornment && <span className="absolute right-3.5 top-1/2 flex -translate-y-1/2 items-center">{endAdornment}</span>}
      </div>
    </FormField>
  )
})

export default Input
