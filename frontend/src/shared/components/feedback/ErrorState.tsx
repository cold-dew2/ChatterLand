import Button from '@/shared/components/button/Button'

export default function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-2xl border border-red-100 bg-red-50 px-5 py-6 text-center">
      <p className="text-sm font-semibold text-red-700">{message}</p>
      {onRetry && <Button size="sm" variant="line" onClick={onRetry} className="mt-3 border-red-200 text-red-700">다시 시도</Button>}
    </div>
  )
}
