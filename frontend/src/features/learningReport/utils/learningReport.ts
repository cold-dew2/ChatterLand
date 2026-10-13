/**
 * 누적 학습 리포트(학생 학습 현황·선생님 학생 리포트·보호자 자녀 리포트 공통 응답).
 * 서버가 저장된 연습 기록만 센다: 기록 1행 = 녹음해서 저장한 문항 1개. 점수·정확도는 없다.
 */
export type ReportPeriod = '7d' | '30d' | 'all'

export type ReportArea = { categoryId: string; name: string; attempts: number; practicedExercises: number; totalExercises: number }
export type ReportDay = { date: string; attempts: number; exercises: number }
export type ReportRepeated = { exerciseId: number; title: string; categoryName: string; attempts: number; days: number; lastPracticedOn: string }
export type ReportRecent = { attemptId: number; title: string; categoryName: string; attemptType: string; practicedAt: string }
export type ReportComparison = {
  startDate: string; endDate: string
  totalAttempts: number; totalAttemptsChange: number
  practiceDays: number; practiceDaysChange: number
  practicedExercises: number; practicedExercisesChange: number
}
export type LearningReport = {
  period: ReportPeriod; startDate: string | null; endDate: string
  totalAttempts: number; practicedExercises: number; practicedActiveExercises: number; activeExercises: number
  practiceDays: number; homeworkAttempts: number; retriedExercises: number
  categories: ReportArea[]; daily: ReportDay[]; repeated: ReportRepeated[]; recentAttempts: ReportRecent[]
  /** 이전 기간에 기록이 있을 때만(없으면 null — 변화량을 만들지 않음) */
  comparison: ReportComparison | null
}

export const PERIOD_LABELS: Record<ReportPeriod, string> = { '7d': '최근 7일', '30d': '최근 30일', all: '전체 기간' }

const num = (value: unknown) => Number(value ?? 0) || 0
const text = (value: unknown) => (value === null || value === undefined ? '' : String(value))
const rows = (value: unknown) => (Array.isArray(value) ? value as Record<string, unknown>[] : [])

export function mapLearningReport(row: Record<string, unknown>): LearningReport {
  const comparison = row.comparison && typeof row.comparison === 'object' ? row.comparison as Record<string, unknown> : null
  const period = (['7d', '30d', 'all'].includes(String(row.period)) ? row.period : 'all') as ReportPeriod
  return {
    period, startDate: row.startDate ? String(row.startDate) : null, endDate: text(row.endDate),
    totalAttempts: num(row.totalAttempts), practicedExercises: num(row.practicedExercises), practicedActiveExercises: num(row.practicedActiveExercises),
    activeExercises: num(row.activeExercises), practiceDays: num(row.practiceDays), homeworkAttempts: num(row.homeworkAttempts), retriedExercises: num(row.retriedExercises),
    categories: rows(row.categories).map((area) => ({ categoryId: text(area.categoryId), name: text(area.name), attempts: num(area.attempts),
      practicedExercises: num(area.practicedExercises), totalExercises: num(area.totalExercises) })),
    daily: rows(row.daily).map((day) => ({ date: text(day.date), attempts: num(day.attempts), exercises: num(day.exercises) })),
    repeated: rows(row.repeated).map((item) => ({ exerciseId: num(item.exerciseId), title: text(item.title), categoryName: text(item.categoryName),
      attempts: num(item.attempts), days: num(item.days), lastPracticedOn: text(item.lastPracticedOn) })),
    recentAttempts: rows(row.recentAttempts).map((item) => ({ attemptId: num(item.attemptId), title: text(item.title), categoryName: text(item.categoryName),
      attemptType: text(item.attemptType), practicedAt: text(item.practicedAt) })),
    comparison: comparison ? {
      startDate: text(comparison.startDate), endDate: text(comparison.endDate),
      totalAttempts: num(comparison.totalAttempts), totalAttemptsChange: num(comparison.totalAttemptsChange),
      practiceDays: num(comparison.practiceDays), practiceDaysChange: num(comparison.practiceDaysChange),
      practicedExercises: num(comparison.practicedExercises), practicedExercisesChange: num(comparison.practicedExercisesChange),
    } : null,
  }
}

const dateKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`

/**
 * 막대 그래프용 날짜 칸: 기간의 모든 날(기록 없는 날은 0). 전체 기간은 끝 날짜 기준 최근 30일만 그린다.
 * 날짜는 서버가 준 끝 날짜(endDate)에서 거꾸로 센다(기기 시계와 달라도 서버 집계와 맞도록).
 */
export function chartDays(report: LearningReport) {
  const span = report.period === '7d' ? 7 : 30
  const [y, m, d] = (report.endDate || dateKey(new Date())).split('-').map(Number)
  const byDate = new Map(report.daily.map((day) => [day.date, day.attempts]))
  return Array.from({ length: span }, (_, i) => {
    const date = new Date(y, m - 1, d - (span - 1 - i))
    const key = dateKey(date)
    return { key, label: `${date.getMonth() + 1}/${date.getDate()}`, attempts: byDate.get(key) ?? 0 }
  })
}

export const attemptTypeLabel: Record<string, string> = { HOMEWORK: '숙제 연습', SELF: '자율 연습', LESSON: '수업 연습', PRACTICE: '연습' }

/** 변화량 문구(+3회 / -1일 / 변화 없음) */
export function changeText(change: number, unit: string) {
  if (change === 0) return '변화 없음'
  return `${change > 0 ? '+' : ''}${change}${unit}`
}
