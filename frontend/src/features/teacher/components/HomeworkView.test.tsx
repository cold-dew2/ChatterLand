import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import HomeworkView from '@/features/teacher/components/HomeworkView'
import type { Homework, Student } from '@/features/teacher/types'

const students = [{ id: 1, name: '김학생' }, { id: 2, name: '이학생' }] as Student[]
const homework = { id: 10, studentId: 1, title: 'ㄹ 연습', type: '발음', dueDate: '2099-01-01', done: false, targetMinutes: 10, description: '' } as unknown as Homework
const props = { students, loadState: 'ready' as const, onRetry: vi.fn(), onBack: vi.fn(), onAssignHw: vi.fn(), onEdit: vi.fn(), onToggle: vi.fn(), onDelete: vi.fn() }

describe('HomeworkView empty states', () => {
  it('distinguishes "no homework at all" from "no homework for the selected student"', () => {
    const { unmount } = render(<HomeworkView {...props} homeworks={[]} />)
    expect(screen.getByText('숙제가 없어요')).toBeTruthy()
    expect(screen.getByRole('button', { name: '첫 숙제 등록' })).toBeTruthy()
    unmount()
    render(<HomeworkView {...props} homeworks={[homework]} />)
    fireEvent.click(screen.getByRole('tab', { name: '이학생' }))
    expect(screen.getByText('이 학생에게 배정한 숙제가 없어요')).toBeTruthy()
    expect(screen.getByRole('button', { name: '숙제 등록' })).toBeTruthy()
    fireEvent.click(screen.getByRole('tab', { name: '김학생' }))
    expect(screen.getByText('ㄹ 연습')).toBeTruthy()
  })

  it('shows loading and error-with-retry states', () => {
    const onRetry = vi.fn()
    const { rerender } = render(<HomeworkView {...props} homeworks={[]} loadState="loading" />)
    expect(screen.getByText('숙제를 불러오고 있어요…')).toBeTruthy()
    rerender(<HomeworkView {...props} homeworks={[]} loadState="error" onRetry={onRetry} />)
    fireEvent.click(screen.getByRole('button', { name: /다시/ }))
    expect(onRetry).toHaveBeenCalledOnce()
  })
})
