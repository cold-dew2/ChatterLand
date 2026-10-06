import type { SpeechAnalysis } from '@/features/student/types'

export type TeacherView = 'home' | 'students' | 'homework' | 'analytics'

export type LearnerType = 'GENERAL' | 'THERAPY'

export type Student = {
  id: number; name: string; age: number; status: string; learnerType: LearnerType
  sessionsTotal: number; sessionsDone: number; score: number | null
  lastSession: string; tags: string[]; parentPhone: string; memo: string; centerName: string
}

export type Homework = {
  id: number; studentId: number; title: string; type: string
  dueDate: string; done: boolean; description: string; targetMinutes: number
  /** 서버의 숙제 버전(수정할 때마다 1 증가). 수정 요청에 함께 보내 동시 수정 충돌을 막는다. */
  version?: number
  /** 숙제로 낸 연습 세트(없으면 자유 숙제) */
  exerciseId?: number | null
  exerciseTitle?: string | null
}

export type StudentFormValues = {
  name: string; age: number; status: string; sessionsTotal: number; tags: string[]
  parentPhone: string; memo: string; learnerType: LearnerType
}

/** GET /teachers/me/students/{id}/speech-analyses 항목 */
export type TeacherSpeechAnalysis = SpeechAnalysis & {
  studentId: number
  exerciseTitle?: string
  hasAudio?: boolean
  createdAt?: string
  reviewedAt?: string | null
}

export type SpeechJudgement = 'ACCEPTABLE' | 'NEEDS_PRACTICE' | 'UNCLEAR'

export const learnerTypeLabel: Record<LearnerType, string> = { GENERAL: '일반 아동 (문장 연습)', THERAPY: '언어재활 아동 (발음 평가)' }
