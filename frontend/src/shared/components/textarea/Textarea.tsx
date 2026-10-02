import { forwardRef, useId } from 'react'
import type { TextareaHTMLAttributes } from 'react'
import FormField, { controlClassName, type ControlSize } from '@/shared/components/formField/FormField'

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string
  hideLabel?: boolean
  hint?: string
  error?: string
  size?: ControlSize
  fieldClassName?: string
}

const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, hideLabel, hint, error, size = 'md', fieldClassName, className = '', id, required, rows = 3, ...props },
  ref,
) {
  const generatedId = useId()
  const textareaId = id ?? generatedId
  const hintId = hint ? `${textareaId}-hint` : undefined
  const errorId = error ? `${textareaId}-error` : undefined
  return (
    <FormField label={label} htmlFor={textareaId} hideLabel={hideLabel} required={required} hint={hint} hintId={hintId}
      error={error} errorId={errorId} className={fieldClassName}>
      <textarea ref={ref} id={textareaId} rows={rows} required={required} aria-invalid={Boolean(error) || undefined}
        aria-describedby={errorId ?? hintId} className={controlClassName(size, Boolean(error), `resize-none ${className}`)} {...props} />
    </FormField>
  )
})

export default Textarea
