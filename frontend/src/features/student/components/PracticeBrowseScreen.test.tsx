import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const practiceContents = vi.fn()
vi.mock('@/features/student/api/studentApi', () => ({
  studentApi: {
    practiceContents: (...a: unknown[]) => practiceContents(...a),
    categories: () => Promise.resolve([{ categoryId: 'articulation', name: '발음' }]),
    history: () => Promise.resolve({ content: [{ id: 1, type: 'word', title: 'ㄱ 기본 자음 낱말 1', date: '2026-10-06 10:00', attemptType: 'HOMEWORK' }] }),
  },
}))

import PracticeBrowseScreen from '@/features/student/components/PracticeBrowseScreen'

const content = (id: number, title: string) => ({ exerciseId: id, title, instruction: '따라 말해 보세요.', inputType: 'mic', color: '#5BA08E',
  difficulty: 'BEGINNER', contentType: 'WORD', pronunciationRule: 'BASIC_CONSONANT', items: [{ itemId: id * 10, word: '가방' }, { itemId: id * 10 + 1, word: '고구마' }] })

beforeEach(() => practiceContents.mockReset())

describe('PracticeBrowseScreen', () => {
  it('lists content with its labels, filters it and starts practice without homework', async () => {
    practiceContents.mockResolvedValue({ content: [content(1001, 'ㄱ 기본 자음 낱말 1')], totalElements: 1, totalPages: 1, page: 0 })
    const onStart = vi.fn()
    render(<PracticeBrowseScreen onBack={vi.fn()} onStart={onStart} />)
    expect(await screen.findByText('연습 세트 1개')).toBeTruthy()
    expect(screen.getByText('가방 · 고구마')).toBeTruthy()
    expect(screen.getAllByText('기본 자음').length).toBeGreaterThan(0)
    expect(screen.getAllByText('초급').length).toBeGreaterThan(1) // 배지 + 필터 선택지
    expect(await screen.findByText('숙제')).toBeTruthy() // 최근 연습: 숙제 연습 표시

    fireEvent.change(screen.getByLabelText('발음 유형'), { target: { value: 'LIAISON' } })
    await waitFor(() => expect(practiceContents).toHaveBeenLastCalledWith(expect.objectContaining({ rule: 'LIAISON', page: 0 })))
    fireEvent.change(screen.getByLabelText('검색'), { target: { value: '고구마' } })
    fireEvent.click(screen.getByRole('button', { name: '검색하기' }))
    await waitFor(() => expect(practiceContents).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '고구마', rule: 'LIAISON' })))

    fireEvent.click(await screen.findByRole('button', { name: 'ㄱ 기본 자음 낱말 1 연습 시작' }))
    expect(onStart).toHaveBeenCalledWith(expect.objectContaining({ id: '1001', items: [expect.objectContaining({ id: '10010', word: '가방' }), expect.anything()] }))
  })

  it('loads more pages and separates empty results and errors', async () => {
    practiceContents.mockResolvedValueOnce({ content: [content(1, '세트 1')], totalElements: 2, totalPages: 2, page: 0 })
      .mockResolvedValueOnce({ content: [content(2, '세트 2')], totalElements: 2, totalPages: 2, page: 1 })
    render(<PracticeBrowseScreen onBack={vi.fn()} onStart={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: '더 보기' }))
    expect(await screen.findByText('세트 2')).toBeTruthy()
    expect(practiceContents).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull()
  })

  it('shows an empty state and an error with retry', async () => {
    practiceContents.mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, page: 0 })
    const first = render(<PracticeBrowseScreen onBack={vi.fn()} onStart={vi.fn()} />)
    expect(await screen.findByText('조건에 맞는 연습이 없어요')).toBeTruthy()
    first.unmount()
    practiceContents.mockRejectedValueOnce(new Error('연결 오류')).mockResolvedValueOnce({ content: [content(1, '세트 1')], totalElements: 1, totalPages: 1, page: 0 })
    render(<PracticeBrowseScreen onBack={vi.fn()} onStart={vi.fn()} />)
    expect(await screen.findByText('연결 오류')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /다시 시도/ }))
    expect(await screen.findByText('세트 1')).toBeTruthy()
  })
})
