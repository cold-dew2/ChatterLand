import { CircleAlert, RotateCcw } from 'lucide-react'
import Button from '@/shared/components/button/Button'

export default function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center rounded-[var(--radius-card)] border border-[var(--coral-200)] bg-white px-5 py-6 text-center">
      <span className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-[var(--coral-100)] text-[var(--coral-600)]" aria-hidden="true"><CircleAlert size={20} /></span>
      <p role="alert" className="text-sm font-semibold leading-relaxed text-[var(--coral-700)]">{message}</p>
      {onRetry && <Button size="sm" variant="line" onClick={onRetry} className="mt-3 border-[var(--coral-200)] text-[var(--coral-700)]"><RotateCcw size={14} aria-hidden="true" />다시 시도</Button>}
    </div>
  )
}
