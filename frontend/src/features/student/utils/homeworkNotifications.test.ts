import { describe, expect, it } from 'vitest'
import { DEFAULT_NOTIFICATION_SETTINGS } from '@/features/account/api/accountApi'
import { homeworkNotices } from '@/features/student/utils/homeworkNotifications'
import type { Homework } from '@/features/student/types'

const now = new Date(2026, 9, 10, 9, 0)
const hw = (id: number, extra: Partial<Homework>): Homework => ({ id, title: `숙제${id}`, type: '연습', description: '', dueDate: '2026-10-20', done: false, targetMinutes: 10, version: 0, createdAt: '2026-09-01', ...extra })

describe('homeworkNotices', () => {
  it('실제 숙제 상태에서만 새 숙제·내용 변경·마감 임박·마감 지남을 만들고 끝낸 숙제는 뺀다', () => {
    const notices = homeworkNotices([
      hw(1, { createdAt: '2026-10-09' }),
      hw(2, { version: 2 }),
      hw(3, { dueDate: '2026-10-11' }),
      hw(4, { dueDate: '2026-10-05' }),
      hw(5, { dueDate: '2026-10-05', done: true, version: 3 }),
      hw(6, {}),
    ], DEFAULT_NOTIFICATION_SETTINGS, now)
    expect(notices.map((notice) => notice.key)).toEqual(['new-1', 'changed-2', 'due-3', 'overdue-4'])
    expect(notices.find((notice) => notice.key === 'due-3')?.message).toBe('내일 마감이에요.')
  })

  it('끈 종류의 알림은 보여 주지 않는다', () => {
    const notices = homeworkNotices([hw(1, { createdAt: '2026-10-09', dueDate: '2026-10-10' })],
      { ...DEFAULT_NOTIFICATION_SETTINGS, homeworkAssigned: false }, now)
    expect(notices.map((notice) => notice.key)).toEqual(['due-1'])
    expect(notices[0].message).toBe('오늘 마감이에요.')
  })
})
