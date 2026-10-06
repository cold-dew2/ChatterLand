import type { Homework, LearnerType, Student } from '@/features/teacher/types'

type Row = Record<string, unknown>

export function mapStudent(item: Row, index = 0): Student {
  const status = String(item.status ?? 'ACTIVE').toUpperCase()
  const score = item.score ?? item.overallScore
  return {
    id: Number(item.studentId ?? item.id ?? index + 1), name: String(item.name ?? item.studentName ?? '학생'), age: Number(item.age ?? 0),
    status: ({ ACTIVE: '진행중', IN_PROGRESS: '진행중', PAUSED: '예정', UPCOMING: '예정', COMPLETED: '완료' } as Record<string, string>)[status] ?? String(item.status ?? '진행중'),
    learnerType: (item.learnerType === 'THERAPY' ? 'THERAPY' : 'GENERAL') as LearnerType,
    sessionsTotal: Number(item.sessionsTotal ?? 0), sessionsDone: Number(item.sessionsDone ?? 0),
    score: score == null ? null : Number(score), lastSession: String(item.lastSession ?? item.lastSessionDate ?? '-'),
    tags: Array.isArray(item.tags) ? item.tags.map(String) : [], parentPhone: String(item.parentPhone ?? ''), memo: String(item.memo ?? ''), centerName: String(item.centerName ?? ''),
  }
}

export function mapHomework(item: Row, index = 0): Homework {
  const status = String(item.status ?? '').toUpperCase()
  return {
    id: Number(item.homeworkId ?? item.id ?? index + 1), studentId: Number(item.studentId ?? 0),
    title: String(item.title ?? item.homeworkTitle ?? '숙제'), type: String(item.type ?? '기타'),
    dueDate: String(item.dueDate ?? ''), done: Boolean(item.done ?? item.completed ?? status === 'COMPLETED'),
    description: String(item.description ?? ''), targetMinutes: Number(item.targetMinutes ?? 10),
    version: typeof item.version === 'number' ? item.version : undefined,
    exerciseId: item.exerciseId == null ? null : Number(item.exerciseId),
    exerciseTitle: item.exerciseTitle == null ? null : String(item.exerciseTitle),
  }
}

export function localDateInput(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}
