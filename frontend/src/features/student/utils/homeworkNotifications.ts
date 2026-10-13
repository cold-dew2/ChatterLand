import type { NotificationSettings } from '@/features/account/api/accountApi'
import type { Homework } from '@/features/student/types'

export type HomeworkNotice = { key: string; kind: keyof NotificationSettings; homeworkId: number; title: string; message: string }

const DAY = 86_400_000
/** 새 숙제로 보여 주는 기간(등록 후) */
const NEW_DAYS = 3

const startOfDay = (value: Date) => new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime()
const parseDate = (value: string) => {
  const [year, month, day] = value.slice(0, 10).split('-').map(Number)
  return year && month && day ? new Date(year, month - 1, day).getTime() : null
}

/**
 * 숙제 알림(앱 안). 실제 숙제 데이터에서만 만든다: 새 숙제(등록 3일 이내)·내용 변경(선생님이 수정해 버전이 올라감)·
 * 마감 임박(오늘·내일 마감)·마감 지남. 끝낸 숙제는 알리지 않고, 사용자가 끈 종류는 빼고 보여 준다.
 */
export function homeworkNotices(homeworks: Homework[], settings: NotificationSettings, now = new Date()): HomeworkNotice[] {
  const today = startOfDay(now)
  const notices: HomeworkNotice[] = []
  for (const homework of homeworks) {
    if (homework.done) continue
    const created = homework.createdAt ? parseDate(homework.createdAt) : null
    const due = homework.dueDate ? parseDate(homework.dueDate) : null
    const isNew = created !== null && today - created < NEW_DAYS * DAY
    if (settings.homeworkAssigned && isNew)
      notices.push({ key: `new-${homework.id}`, kind: 'homeworkAssigned', homeworkId: homework.id, title: homework.title, message: '새 숙제가 등록됐어요.' })
    if (settings.homeworkChanged && !isNew && (homework.version ?? 0) > 0)
      notices.push({ key: `changed-${homework.id}`, kind: 'homeworkChanged', homeworkId: homework.id, title: homework.title, message: '선생님이 숙제 내용을 바꿨어요.' })
    if (due !== null && due < today) {
      if (settings.homeworkOverdue)
        notices.push({ key: `overdue-${homework.id}`, kind: 'homeworkOverdue', homeworkId: homework.id, title: homework.title, message: `마감(${homework.dueDate})이 지났어요.` })
    } else if (due !== null && due - today <= DAY && settings.homeworkDueSoon) {
      notices.push({ key: `due-${homework.id}`, kind: 'homeworkDueSoon', homeworkId: homework.id, title: homework.title, message: due === today ? '오늘 마감이에요.' : '내일 마감이에요.' })
    }
  }
  return notices
}
