import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/features/student/hooks/useAudioRecorder', () => ({
  useAudioRecorder: () => ({ status: 'idle', recording: null, previewUrl: null, elapsed: 0, error: '', maxSeconds: 30, start: vi.fn(), stop: vi.fn(), cancel: vi.fn(), reset: vi.fn() }),
}))
const exercisesApi = vi.fn()
const practicedApi = vi.fn()
vi.mock('@/features/student/api/studentApi', () => ({
  studentApi: {
    exercises: (...args: unknown[]) => exercisesApi(...args),
    practiced: (...args: unknown[]) => practicedApi(...args),
  },
}))

import PracticeRunScreen from '@/features/student/components/PracticeRunScreen'
import PracticeCategoryScreen from '@/features/student/components/PracticeCategoryScreen'
import { pickRandomOrder, PRACTICE_PAGE_SIZE } from '@/features/student/hooks/usePracticeSequence'

const TOTAL = 45
const category = { id: 'articulation', label: '발음', color: '#3e7a47', desc: '정확한 소리를 연습해요.', exercises: [] }
/** 전체 순서 n번째(0부터) 세트: 제목 '세트 n+1', 문항 하나 */
const row = (n: number, categoryId = 'articulation') => ({ exerciseId: 100 + n, title: `세트 ${n + 1}`, instruction: '말해 보세요', categoryId, items: [{ itemId: 1000 + n, word: `낱말${n + 1}` }] })
const pageOf = (page: number, total = TOTAL) => ({
  content: Array.from({ length: Math.max(0, Math.min(PRACTICE_PAGE_SIZE, total - page * PRACTICE_PAGE_SIZE)) }, (_, i) => row(page * PRACTICE_PAGE_SIZE + i)),
  totalElements: total,
})

beforeEach(() => {
  exercisesApi.mockReset(); practicedApi.mockReset()
  exercisesApi.mockImplementation((_: string, page: number) => Promise.resolve(pageOf(page)))
  practicedApi.mockImplementation(() => Promise.resolve({ content: [row(7), row(2)], totalElements: 2 }))
})

describe('pickRandomOrder', () => {
  it('전체 순서에서 겹치지 않는 위치를 정해진 개수만큼 고르고, 전체보다 많이 고르지 않는다', () => {
    const order = pickRandomOrder(TOTAL)
    expect(order).toHaveLength(10)
    expect(new Set(order).size).toBe(10)
    expect(order.every((value) => value >= 0 && value < TOTAL)).toBe(true)
    expect(pickRandomOrder(3)).toHaveLength(3)
  })
})

