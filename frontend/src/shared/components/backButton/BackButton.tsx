import Link from 'next/link'
import { ArrowLeft, ChevronLeft } from 'lucide-react'

type BackButtonProps = {
  label: string
  /** soft: 학생·인증 화면의 둥근 말랑 버튼 · plain: 선생님 화면의 절제된 버튼 */
  tone?: 'soft' | 'plain'
  icon?: 'chevron' | 'arrow'
  className?: string
} & ({ href: string; onClick?: never } | { onClick: () => void; href?: never })

const toneClassName = {
  soft: 'h-11 w-11 rounded-full border-[1.5px] border-[var(--line-puffy)] bg-white text-[var(--ink-800)] shadow-[var(--shadow-puffy)] hover:bg-[var(--ink-25)] active:translate-y-px',
  plain: 'h-11 w-11 rounded-[var(--radius-md)] text-[var(--ink-700)] hover:bg-[var(--ink-100)]',
}

/** 화면 왼쪽 위 뒤로 가기. 링크(href) 또는 버튼(onClick)으로 쓴다. 터치 영역 44px */
export default function BackButton({ label, tone = 'soft', icon = 'chevron', className = '', ...action }: BackButtonProps) {
  const Icon = icon === 'arrow' ? ArrowLeft : ChevronLeft
  const style = `inline-flex shrink-0 items-center justify-center transition-[background-color,transform] ${toneClassName[tone]} ${className}`.trim()
  const glyph = <Icon size={icon === 'arrow' ? 20 : 22} aria-hidden="true" />
  if (action.href !== undefined) return <Link href={action.href} aria-label={label} className={style}>{glyph}</Link>
  return <button type="button" onClick={action.onClick} aria-label={label} className={style}>{glyph}</button>
}
