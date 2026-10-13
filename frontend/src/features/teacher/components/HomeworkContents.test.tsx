import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const practiceContents = vi.fn()
vi.mock('@/features/teacher/api/teacherApi', () => ({ teacherApi: { practiceContents: (...a: unknown[]) => practiceContents(...a) } }))

import HomeworkModal from '@/features/teacher/components/HomeworkModal'
import type { Student } from '@/features/teacher/types'
import { localDateInput } from '@/features/teacher/utils/mappers'

const students = [{ id: 7, name: '김학생' }] as Student[]
const row = (id: number) => ({ exerciseId: id, title: `콘텐츠 ${id}`, categoryName: '어휘력', instruction: '', inputType: 'mic', items: [{ itemId: id, word: `낱말${id}` }] })
const TOTAL = 25

beforeEach(() => {
  practiceContents.mockReset()
  practiceContents.mockImplementation((params: { page: number; size: number; categoryId?: string }) => {
    const all = Array.from({ length: TOTAL }, (_, i) => row(i + 1))
    return Promise.resolve({ content: all.slice(params.page * params.size, params.page * params.size + params.size), totalElements: TOTAL })
  })
})

describe('숙제 콘텐츠 여러 개 고르기', () => {
  it('여러 쪽에서 고른 콘텐츠를 순서대로 보여 주고, 뺄 수 있고, 저장할 때 모두 보낸다', async () => {
    const onAssign = vi.fn().mockResolvedValue(true)
    render(<HomeworkModal students={students} preStudentId={7} onClose={vi.fn()} onAssign={onAssign} onUpdate={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: '콘텐츠 2 선택' }))
    fireEvent.click(screen.getByRole('button', { name: '2쪽' }))
    fireEvent.click(await screen.findByRole('button', { name: '콘텐츠 15 선택' }))
    fireEvent.click(screen.getByRole('button', { name: '3쪽' }))
    fireEvent.click(await screen.findByRole('button', { name: '콘텐츠 25 선택' }))
    expect(screen.getByText('선택한 연습 3개')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '콘텐츠 15 빼기' }))
    expect(screen.getByText('선택한 연습 2개')).toBeTruthy()
    fireEvent.click(screen.getByRole('tab', { name: '어휘력' }))
    await waitFor(() => expect(practiceContents).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 'vocabulary', page: 0 })))
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '숙제 등록' })) })
    expect(onAssign).toHaveBeenCalledWith(expect.objectContaining({ title: '콘텐츠 2 외 1개', exerciseId: 2, exerciseIds: [2, 25] }))
  })

  it('숙제 수정: 기존 콘텐츠를 체크된 상태로 보여 주고, 기존 콘텐츠를 빼면 같은 창에서 확인한 뒤 바꾼 목록을 보낸다', async () => {
    const onUpdate = vi.fn().mockResolvedValue(true)
    const due = localDateInput(new Date(Date.now() + 3 * 86400_000))
    render(<HomeworkModal students={students} preStudentId={7} onClose={vi.fn()} onAssign={vi.fn()} onUpdate={onUpdate}
      initialHomework={{ id: 3, studentId: 7, title: '어휘 숙제', type: '말하기 연습', dueDate: due, done: false, description: '', targetMinutes: 10, version: 1,
        exercises: [{ id: '1', label: '콘텐츠 1' }, { id: '2', label: '콘텐츠 2' }] }} />)
    expect(screen.getByText('연습 콘텐츠 2개')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '콘텐츠 바꾸기' }))
    expect((await screen.findByRole('button', { name: '콘텐츠 1 선택' })).getAttribute('aria-pressed')).toBe('true')
    fireEvent.click(screen.getByRole('button', { name: '콘텐츠 1 선택' }))
    fireEvent.click(screen.getByRole('button', { name: '콘텐츠 3 선택' }))
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '수정 저장' })) })
    expect(onUpdate).not.toHaveBeenCalled()
    expect(screen.getByRole('alertdialog', { name: '숙제 콘텐츠 빼기 확인' }).textContent).toContain('콘텐츠 1')
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '빼고 저장' })) })
    expect(onUpdate).toHaveBeenCalledWith(3, expect.objectContaining({ exerciseIds: [2, 3] }))
  })

  it('콘텐츠를 바꾸지 않은 수정은 콘텐츠 목록을 보내지 않는다(기존 계약 그대로)', async () => {
    const onUpdate = vi.fn().mockResolvedValue(true)
    const due = localDateInput(new Date(Date.now() + 3 * 86400_000))
    render(<HomeworkModal students={students} preStudentId={7} onClose={vi.fn()} onAssign={vi.fn()} onUpdate={onUpdate}
      initialHomework={{ id: 3, studentId: 7, title: '어휘 숙제', type: '말하기 연습', dueDate: due, done: false, description: '', targetMinutes: 10, version: 1,
        exercises: [{ id: '1', label: '콘텐츠 1' }] }} />)
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '수정 저장' })) })
    expect(onUpdate.mock.calls[0][1]).not.toHaveProperty('exerciseIds')
  })
})
