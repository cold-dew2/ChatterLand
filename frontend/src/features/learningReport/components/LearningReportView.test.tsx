import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import LearningReportView from '@/features/learningReport/components/LearningReportView'
import { changeText, chartDays, mapLearningReport } from '@/features/learningReport/utils/learningReport'

const area = (categoryId: string, name: string, attempts: number, practiced: number) => ({ categoryId, name, attempts, practicedExercises: practiced, totalExercises: 300 })
const report = (period: string, extra: Record<string, unknown> = {}) => ({
  period, startDate: period === 'all' ? '2026-08-01' : '2026-10-07', endDate: '2026-10-13',
  totalAttempts: 4, practicedExercises: 2, practicedActiveExercises: 2, activeExercises: 1589, practiceDays: 2, homeworkAttempts: 1, retriedExercises: 1,
  categories: [area('articulation', '발음', 4, 2), area('vocabulary', '어휘력', 0, 0)],
  daily: [{ date: '2026-10-13', attempts: 3, exercises: 2 }, { date: '2026-10-10', attempts: 1, exercises: 1 }],
  repeated: [{ exerciseId: 1, title: 'ㄹ 발음', categoryName: '발음', attempts: 3, days: 1, lastPracticedOn: '2026-10-13' }],
  recentAttempts: [{ attemptId: 9, title: 'ㄹ 발음', categoryName: '발음', attemptType: 'HOMEWORK', practicedAt: '2026-10-13 10:00' }],
  comparison: null, ...extra,
})

describe('누적 학습 리포트', () => {
  it('fills every day of the period with 0 and counts from the server end date', () => {
    const days = chartDays(mapLearningReport(report('7d')))
    expect(days).toHaveLength(7)
    expect(days[0].key).toBe('2026-10-07')
    expect(days[6]).toMatchObject({ key: '2026-10-13', attempts: 3 })
    expect(days.find((day) => day.key === '2026-10-08')?.attempts).toBe(0)
    expect(chartDays(mapLearningReport(report('all')))).toHaveLength(30)
    expect(changeText(3, '회')).toBe('+3회')
    expect(changeText(-1, '일')).toBe('-1일')
    expect(changeText(0, '개')).toBe('변화 없음')
  })

  it('shows a change only when the previous period has records, and switches period', async () => {
    const load = vi.fn((period: string) => Promise.resolve(period === '30d'
      ? report('30d', { comparison: { startDate: '2026-08-15', endDate: '2026-09-13', totalAttempts: 1, totalAttemptsChange: 3, practiceDays: 1, practiceDaysChange: 1, practicedExercises: 1, practicedExercisesChange: 1 } })
      : report(period)))
    render(<LearningReportView load={load} initialPeriod="7d" />)
    expect(await screen.findByText('이전 7일에 저장된 기록이 없어 비교하지 않아요.')).toBeTruthy()
    expect(screen.queryByText(/이전 7일보다/)).toBeNull()
    expect(screen.getByText(/전체 연습 콘텐츠 1,589개 중/)).toBeTruthy()
    expect(within(screen.getByRole('list', { name: '날짜별 연습 횟수' })).getAllByRole('listitem')).toHaveLength(7)
    expect(screen.getByText('이 기간에 연습하지 않았어요')).toBeTruthy()
    expect(screen.getByText('숙제 연습')).toBeTruthy()

    fireEvent.click(screen.getByRole('tab', { name: '최근 30일' }))
    expect(await screen.findByText('이전 30일보다 +3회')).toBeTruthy()
    expect(load).toHaveBeenLastCalledWith('30d')
    // 숫자로 된 점수·정확도는 없다(안내 문구의 '발음 점수·정확도는 계산하지 않으며'만 있음)
    expect(screen.queryByText(/(점수|정확도)\s*[0-9]|[0-9]+\s*점\b/)).toBeNull()
    expect(screen.getByText(/발음 점수·정확도는 계산하지 않으며/)).toBeTruthy()
  })

  it('separates empty, error and retry states', async () => {
    const load = vi.fn()
      .mockRejectedValueOnce(new Error('서버 오류'))
      .mockResolvedValueOnce(report('7d', { totalAttempts: 0, daily: [], repeated: [], recentAttempts: [] }))
    render(<LearningReportView load={load} initialPeriod="7d" emptyDescription="연습하면 쌓여요." />)
    expect(await screen.findByText('서버 오류')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /다시 시도/ }))
    expect(await screen.findByText('최근 7일 동안 저장된 연습 기록이 없어요')).toBeTruthy()
    expect(screen.getByText('연습하면 쌓여요.')).toBeTruthy()
  })
})
