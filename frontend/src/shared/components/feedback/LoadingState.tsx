import Spinner from '@/shared/components/spinner/Spinner'

export default function LoadingState({ label = '불러오는 중이에요…' }: { label?: string }) {
  return (
    <div role="status" aria-live="polite" className="flex items-center justify-center gap-2 py-10 text-sm text-gray-400">
      <Spinner />{label}
    </div>
  )
}
