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
    <div className={`fixed inset-0 z-50 flex justify-center ${sheet ? 'items-end' : 'items-center px-5'}`}>
      <div className="absolute inset-0 bg-black/40" aria-hidden="true" onClick={() => { if (!closeDisabled) onClose() }} />
      <section ref={panelRef} tabIndex={-1} role={role} aria-modal="true" aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        className={`relative w-full bg-white shadow-2xl focus:outline-none ${sheet ? 'max-w-md overflow-hidden rounded-t-3xl' : 'max-w-sm rounded-2xl p-5'}`}>
        {sheet ? (
          <header className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
            <h2 id={titleId} className="text-base font-bold text-gray-900">{title}</h2>
            <button type="button" onClick={onClose} disabled={closeDisabled} aria-label="닫기"
              className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 disabled:opacity-40"><X size={18} /></button>
          </header>
        ) : (
          <h2 id={titleId} className="text-base font-bold text-gray-900">{title}</h2>
        )}
        {description && <p id={descriptionId} className={`text-sm leading-relaxed text-gray-500 ${sheet ? 'px-5 pt-4' : 'mt-2'}`}>{description}</p>}
        {children && <div className={sheet ? 'max-h-[70vh] space-y-4 overflow-y-auto px-5 py-5' : ''}>{children}</div>}
        {footer && <footer className={sheet ? 'border-t border-gray-100 px-5 py-4' : 'mt-5'}>{footer}</footer>}
      </section>
    </div>
  )
}
