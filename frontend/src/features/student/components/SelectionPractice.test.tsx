import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/features/student/hooks/useAudioRecorder', () => ({
  useAudioRecorder: () => ({ status: 'idle', recording: null, previewUrl: null, elapsed: 0, error: '', maxSeconds: 30, start: vi.fn(), stop: vi.fn(), cancel: vi.fn(), reset: vi.fn() }),
}))
const practiceContent = vi.fn()
const exercisesApi = vi.fn()
const practicedApi = vi.fn()
vi.mock('@/features/student/api/studentApi', () => ({
  studentApi: {
    practiceContent: (...args: unknown[]) => practiceContent(...args),
    exercises: (...args: unknown[]) => exercisesApi(...args),
    practiced: (...args: unknown[]) => practicedApi(...args),
  },
}))

import PracticeRunScreen from '@/features/student/components/PracticeRunScreen'
import PracticeCategoryScreen from '@/features/student/components/PracticeCategoryScreen'

const category = { id: 'articulation', label: '발음', color: '#3e7a47', desc: '정확한 소리를 연습해요.', exercises: [] }
const content = (id: number, word: string) => ({ exerciseId: id, title: `세트 ${id}`, instruction: '말해 보세요', categoryId: 'articulation', items: [{ itemId: id * 10, word }] })
const handlers = () => ({ onIndexChange: vi.fn(), onExit: vi.fn(), onComplete: vi.fn(), onOpenConsent: vi.fn() })

beforeEach(() => {
  practiceContent.mockReset(); exercisesApi.mockReset(); practicedApi.mockReset()
  practiceContent.mockImplementation((id: number) => Promise.resolve(content(id, `낱말${id}`)))
  exercisesApi.mockImplementation((_: string, page: number) => Promise.resolve({ totalElements: 3, content: page === 0 ? [content(1, '사과'), content(2, '학교'), content(3, '친구')] : [] }))
  practicedApi.mockResolvedValue({ content: [], totalElements: 0 })
})

describe('선택 연습', () => {
  it('고른 순서대로 1 / n을 세고, 영역 전체 연습과 다른 이름으로 표시한다', async () => {
    const selection = [{ id: '7', label: '세트 7' }, { id: '3', label: '세트 3' }]
    const run = handlers()
    render(<PracticeRunScreen category={category} mode="selected" index={0} selection={selection} {...run} />)
    expect(await screen.findByText('낱말7')).toBeTruthy()
    expect(screen.getByText('선택한 발음 연습 1 / 2')).toBeTruthy()
    expect(practiceContent).toHaveBeenCalledWith(7)
    expect((screen.getByRole('button', { name: '이전 문제' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: '다음 문제' }))
    expect(run.onIndexChange).toHaveBeenCalledWith(1)
  })

  it('숙제 연습은 숙제 목록으로 돌아가는 뒤로 가기를 쓴다', async () => {
    const run = handlers()
    render(<PracticeRunScreen category={{ ...category, id: 'homework', label: 'ㄹ 숙제' }} mode="selected" index={0} selection={[{ id: '5', label: '세트 5' }]} homeworkId={9} {...run} />)
    expect(await screen.findByText('숙제 연습 1 / 1')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '숙제 목록으로 돌아가기' }))
    expect(run.onExit).toHaveBeenCalledOnce()
    expect((screen.getByRole('button', { name: '다음 문제' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('선택하기를 누르면 연습 시작 막대가 보이고, 0개일 때는 연습·해제 버튼이 막혀 있다', async () => {
    const onStart = vi.fn()
    render(<PracticeCategoryScreen category={category} onBack={vi.fn()} onStart={onStart} />)
    await screen.findByText('연습 세트 3개')
    expect(screen.queryByRole('button', { name: '선택한 콘텐츠 연습하기' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '선택하기' }))
    const start = screen.getByRole('button', { name: '선택한 콘텐츠 연습하기' }) as HTMLButtonElement
    expect(start.disabled).toBe(true)
    fireEvent.click(start)
    expect(onStart).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '세트 2 선택' }))
    expect((screen.getByRole('button', { name: '선택한 1개 연습하기' }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('영역 목록에서 고른 콘텐츠만 고른 순서로 연습을 시작한다', async () => {
    const onStart = vi.fn()
    render(<PracticeCategoryScreen category={category} onBack={vi.fn()} onStart={onStart} />)
    await screen.findByText('연습 세트 3개')
    fireEvent.click(screen.getByRole('button', { name: '선택하기' }))
    fireEvent.click(screen.getByRole('button', { name: '세트 3 선택' }))
    fireEvent.click(screen.getByRole('button', { name: '세트 1 선택' }))
    fireEvent.click(screen.getByRole('button', { name: '선택한 2개 연습하기' }))
    expect(onStart).toHaveBeenCalledWith('selected', 0, undefined, { selection: [{ id: '3', label: '세트 3' }, { id: '1', label: '세트 1' }] })
    fireEvent.click(screen.getByRole('tab', { name: '선택 2' }))
    fireEvent.click(screen.getByRole('button', { name: '세트 3 빼기' }))
    expect(screen.getByRole('button', { name: '선택한 1개 연습하기' })).toBeTruthy()
  })
})
