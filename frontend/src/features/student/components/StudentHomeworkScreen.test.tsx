import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/client'

const homeworks = vi.fn()
const completeHomework = vi.fn()
const practiceContent = vi.fn()
vi.mock('@/features/student/api/studentApi', () => ({
  studentApi: { homeworks: (...a: unknown[]) => homeworks(...a), completeHomework: (...a: unknown[]) => completeHomework(...a),
    practiceContent: (...a: unknown[]) => practiceContent(...a) },
}))

import StudentHomeworkScreen from '@/features/student/components/StudentHomeworkScreen'

const hw = (id: number, title: string, done = false) => ({ homeworkId: id, title, type: '발음', dueDate: '2099-12-31', done, targetMinutes: 10 })
const page = (items: unknown[]) => ({ content: items })
const renderScreen = (onCompleteHomework = vi.fn(), onStartPractice = vi.fn()) => render(<StudentHomeworkScreen onBack={vi.fn()} onStartPractice={onStartPractice} onCompleteHomework={onCompleteHomework} />)

beforeEach(() => { homeworks.mockReset(); completeHomework.mockReset(); practiceContent.mockReset() })

describe('StudentHomeworkScreen', () => {
  it('shows the empty state when there is no homework', async () => {
    homeworks.mockResolvedValue(page([]))
    renderScreen()
    expect(await screen.findByText('등록된 숙제가 없어요')).toBeTruthy()
  })

  it('separates the all-completed state from having no homework', async () => {
    homeworks.mockResolvedValue(page([hw(1, '끝낸 숙제', true)]))
    renderScreen()
    expect(await screen.findByText('남은 숙제가 없어요')).toBeTruthy()
    expect(screen.queryByText('등록된 숙제가 없어요')).toBeNull()
    expect(screen.getByText('끝낸 숙제')).toBeTruthy()
  })

  it('completes a homework once, moves it to the completed list and notifies the parent', async () => {
    homeworks.mockResolvedValue(page([hw(1, '남은 숙제'), hw(2, '끝낸 숙제', true)]))
    let finish: () => void = () => undefined
    completeHomework.mockReturnValue(new Promise<void>((resolve) => { finish = resolve }))
    const onComplete = vi.fn()
    renderScreen(onComplete)
    const button = await screen.findByRole('button', { name: '완료' })
    fireEvent.click(button); fireEvent.click(button)
    expect(completeHomework).toHaveBeenCalledTimes(1)
    finish()
    expect(await screen.findByText('남은 숙제가 없어요')).toBeTruthy()
    expect(onComplete).toHaveBeenCalledWith(1)
  })

  it('reloads the list when the homework was deleted by the teacher (404)', async () => {
    homeworks.mockResolvedValueOnce(page([hw(1, '삭제될 숙제')])).mockResolvedValueOnce(page([]))
    completeHomework.mockRejectedValue(new ApiError('숙제를 찾을 수 없습니다.', 404))
    renderScreen()
    fireEvent.click(await screen.findByRole('button', { name: '완료' }))
    expect(await screen.findByText('선생님이 삭제한 숙제예요. 목록을 새로 불러왔어요.')).toBeTruthy()
    expect(await screen.findByText('등록된 숙제가 없어요')).toBeTruthy()
    expect(homeworks).toHaveBeenCalledTimes(2)
  })

  it('keeps the item and shows the server message on other failures', async () => {
    homeworks.mockResolvedValue(page([hw(1, '남은 숙제')]))
    completeHomework.mockRejectedValue(new ApiError('서버 내부 오류가 발생했습니다.', 500))
    renderScreen()
    fireEvent.click(await screen.findByRole('button', { name: '완료' }))
    expect(await screen.findByText('서버 내부 오류가 발생했습니다.')).toBeTruthy()
    expect((screen.getByRole('button', { name: '완료' }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('shows an error with a retry that really refetches', async () => {
    homeworks.mockRejectedValueOnce(new ApiError('서버에 연결할 수 없어요.', 0, 'NETWORK_ERROR')).mockResolvedValueOnce(page([hw(1, '다시 불러온 숙제')]))
    renderScreen()
    expect((await screen.findByRole('alert')).textContent).toContain('서버에 연결할 수 없어요.')
    fireEvent.click(screen.getByRole('button', { name: /다시/ }))
    await waitFor(() => expect(homeworks).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('다시 불러온 숙제')).toBeTruthy()
  })

  it('starts the assigned practice set as homework, or the practice menu for a free homework', async () => {
    homeworks.mockResolvedValue(page([{ ...hw(1, '연음 숙제'), exerciseId: 1205, exerciseTitle: '연음 낱말 1', attemptCount: 2 }, hw(2, '자유 숙제')]))
    practiceContent.mockResolvedValue({ exerciseId: 1205, title: '연음 낱말 1', instruction: '따라 말해요', inputType: 'mic', items: [{ itemId: 1, word: '옷이' }] })
    const onStart = vi.fn()
    renderScreen(vi.fn(), onStart)
    expect(await screen.findByText(/연음 낱말 1/)).toBeTruthy()
    expect(screen.getByText(/2번 연습함/)).toBeTruthy()
    const buttons = screen.getAllByRole('button', { name: /연습하러 가기/ })
    fireEvent.click(buttons[0])
    await waitFor(() => expect(onStart).toHaveBeenCalledWith(expect.objectContaining({ id: 1, exerciseId: 1205 }), expect.objectContaining({ id: '1205', label: '연음 낱말 1' })))
    fireEvent.click(buttons[1])
    expect(onStart).toHaveBeenLastCalledWith(expect.objectContaining({ id: 2 }))
    expect(practiceContent).toHaveBeenCalledTimes(1)
  })
})

