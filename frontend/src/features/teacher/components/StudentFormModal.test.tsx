import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

const availableStudents = vi.fn()
vi.mock('@/features/teacher/api/teacherApi', () => ({ teacherApi: { availableStudents: () => availableStudents() } }))

import StudentFormModal from '@/features/teacher/components/StudentFormModal'

describe('기존 학생 계정 연결(담당 전)', () => {
  it('shows only name·age·center and never carries contact or memo into the link request', async () => {
    // 예전 서버처럼 개인정보가 섞여 와도 화면·요청에 쓰지 않는다
    availableStudents.mockResolvedValue([{ studentId: 7, name: '김하늘', age: 8, centerName: '해솔 센터', parentPhone: '010-0000-0000', memo: '민감 메모', focusAreas: '조음' }])
    const onSave = vi.fn().mockResolvedValue(true)
    render(<StudentFormModal onClose={vi.fn()} onSave={onSave} />)
    fireEvent.click(screen.getByRole('tab', { name: '기존 계정 연결' }))
    const option = await screen.findByRole('radio', { name: /김하늘 · 8세/ })
    expect(screen.queryByText(/010-0000-0000|민감 메모/)).toBeNull()
    fireEvent.click(option)
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '기존 계정 연결' })) })
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ name: '김하늘', age: 8, parentPhone: '', memo: '', tags: [] }), 7)
  })
})
