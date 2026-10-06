import type { ReactNode } from 'react'

type StatProps = {
  label: ReactNode
  value: ReactNode
  /** 값 뒤에 작게 붙는 단위·분모(예: %, /10, 명) */
  unit?: ReactNode
  /** 값 아래 보조 설명(예: 연습 24회) */
  caption?: ReactNode
  /** 값 색. neutral이 기본이며, 측정값을 점수처럼 보이게 하지 않도록 강조색은 상태(경고 등)에만 쓴다 */
  tone?: 'neutral' | 'primary' | 'danger'
  size?: 'md' | 'lg'
  children?: ReactNode
  className?: string
}

const toneClassName = { neutral: 'text-[var(--ink-900)]', primary: 'text-[var(--meadow-700)]', danger: 'text-[var(--coral-600)]' }

/**
 * 라벨 · 큰 숫자 · 보조 설명으로 된 통계 칸(학생 홈, 선생님 오늘 현황, 연습 기록 요약 등).
 * 라벨(p)과 값이 같은 부모 안에 있어 화면 읽기 순서가 '라벨 → 값'이 된다.
 */
export default function Stat({ label, value, unit, caption, tone = 'neutral', size = 'md', children, className = '' }: StatProps) {
  return (
    <div className={className}>
      <p className="text-xs font-medium text-[var(--ink-600)]">{label}</p>
      <p className={`mt-1 font-number font-black leading-none ${size === 'lg' ? 'text-[34px]' : 'text-[28px]'} ${toneClassName[tone]}`}>
        {value}{unit && <span className="ml-0.5 text-base font-extrabold text-[var(--ink-400)]">{unit}</span>}
      </p>
      {caption && <p className="mt-1.5 text-[11px] text-[var(--ink-500)]">{caption}</p>}
      {children}
    </div>
  )
}
