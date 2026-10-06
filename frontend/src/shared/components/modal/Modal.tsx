"use client"

import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

type ModalProps = {
  title: string
  description?: string
  onClose: () => void
  children?: ReactNode
  footer?: ReactNode
  variant?: 'sheet' | 'dialog'
  role?: 'dialog' | 'alertdialog'
  /** 저장 중처럼 닫으면 안 되는 상태에서 true */
  closeDisabled?: boolean
}

/** 하단 시트(sheet) 또는 가운데 대화상자(dialog). Esc · 배경 클릭으로 닫히고 포커스를 되돌린다. */
export default function Modal({ title, description, onClose, children, footer, variant = 'sheet', role = 'dialog', closeDisabled = false }: ModalProps) {
  const titleId = useId()
  const descriptionId = useId()
  const panelRef = useRef<HTMLElement>(null)
  const closeRef = useRef(onClose)
  const disabledRef = useRef(closeDisabled)

  useEffect(() => { closeRef.current = onClose; disabledRef.current = closeDisabled }, [onClose, closeDisabled])

  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const firstField = panelRef.current?.querySelector<HTMLElement>('input, select, textarea')
    ;(firstField ?? panelRef.current)?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !disabledRef.current) closeRef.current()
      if (event.key !== 'Tab' || !panelRef.current) return
      const focusable = panelRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')
      if (!focusable.length) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      previousFocus?.focus?.()
    }
  }, [])

  const sheet = variant === 'sheet'
  return (
    <div className={`fixed inset-0 z-50 flex justify-center ${sheet ? 'items-end sm:items-center sm:px-5' : 'items-center px-5'}`}>
      <div className="absolute inset-0 bg-[var(--ink-950)]/45" aria-hidden="true" onClick={() => { if (!closeDisabled) onClose() }} />
      <section ref={panelRef} tabIndex={-1} role={role} aria-modal="true" aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        className={`relative w-full bg-[var(--ink-25)] shadow-[var(--shadow-sheet)] focus:outline-none ${sheet ? 'flex max-h-[92vh] max-w-md flex-col overflow-hidden rounded-t-[var(--radius-sheet)] sm:max-h-[88vh] sm:rounded-[var(--radius-sheet)]' : 'max-w-sm rounded-[var(--radius-card-lg)] p-6'}`}>
        {sheet ? (
          <header className="relative flex shrink-0 items-start justify-between gap-3 px-6 pt-6 pb-1">
            <span className="absolute left-1/2 top-2.5 h-1 w-10 -translate-x-1/2 rounded-full bg-[var(--ink-200)] sm:hidden" aria-hidden="true" />
            <h2 id={titleId} className="font-display text-[22px] leading-snug text-[var(--ink-900)]">{title}</h2>
            <button type="button" onClick={onClose} disabled={closeDisabled} aria-label="닫기"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[var(--ink-100)] text-[var(--ink-700)] hover:bg-[var(--ink-150)] disabled:opacity-40"><X size={18} /></button>
          </header>
        ) : (
          <h2 id={titleId} className="font-display text-xl leading-snug text-[var(--ink-900)]">{title}</h2>
        )}
        {description && <p id={descriptionId} className={`text-sm leading-relaxed text-[var(--ink-600)] ${sheet ? 'shrink-0 px-6' : 'mt-2'}`}>{description}</p>}
        {children && <div className={sheet ? 'min-h-0 flex-1 space-y-4 overflow-y-auto px-6 py-5' : 'mt-4 space-y-3'}>{children}</div>}
        {footer && <footer className={sheet ? 'shrink-0 border-t border-[var(--line-soft)] bg-[var(--ink-25)] px-6 pt-4 pb-6' : 'mt-6'}>{footer}</footer>}
      </section>
    </div>
  )
}
