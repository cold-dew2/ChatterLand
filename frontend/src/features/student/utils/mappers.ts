import type { Exercise, Homework, HistoryItem, Session } from '@/features/student/types'

type Row = Record<string, unknown>

const toNumberOrNull = (value: unknown) => value == null || value === '' || !Number.isFinite(Number(value)) ? null : Number(value)

export function mapExercise(exercise: Row, fallbackColor = 'var(--brand-primary)'): Exercise {
  const exerciseId = String(exercise.exerciseId ?? exercise.id ?? '')
  const entries: unknown[] = Array.isArray(exercise.items) ? exercise.items : []
  return {
    id: exerciseId,
    label: String(exercise.title ?? exercise.label ?? '연습'),
    color: String(exercise.color ?? fallbackColor),
    instruction: String(exercise.instruction ?? '화면을 보고 따라 말해보세요.'),
    inputType: exercise.inputType === 'read' || exercise.inputType === 'speak' ? exercise.inputType : 'mic',
    items: entries.map((entry, index) => {
      const value = entry && typeof entry === 'object' ? entry as Row : { word: entry }
      return { id: String(value.itemId ?? `${exerciseId}-${index}`), word: String(value.word ?? value.text ?? ''), emoji: String(value.emoji ?? '') }
    }),
  }
}

export function mapSession(item: Row): Session {
  const rawExercises = Array.isArray(item.exercises) ? item.exercises as Row[] : []
  return {
    id: String(item.sessionId ?? item.id ?? ''),
    title: String(item.title ?? '언어 연습'),
    date: String(item.date ?? item.scheduledAt ?? ''),
    done: Boolean(item.done ?? String(item.status ?? '').toUpperCase() === 'COMPLETED'),
    exercises: rawExercises.map((exercise) => mapExercise(exercise)),
  }
}

export function mapHomework(item: Row): Homework {
  return {
    id: Number(item.homeworkId ?? item.id),
    title: String(item.title ?? '숙제'),
    type: String(item.type ?? '연습'),
    description: String(item.description ?? ''),
    dueDate: String(item.dueDate ?? ''),
    done: Boolean(item.done),
    targetMinutes: Number(item.targetMinutes ?? 0),
  }
}

export function mapHistoryItem(item: Row): HistoryItem {
  const [date = '', time = ''] = String(item.date ?? '').split(' ')
  return {
    id: String(item.id ?? `${date}-${time}`),
    type: item.type === 'ai' || item.type === 'hw' ? item.type : 'word',
    title: String(item.title ?? '학습 활동'),
    date, time,
    score: toNumberOrNull(item.score),
    matchRate: toNumberOrNull(item.matchRate),
  }
}

export function isOverdue(dueDate: string, done: boolean) {
  return !done && Boolean(dueDate) && new Date(`${dueDate}T23:59:59`) < new Date()
}

export { toNumberOrNull }
