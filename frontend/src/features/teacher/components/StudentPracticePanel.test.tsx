import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const studentAttempts = vi.fn()
const studentAttemptSummary = vi.fn()
vi.mock('@/features/teacher/api/teacherApi', () => ({
  teacherApi: {
    studentAttempts: (...a: unknown[]) => studentAttempts(...a), studentAttemptSummary: (...a: unknown[]) => studentAttemptSummary(...a),
    speechFeedback: () => Promise.resolve({ analysisId: 'a', status: 'NOT_GENERATED', source: 'AI', reason: '학생이 아직 AI 설명을 만들지 않았어요.' }),
  },
}))

import StudentPracticePanel from '@/features/teacher/components/StudentPracticePanel'

const row = (id: number, type: 'SELF' | 'LESSON' | 'PRACTICE' | 'HOMEWORK', extra: Record<string, unknown> = {}) => ({ attemptId: id, attemptType: type, exerciseId: 1001, exerciseTitle: 'ㄱ 기본 자음 낱말 1',
  pronunciationRule: 'BASIC_CONSONANT', contentType: 'WORD', analysisId: `a-${id}`, analysisStatus: 'COMPLETED', targetText: '가방', transcript: '가방',
  textMatchRate: 100, createdAt: '2026-10-06 10:00', ...extra })

beforeEach(() => { studentAttempts.mockReset(); studentAttemptSummary.mockReset() })

describe('StudentPracticePanel', () => {
  it('shows counts and separates self practice from homework practice without treating the match rate as a score', async () => {
    studentAttemptSummary.mockResolvedValue({ totalCount: 2, practiceCount: 1, homeworkCount: 1, exerciseCount: 1, lastPracticedAt: '2026-10-06 10:00' })
    studentAttempts.mockResolvedValue({ content: [row(2, 'HOMEWORK', { homeworkId: 9, homeworkTitle: '1주차 받침' }), row(1, 'PRACTICE')], totalElements: 2 })
    render(<StudentPracticePanel studentId={7} />)
    expect(await screen.findByText(/숙제 "1주차 받침"/)).toBeTruthy()
    expect(screen.getAllByText('자율 연습').length).toBeGreaterThan(0)
    expect(screen.getByText('2회')).toBeTruthy()
    expect(screen.getAllByText('(발음 점수 아님)')).toHaveLength(2)
    expect(screen.getAllByText('미평가')).toHaveLength(2)
    expect(screen.getAllByRole('button', { name: /학생이 본 AI 설명 보기/ })).toHaveLength(2) // 기존 선생님 AI 설명 조회 재사용
    expect(studentAttempts).toHaveBeenCalledWith(7, undefined, 0, 20)

    fireEvent.click(screen.getByRole('tab', { name: '숙제 연습' }))
    await waitFor(() => expect(studentAttempts).toHaveBeenLastCalledWith(7, 'HOMEWORK', 0, 20))
  })

  it('shows empty and error states with retry', async () => {
    studentAttemptSummary.mockResolvedValue({ totalCount: 0, practiceCount: 0, homeworkCount: 0, exerciseCount: 0 })
    studentAttempts.mockResolvedValueOnce({ content: [], totalElements: 0 })
    const first = render(<StudentPracticePanel studentId={7} />)
    expect(await screen.findByText('아직 연습 기록이 없어요')).toBeTruthy()
    first.unmount()
    studentAttempts.mockRejectedValueOnce(new Error('담당 학생 정보에 접근할 수 없습니다.')).mockResolvedValueOnce({ content: [row(1, 'PRACTICE')], totalElements: 1 })
    render(<StudentPracticePanel studentId={7} />)
    expect(await screen.findByText('담당 학생 정보에 접근할 수 없습니다.')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /다시 시도/ }))
    expect(await screen.findByText('ㄱ 기본 자음 낱말 1')).toBeTruthy()
  })

  it('shows self, lesson and homework practice separately and labels unclassified older records without reclassifying them', async () => {
    studentAttemptSummary.mockResolvedValue({ totalCount: 4, practiceCount: 3, homeworkCount: 1, selfCount: 1, lessonCount: 1, unclassifiedCount: 1, exerciseCount: 1, lastPracticedAt: '2026-10-06 10:00' })
    studentAttempts.mockResolvedValue({ content: [row(4, 'SELF'), row(3, 'LESSON'), row(2, 'HOMEWORK'), row(1, 'PRACTICE')], totalElements: 4 })
    render(<StudentPracticePanel studentId={7} />)
    const panel = await screen.findByRole('region', { name: '연습 기록' })
    expect(await screen.findByText(/이전 기록 1회\(유형 구분 전\)/)).toBeTruthy()
    for (const label of ['자율 연습', '수업 연습', '숙제 연습', '이전 기록']) expect(screen.getAllByText(label).length).toBeGreaterThan(1) // 탭 + 기록 배지
    expect(panel.querySelectorAll('article')).toHaveLength(4)
    fireEvent.click(screen.getByRole('tab', { name: '수업 연습' }))
    await waitFor(() => expect(studentAttempts).toHaveBeenLastCalledWith(7, 'LESSON', 0, 20))
    fireEvent.click(screen.getByRole('tab', { name: '이전 기록' }))
    await waitFor(() => expect(studentAttempts).toHaveBeenLastCalledWith(7, 'PRACTICE', 0, 20))
  })
})