describe('PracticeRunScreen 이전/다음과 뒤로 가기', () => {
  const renderRun = (props: Partial<Parameters<typeof PracticeRunScreen>[0]> = {}) => {
    const handlers = { onIndexChange: vi.fn(), onExit: vi.fn(), onComplete: vi.fn(), onOpenConsent: vi.fn() }
    const view = render(<PracticeRunScreen category={category} mode="sequential" index={0} {...handlers} {...props} />)
    return { ...handlers, ...view }
  }

  it('첫 세트에서는 이전이 비활성이고, 다음은 바로 뒤 세트로 간다', async () => {
    const { onIndexChange } = renderRun({ index: 0 })
    expect(await screen.findByText('낱말1')).toBeTruthy()
    expect(screen.getByText(`발음 1 / ${TOTAL}`)).toBeTruthy()
    expect((screen.getByRole('button', { name: '이전 문제' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: '다음 문제' }))
    expect(onIndexChange).toHaveBeenCalledWith(1)
  })

  it('10번째에서 이전은 9번째, 다음은 11번째로 간다(특정 세트에서 들어와도 같은 영역 순서)', async () => {
    const { onIndexChange } = renderRun({ index: 9 })
    expect(await screen.findByText('낱말10')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '이전 문제' }))
    expect(onIndexChange).toHaveBeenLastCalledWith(8)
    fireEvent.click(screen.getByRole('button', { name: '다음 문제' }))
    expect(onIndexChange).toHaveBeenLastCalledWith(10)
  })

  it('마지막 세트에서는 다음이 비활성이다', async () => {
    renderRun({ index: TOTAL - 1 })
    expect(await screen.findByText(`낱말${TOTAL}`)).toBeTruthy()
    expect((screen.getByRole('button', { name: '다음 문제' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: '이전 문제' }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('페이지 경계(20번째→21번째)를 넘을 때 다음 페이지만 받아 오고, 받은 페이지는 다시 받지 않는다', async () => {
    const { rerender, onIndexChange, onExit, onComplete, onOpenConsent } = renderRun({ index: 19 })
    expect(await screen.findByText('낱말20')).toBeTruthy()
    rerender(<PracticeRunScreen category={category} mode="sequential" index={20} onIndexChange={onIndexChange} onExit={onExit} onComplete={onComplete} onOpenConsent={onOpenConsent} />)
    expect(await screen.findByText('낱말21')).toBeTruthy()
    rerender(<PracticeRunScreen category={category} mode="sequential" index={19} onIndexChange={onIndexChange} onExit={onExit} onComplete={onComplete} onOpenConsent={onOpenConsent} />)
    expect(await screen.findByText('낱말20')).toBeTruthy()
    const pages = exercisesApi.mock.calls.map((call) => call[1])
    expect(pages.filter((page) => page === 0)).toHaveLength(1)
    expect(pages.filter((page) => page === 1)).toHaveLength(1)
    expect(exercisesApi.mock.calls.every((call) => call[0] === 'articulation' && call[2] === PRACTICE_PAGE_SIZE)).toBe(true)
  })

  it('위쪽 뒤로 가기는 흐름을 끝내고 영역 화면으로 간다(이전 세트로 가지 않는다)', async () => {
    const { onExit, onIndexChange } = renderRun({ index: 5 })
    expect(await screen.findByText('낱말6')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '카테고리로 돌아가기' }))
    expect(onExit).toHaveBeenCalledOnce()
    expect(onIndexChange).not.toHaveBeenCalled()
  })

  it('랜덤 연습은 시작할 때 정한 순서를 그대로 따른다', async () => {
    const order = [30, 4, 17]
    const { rerender, onIndexChange, onExit, onComplete, onOpenConsent } = renderRun({ mode: 'random', index: 0, order })
    expect(await screen.findByText('낱말31')).toBeTruthy()
    expect(screen.getByText('랜덤 1 / 3')).toBeTruthy()
    rerender(<PracticeRunScreen category={category} mode="random" index={1} order={order} onIndexChange={onIndexChange} onExit={onExit} onComplete={onComplete} onOpenConsent={onOpenConsent} />)
    expect(await screen.findByText('낱말5')).toBeTruthy()
    rerender(<PracticeRunScreen category={category} mode="random" index={0} order={order} onIndexChange={onIndexChange} onExit={onExit} onComplete={onComplete} onOpenConsent={onOpenConsent} />)
    expect(await screen.findByText('낱말31')).toBeTruthy()
  })

  it('다시 연습은 연습했던 세트 목록 순서로 이동한다', async () => {
    renderRun({ mode: 'retry', index: 1 })
    expect(await screen.findByText('낱말3')).toBeTruthy()
    expect(screen.getByText('다시 연습 2 / 2')).toBeTruthy()
    expect(practicedApi).toHaveBeenCalledWith('articulation', 0, PRACTICE_PAGE_SIZE)
    expect((screen.getByRole('button', { name: '다음 문제' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('이해력은 이야기와 질문을 보여 주고 모범 답은 열어 볼 때만 보인다', async () => {
    exercisesApi.mockResolvedValue({ totalElements: 1, content: [{ exerciseId: 6001, title: '누가 했나요 · 꽃에 물 주기', categoryId: 'comprehension',
      instruction: '이야기: 할머니가 꽃에 물을 주셨어요.\n질문: 꽃에 물을 준 사람은 누구인가요?\n이야기를 읽고 질문에 알맞은 대답을 말해 보세요.',
      items: [{ itemId: 1, word: '할머니가 꽃에 물을 주셨어요.' }] }] })
    renderRun({ category: { ...category, id: 'comprehension', label: '이해력' } })
    expect(await screen.findByText(/질문: 꽃에 물을 준 사람은 누구인가요/)).toBeTruthy()
    expect(screen.queryByText('할머니가 꽃에 물을 주셨어요.')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '모범 답 보기' }))
    expect(screen.getByText('할머니가 꽃에 물을 주셨어요.')).toBeTruthy()
  })
})

describe('PracticeCategoryScreen', () => {
  it('전체 개수를 보여 주고, 목록은 20개씩 더 불러오며, 고른 세트 위치로 전체 연습을 시작한다', async () => {
    const onStart = vi.fn()
    render(<PracticeCategoryScreen category={category} onBack={vi.fn()} onStart={onStart} />)
    expect(await screen.findByText(`연습 세트 ${TOTAL}개`)).toBeTruthy()
    expect(screen.getAllByRole('listitem')).toHaveLength(PRACTICE_PAGE_SIZE)
    fireEvent.click(screen.getByRole('button', { name: `더 보기 (20 / ${TOTAL})` }))
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(40))
    expect(exercisesApi).toHaveBeenLastCalledWith('articulation', 1, PRACTICE_PAGE_SIZE)
    fireEvent.click(screen.getByRole('button', { name: /^10 세트 10/ }))
    expect(onStart).toHaveBeenLastCalledWith('sequential', 9)
    fireEvent.click(screen.getByRole('button', { name: '전체 발음 연습하기' }))
    expect(onStart).toHaveBeenLastCalledWith('sequential', 0)
    fireEvent.click(screen.getByRole('button', { name: '랜덤 연습' }))
    const [mode, index, order] = onStart.mock.lastCall!
    expect([mode, index, (order as number[]).length, new Set(order as number[]).size]).toEqual(['random', 0, 10, 10])
  })

  it('최근 학습과 다시 연습 목록은 연습했던 세트로 다시 연습을 시작한다', async () => {
    const onStart = vi.fn()
    render(<PracticeCategoryScreen category={category} onBack={vi.fn()} onStart={onStart} />)
    expect(await screen.findByText('최근 학습')).toBeTruthy()
    fireEvent.click(screen.getByRole('tab', { name: '다시 연습 2' }))
    fireEvent.click(screen.getByRole('button', { name: /^2 세트 3/ }))
    expect(onStart).toHaveBeenLastCalledWith('retry', 1)
  })

  it('연습한 기록이 없으면 다시 연습은 비활성이고 안내를 보여 준다', async () => {
    practicedApi.mockResolvedValue({ content: [], totalElements: 0 })
    render(<PracticeCategoryScreen category={category} onBack={vi.fn()} onStart={vi.fn()} />)
    await screen.findByText(`연습 세트 ${TOTAL}개`)
    await waitFor(() => expect((screen.getByRole('button', { name: '다시 연습' }) as HTMLButtonElement).disabled).toBe(true))
    expect(screen.queryByText('최근 학습')).toBeNull()
    fireEvent.click(screen.getByRole('tab', { name: '다시 연습 0' }))
    expect(screen.getByText('아직 이 영역에서 연습한 기록이 없어요')).toBeTruthy()
  })
})
