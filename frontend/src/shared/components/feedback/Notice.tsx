import type { ReactNode } from 'react'

type NoticeTone = 'error' | 'warning' | 'info' | 'success'

const toneClassName: Record<NoticeTone, string> = {
  error: 'bg-red-50 text-red-700',
  warning: 'bg-amber-50 text-amber-800',
  info: 'bg-gray-50 text-gray-600',
  success: 'bg-green-50 text-green-700',
}

/** 폼 오류 · 저장 결과 · 안내 문구를 같은 모양으로 표시한다. 오류는 스크린리더에 즉시 읽힌다. */
export default function Notice({ tone = 'info', className = '', children }: { tone?: NoticeTone; className?: string; children: ReactNode }) {
  return (
    <p role={tone === 'error' ? 'alert' : 'status'} className={`rounded-xl px-3 py-2 text-xs leading-relaxed ${toneClassName[tone]} ${className}`.trim()}>
      {children}
    </p>
  )
}
