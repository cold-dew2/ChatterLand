import { forwardRef, useId } from 'react'
import type { ReactNode, SelectHTMLAttributes } from 'react'
import { ChevronDown } from 'lucide-react'
import FormField, { controlClassName, type ControlSize } from '@/shared/components/formField/FormField'

export type SelectOption = { value: string | number; label: string; disabled?: boolean }

type SelectProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> & {
  label: string
  hideLabel?: boolean
  hint?: string
  error?: string
  size?: ControlSize
  options?: SelectOption[]
  placeholder?: string
  fieldClassName?: string
  children?: ReactNode
}

const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, hideLabel, hint, error, size = 'md', options, placeholder, fieldClassName, className = '', id, required, value, children, ...props },
  ref,
) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const hintId = hint ? `${selectId}-hint` : undefined
  const errorId = error ? `${selectId}-error` : undefined
  const empty = value === '' || value === undefined
  return (
    <FormField label={label} htmlFor={selectId} hideLabel={hideLabel} required={required} hint={hint} hintId={hintId}
      error={error} errorId={errorId} className={fieldClassName}>
      <div className="relative">
        <select ref={ref} id={selectId} required={required} value={value} aria-invalid={Boolean(error) || undefined}
          aria-describedby={errorId ?? hintId}
          className={controlClassName(size, Boolean(error), `cursor-pointer appearance-none pr-10 ${placeholder && empty ? 'text-[var(--ink-400)]' : ''} ${className}`)} {...props}>
          {placeholder && <option value="" disabled>{placeholder}</option>}
          {options?.map((option) => <option key={option.value} value={option.value} disabled={option.disabled}>{option.label}</option>)}
          {children}
        </select>
        <ChevronDown size={16} aria-hidden="true" className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-[var(--ink-500)]" />
      </div>
    </FormField>
  )
})

export default Select
