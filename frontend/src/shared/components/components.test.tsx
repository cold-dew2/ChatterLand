import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import Button from '@/shared/components/button/Button'
import ConfirmDialog from '@/shared/components/feedback/ConfirmDialog'
import ErrorState from '@/shared/components/feedback/ErrorState'
import Input from '@/shared/components/input/Input'
import Modal from '@/shared/components/modal/Modal'
import Select from '@/shared/components/select/Select'
import Tabs from '@/shared/components/tabs/Tabs'

describe('Button', () => {
  it('fires click and blocks duplicate submit while loading', () => {
    const onClick = vi.fn()
    const { rerender } = render(<Button onClick={onClick}>저장</Button>)
    fireEvent.click(screen.getByRole('button', { name: '저장' }))
    expect(onClick).toHaveBeenCalledOnce()
    rerender(<Button onClick={onClick} loading loadingLabel="저장 중…">저장</Button>)
    const busy = screen.getByRole('button', { name: /저장 중/ })
    expect((busy as HTMLButtonElement).disabled).toBe(true)
    expect(busy.getAttribute('aria-busy')).toBe('true')
    fireEvent.click(busy)
    expect(onClick).toHaveBeenCalledOnce()
  })
})

describe('Input / Select', () => {
  it('links label, error message and aria-invalid', () => {
    render(<Input label="이메일" error="올바른 이메일 주소를 입력해 주세요." defaultValue="x" />)
    const input = screen.getByLabelText('이메일')
    expect(input.getAttribute('aria-invalid')).toBe('true')
    expect(input.getAttribute('aria-describedby')).toBeTruthy()
    expect(document.getElementById(input.getAttribute('aria-describedby')!)?.textContent).toContain('올바른 이메일')
  })
  it('renders options with placeholder and reports changes', () => {
    const onChange = vi.fn()
    render(<Select label="학습자 유형" value="" placeholder="선택하세요" onChange={onChange}
      options={[{ value: 'GENERAL', label: '일반' }, { value: 'THERAPY', label: '언어재활' }]} />)
    fireEvent.change(screen.getByLabelText('학습자 유형'), { target: { value: 'THERAPY' } })
    expect(onChange).toHaveBeenCalled()
    expect(screen.getByRole('option', { name: '선택하세요' })).toBeTruthy()
  })
})

describe('Tabs', () => {
  it('supports click and arrow-key navigation', () => {
    const onChange = vi.fn()
    render(<Tabs ariaLabel="역할" value="student" onChange={onChange} items={[{ value: 'student', label: '학생' }, { value: 'teacher', label: '선생님' }]} />)
    const student = screen.getByRole('tab', { name: '학생' })
    expect(student.getAttribute('aria-selected')).toBe('true')
    fireEvent.keyDown(student, { key: 'ArrowRight' })
    expect(onChange).toHaveBeenLastCalledWith('teacher')
    fireEvent.keyDown(student, { key: 'ArrowLeft' })
    expect(onChange).toHaveBeenLastCalledWith('teacher')
    fireEvent.click(screen.getByRole('tab', { name: '선생님' }))
    expect(onChange).toHaveBeenCalledTimes(3)
  })
})

describe('Tabs chip with long labels', () => {
  it('keeps the full label available while the chip is width-limited', () => {
    const long = '가나다라마바사아자차카타파하'.repeat(5)
    render(<Tabs ariaLabel="학생별 숙제 필터" variant="chip" value="all" onChange={vi.fn()} items={[{ value: 'all', label: '전체' }, { value: 's1', label: long }]} />)
    const chip = screen.getByRole('tab', { name: long })
    expect(chip.getAttribute('title')).toBe(long)
    expect(chip.className).toContain('max-w-[12rem]')
    expect(chip.className).toContain('truncate')
  })
})

describe('Modal / ConfirmDialog', () => {
  it('closes with Escape unless closing is disabled', () => {
    const onClose = vi.fn()
    const { rerender } = render(<Modal title="학생 추가" onClose={onClose}>내용</Modal>)
    expect(screen.getByRole('dialog', { name: '학생 추가' }).getAttribute('aria-modal')).toBe('true')
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledOnce()
    rerender(<Modal title="학생 추가" onClose={onClose} closeDisabled>내용</Modal>)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledOnce()
  })
  it('confirms once and disables both buttons while pending', () => {
    const onConfirm = vi.fn(); const onCancel = vi.fn()
    const { rerender } = render(<ConfirmDialog title="삭제할까요?" description="되돌릴 수 없어요." onCancel={onCancel} onConfirm={onConfirm} />)
    fireEvent.click(screen.getByRole('button', { name: '삭제' }))
    expect(onConfirm).toHaveBeenCalledOnce()
    rerender(<ConfirmDialog title="삭제할까요?" description="되돌릴 수 없어요." pending onCancel={onCancel} onConfirm={onConfirm} />)
    expect((screen.getByRole('button', { name: '취소' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: /처리 중/ }) as HTMLButtonElement).disabled).toBe(true)
  })
})

describe('ErrorState', () => {
  it('shows the message as an alert with retry', () => {
    const onRetry = vi.fn()
    render(<ErrorState message="불러오지 못했어요." onRetry={onRetry} />)
    expect(screen.getByRole('alert').textContent).toContain('불러오지 못했어요.')
    fireEvent.click(screen.getByRole('button', { name: /다시/ }))
    expect(onRetry).toHaveBeenCalledOnce()
  })
})
