"use client"

import { useCallback, useEffect, useState } from 'react'
import { studentApi } from '@/features/student/api/studentApi'
import type { HistoryItem, Homework, LoadState, Session, StudentSummary } from '@/features/student/types'
import { mapHistoryItem, mapHomework, mapSession, toNumberOrNull } from '@/features/student/utils/mappers'

const initialStudent: StudentSummary = {
  name: '학생', grade: '학습 중', averageMatchRate: null, totalAttempts: 0, sessionsTotal: 0, sessionsDone: 0, streak: 0,
}

/** 학생 홈에 필요한 데이터(프로필 요약·다음 세션·숙제·최근 학습 기록)를 서버에서 불러온다. */
export function useStudentHome() {
  const [student, setStudent] = useState<StudentSummary>(initialStudent)
  const [profileState, setProfileState] = useState<LoadState>('loading')
  const [nextSession, setNextSession] = useState<Session | null>(null)
  const [homeworks, setHomeworks] = useState<Homework[]>([])
  const [homeworkState, setHomeworkState] = useState<LoadState>('loading')
  const [recent, setRecent] = useState<HistoryItem[]>([])
  const [recentState, setRecentState] = useState<LoadState>('loading')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    studentApi.me().then((result) => {
      if (!active) return
      setStudent({
        name: String(result.name ?? '학생'), grade: String(result.level ?? '학습 중'),
        averageMatchRate: toNumberOrNull(result.averageMatchRate), totalAttempts: Number(result.totalAttempts ?? 0),
        sessionsTotal: Number(result.totalSessions ?? 0), sessionsDone: Number(result.completedSessions ?? 0), streak: Number(result.streak ?? 0),
      })
      setProfileState('ready')
    }).catch(() => { if (active) setProfileState('error') })
    studentApi.sessions(0, 1, 'UPCOMING').then((response) => {
      const row = (response as { content?: Record<string, unknown>[] }).content?.[0]
      if (active) setNextSession(row ? mapSession(row) : null)
    }).catch(() => { if (active) setNextSession(null) })
    studentApi.homeworks(0, 10).then((response) => {
      const content = (response as { content?: Record<string, unknown>[] }).content
      if (!content) throw new Error('숙제 응답을 확인할 수 없어요.')
      if (!active) return
      setHomeworks(content.map(mapHomework))
      setHomeworkState('ready')
    }).catch(() => { if (active) setHomeworkState('error') })
    studentApi.history(undefined, 0, 3).then((response) => {
      const content = (response as { content?: Record<string, unknown>[] }).content ?? []
      if (!active) return
      setRecent(content.map(mapHistoryItem))
      setRecentState('ready')
    }).catch(() => { if (active) setRecentState('error') })
    return () => { active = false }
  }, [revision])

  const reload = useCallback(() => {
    setProfileState('loading'); setHomeworkState('loading'); setRecentState('loading'); setRevision((value) => value + 1)
  }, [])
  const markHomeworkDone = useCallback((id: number) => {
    setHomeworks((current) => current.map((homework) => homework.id === id ? { ...homework, done: true } : homework))
  }, [])

  return { student, profileState, nextSession, homeworks, homeworkState, recent, recentState, reload, markHomeworkDone }
}
