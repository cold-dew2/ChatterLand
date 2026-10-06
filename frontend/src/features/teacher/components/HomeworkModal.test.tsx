import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
const practiceContents = vi.fn()
vi.mock('@/features/teacher/api/teacherApi', () => ({ teacherApi: { practiceContents: (...a: unknown[]) => practiceContents(...a) } }))

import HomeworkModal from '@/features/teacher/components/HomeworkModal'
import type { Student } from '@/features/teacher/types'
import { localDateInput } from '@/features/teacher/utils/mappers'

const students = [{ id: 7, name: '김학생' }, { id: 8, name: '이학생' }] as Student[]
const setup = (props: Partial<Parameters<typeof HomeworkModal>[0]> = {}) => {
  const onAssign = vi.fn().mockResolvedValue(true)
  const onUpdate = vi.fn().mockResolvedValue(true)
  const onClose = vi.fn()
  const view = render(<HomeworkModal students={students} preStudentId={7} onClose={onClose} onAssign={onAssign} onUpdate={onUpdate} {...props} />)
  return { onAssign, onUpdate, onClose, ...view }
}

describe('HomeworkModal (교사 숙제 배정)', () => {
  it('미리 선택한 학생으로 배정하고 저장되면 닫는다', async () => {
    const { onAssign, onClose } = setup()
    fireEvent.change(screen.getByLabelText(/숙제 내용/), { target: { value: 'ㄹ 말 5번 연습' } })
    fireEvent.click(screen.getByRole('radio', { name: '조음 말하기' }))
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '숙제 등록' })) })
    expect(onAssign).toHaveBeenCalledWith(expect.objectContaining({ studentId: 7, title: 'ㄹ 말 5번 연습', type: '조음 말하기', targetMinutes: 10 }))
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('필수값·범위를 검증하고 API를 호출하지 않는다', async () => {
    const { onAssign } = setup({ preStudentId: 999 })
    expect((screen.getByLabelText(/제자 선택/) as HTMLSelectElement).value).toBe('')
    fireEvent.change(screen.getByLabelText(/목표 시간/), { target: { value: '0' } })
    fireEvent.change(screen.getByLabelText(/마감일/), { target: { value: '2000-01-01' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '숙제 등록' })) })
    expect(screen.getByText('숙제를 배정할 학생을 선택해 주세요.')).toBeTruthy()
    expect(screen.getByText('목표 시간은 1~120분 사이로 입력해 주세요.')).toBeTruthy()
    expect(screen.getByText('마감일은 오늘 이후로 설정해 주세요.')).toBeTruthy()
    expect(onAssign).not.toHaveBeenCalled()
  })

  it('저장 중에는 중복 제출을 막고, 실패하면 모달을 닫지 않고 서버 오류를 보여준다', async () => {
    let finish: (value: boolean) => void = () => undefined
    const onAssign = vi.fn().mockReturnValue(new Promise<boolean>((resolve) => { finish = resolve }))
    const onClose = vi.fn()
    const { rerender } = render(<HomeworkModal students={students} preStudentId={7} onClose={onClose} onAssign={onAssign} onUpdate={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: '숙제 등록' }))
    fireEvent.click(screen.getByRole('button', { name: /저장 중/ }))
    expect(onAssign).toHaveBeenCalledOnce()
    expect((screen.getByRole('button', { name: '취소' }) as HTMLButtonElement).disabled).toBe(true)
    await act(async () => finish(false))
    rerender(<HomeworkModal students={students} preStudentId={7} error="담당 학생 정보에 접근할 수 없습니다." onClose={onClose} onAssign={onAssign} onUpdate={vi.fn()} />)
    expect(screen.getByText('담당 학생 정보에 접근할 수 없습니다.')).toBeTruthy()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('취소 버튼과 Escape로 닫고, 학생이 없으면 등록할 수 없다', () => {
    const { onClose, unmount } = setup()
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(2)
    unmount()
    render(<HomeworkModal students={[]} preStudentId={0} onClose={vi.fn()} onAssign={vi.fn()} onUpdate={vi.fn()} />)
    expect(screen.getByText('먼저 학생을 등록해 주세요.')).toBeTruthy()
    expect((screen.getByRole('button', { name: '숙제 등록' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('수정 모드에서는 학생을 바꿀 수 없고 변경 내용만 보낸다', async () => {
    const due = localDateInput(new Date(Date.now() + 3 * 86400_000))
    const { onUpdate } = setup({ initialHomework: { id: 3, studentId: 8, title: '기존 숙제', type: '단어 말하기', dueDate: due, done: false, targetMinutes: 15, description: '' } as never })
    expect((screen.getByLabelText(/제자 선택/) as HTMLSelectElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/숙제 내용/), { target: { value: '수정된 숙제' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '수정 저장' })) })
    expect(onUpdate).toHaveBeenCalledWith(3, expect.objectContaining({ title: '수정된 숙제', type: '단어 말하기', targetMinutes: 15, dueDate: due }))
  })

  it('연습 콘텐츠를 골라 숙제를 만들면 콘텐츠 ID를 함께 보내고, 제목을 비우면 콘텐츠 이름을 쓴다', async () => {
    practiceContents.mockResolvedValue({ content: [{ exerciseId: 1205, title: '연음 낱말 1', instruction: '따라 말해요', inputType: 'mic', pronunciationRule: 'LIAISON', difficulty: 'INTERMEDIATE',
      items: [{ itemId: 1, word: '옷이' }, { itemId: 2, word: '꽃을' }] }], totalElements: 1 })
    const { onAssign } = setup()
    fireEvent.change(screen.getByLabelText('콘텐츠 검색'), { target: { value: '옷' } })
    fireEvent.click(screen.getByRole('button', { name: '콘텐츠 찾기' }))
    fireEvent.click(await screen.findByRole('button', { name: '연음 낱말 1 선택' }))
    expect(screen.getByText('옷이 · 꽃을')).toBeTruthy()
    expect(practiceContents).toHaveBeenCalledWith(expect.objectContaining({ keyword: '옷' }))
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '숙제 등록' })) })
    expect(onAssign).toHaveBeenCalledWith(expect.objectContaining({ studentId: 7, title: '연음 낱말 1', exerciseId: 1205 }))
  })

  it('수정할 때는 콘텐츠를 바꿀 수 없고 지정된 콘텐츠만 보여 준다', () => {
    setup({ initialHomework: { id: 3, studentId: 7, title: '받침 숙제', type: '말하기 연습', dueDate: localDateInput(new Date()), done: false, description: '', targetMinutes: 10, version: 0,
      exerciseId: 1205, exerciseTitle: '연음 낱말 1' } })
    expect(screen.getByText('연음 낱말 1')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '콘텐츠 찾기' })).toBeNull()
  })
})

