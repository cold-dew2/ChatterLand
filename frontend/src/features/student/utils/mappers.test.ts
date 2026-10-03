import { describe, expect, it } from 'vitest'
import { isOverdue, mapExercise, mapHistoryItem, mapHomework, toNumberOrNull } from '@/features/student/utils/mappers'

describe('student API mappers', () => {
  it('maps exercise items from server rows without inventing values', () => {
    const exercise = mapExercise({ exerciseId: 1, title: 'ㄹ 발음', instruction: '말해보세요', inputType: 'mic',
      items: [{ itemId: 11, text: '라디오' }, { itemId: 12, word: '리본', emoji: '🎀' }] })
    expect(exercise.id).toBe('1')
    expect(exercise.items).toEqual([{ id: '11', word: '라디오', emoji: '' }, { id: '12', word: '리본', emoji: '🎀' }])
    expect(mapExercise({ id: 2, inputType: 'unknown' }).inputType).toBe('mic')
  })

  it('keeps missing scores as null (no fallback to other values)', () => {
    const item = mapHistoryItem({ id: 5, type: 'word', title: '연습', date: '2026-10-02 10:30', score: null, matchRate: '87.5' })
    expect(item.score).toBeNull()
    expect(item.matchRate).toBe(87.5)
    expect(item.date).toBe('2026-10-02')
    expect(item.time).toBe('10:30')
    expect(mapHistoryItem({ type: 'other' }).type).toBe('word')
  })

  it('converts numeric-like values safely', () => {
    expect(toNumberOrNull(undefined)).toBeNull()
    expect(toNumberOrNull('')).toBeNull()
    expect(toNumberOrNull('abc')).toBeNull()
    expect(toNumberOrNull('0')).toBe(0)
  })

  it('maps homework and detects overdue only when not done', () => {
    const hw = mapHomework({ homeworkId: 3, title: '숙제', dueDate: '2000-01-01', done: false, targetMinutes: 10 })
    expect(hw.id).toBe(3)
    expect(isOverdue(hw.dueDate, false)).toBe(true)
    expect(isOverdue(hw.dueDate, true)).toBe(false)
    expect(isOverdue('', false)).toBe(false)
  })
})
