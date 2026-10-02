import type { ReactNode } from 'react'
import Button from '@/shared/components/button/Button'
import Modal from '@/shared/components/modal/Modal'

type ConfirmDialogProps = {
  title: string
  description: string
  confirmLabel?: string
  pending?: boolean
  children?: ReactNode
  onCancel: () => void
  onConfirm: () => void | Promise<void>
}

export default function ConfirmDialog({
  title, description, confirmLabel = '삭제', pending = false, children, onCancel, onConfirm,
}: ConfirmDialogProps) {
  return (
    <Modal variant="dialog" role="alertdialog" title={title} description={description} onClose={onCancel} closeDisabled={pending}
      footer={<div className="flex gap-2">
        <Button variant="neutral" fullWidth disabled={pending} onClick={onCancel}>취소</Button>
        <Button variant="danger" fullWidth loading={pending} loadingLabel="처리 중…" onClick={() => void onConfirm()}>{confirmLabel}</Button>
      </div>}>
      {children}
    </Modal>
  )
}
