"use client"

import { useCallback, useEffect, useRef, useState } from 'react'
import { authApi } from '@/features/auth/api/authApi'
import { teacherApi } from '@/features/teacher/api/teacherApi'
import type { Homework, Student, StudentFormValues } from '@/features/teacher/types'
import { mapHomework, mapStudent } from '@/features/teacher/utils/mappers'
import { ApiError, errorMessage } from '@/shared/api/client'
import { newIdempotencyKey } from '@/shared/api/idempotencyKey'

const VERSION_CONFLICT_MESSAGE = '다른 곳에서 먼저 바뀐 숙제예요. 최신 내용을 불러왔으니 확인한 뒤 다시 저장해 주세요.'
const isVersionConflict = (error: unknown) => error instanceof ApiError && error.status === 409 && error.code === 'VERSION_CONFLICT'

/**
 * 선생님 화면의 담당 학생·숙제 데이터와 등록·수정·삭제 요청.
 * 요청 결과(성공/실패 문구)를 함께 관리해 목록과 상세 화면이 같은 데이터로 갱신되게 한다.
 */
export function useTeacherData() {
  const [students, setStudents] = useState<Student[]>([])
  const [homeworks, setHomeworks] = useState<Homework[]>([])
  const [loadState, setLoadState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [loadRevision, setLoadRevision] = useState(0)
  const [studentRevision, setStudentRevision] = useState(0)
  const [centerName, setCenterName] = useState('')
  const [mutationError, setMutationError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  // 숙제 등록 요청 키: 같은 내용을 다시 보내면(응답 유실 후 재시도) 같은 키를 써서 서버가 중복으로 만들지 않게 한다.
  const pendingCreate = useRef<{ payload: string; key: string } | null>(null)

  useEffect(() => {
    let active = true
    authApi.me().then((user) => { if (active) setCenterName(user.centerName ?? '') }).catch(() => undefined)
    Promise.all([teacherApi.students(undefined, undefined, 0, 100), teacherApi.homeworks(undefined, undefined, 0, 100)]).then(([studentPage, homeworkPage]) => {
      if (!active) return
      setStudents(((studentPage as { content?: Record<string, unknown>[] }).content ?? []).map(mapStudent))
      setHomeworks(((homeworkPage as { content?: Record<string, unknown>[] }).content ?? []).map(mapHomework))
      setLoadState('ready')
    }).catch(() => { if (active) setLoadState('error') })
    return () => { active = false }
  }, [loadRevision])

  useEffect(() => {
    if (!successMessage) return
    const timer = window.setTimeout(() => setSuccessMessage(''), 3000)
    return () => window.clearTimeout(timer)
  }, [successMessage])

  const reload = useCallback(() => { setLoadState('loading'); setLoadRevision((value) => value + 1) }, [])
  const succeed = (message: string) => { setMutationError(''); setSuccessMessage(message) }
  const fail = (error: unknown, fallback: string) => { setSuccessMessage(''); setMutationError(errorMessage(error, fallback)) }

  const saveStudent = async (values: StudentFormValues, editing: Student | null, existingStudentId?: number) => {
    try {
      const request = existingStudentId
        ? teacherApi.addStudent({ ...values, studentId: existingStudentId })
        : editing ? teacherApi.updateStudent(editing.id, values) : teacherApi.addStudent(values)
      const saved = mapStudent(await request as Record<string, unknown>)
      setStudents((current) => current.some((item) => item.id === saved.id) ? current.map((item) => item.id === saved.id ? saved : item) : [...current, saved])
      setStudentRevision((revision) => revision + 1)
      succeed(editing ? '학생 정보를 저장했어요.' : '제자를 추가했어요.')
      return true
    } catch (error) {
      fail(error, editing ? '학생 정보를 저장하지 못했어요.' : '학생을 등록하지 못했어요.')
      return false
    }
  }

  const removeStudent = async (studentId: number) => {
    try {
      await teacherApi.deleteStudent(studentId)
      setStudents((current) => current.filter((item) => item.id !== studentId))
      setHomeworks((current) => current.filter((item) => item.studentId !== studentId))
      setStudentRevision((revision) => revision + 1)
      succeed('담당 목록에서 해제했어요.')
      return true
    } catch (error) {
      fail(error, '학생을 담당 목록에서 삭제하지 못했어요.')
      return false
    }
  }

  /** 숙제 목록만 다시 불러온다(수정 충돌 뒤 최신 버전으로 맞추기). 실패하면 기존 목록을 유지한다. */
  const refreshHomeworks = async () => {
    try {
      const page = await teacherApi.homeworks(undefined, undefined, 0, 100) as { content?: Record<string, unknown>[] }
      setHomeworks((page.content ?? []).map(mapHomework))
    } catch { /* 충돌 안내는 그대로 두고, 목록은 다음 새로고침에서 다시 불러온다 */ }
  }

  const addHomework = async (homework: Omit<Homework, 'id' | 'done'>) => {
    const payload = JSON.stringify(homework)
    if (pendingCreate.current?.payload !== payload) pendingCreate.current = { payload, key: newIdempotencyKey() }
    try {
      const saved = mapHomework(await teacherApi.addHomework(homework, pendingCreate.current.key) as Record<string, unknown>)
      pendingCreate.current = null
      setHomeworks((current) => current.some((item) => item.id === saved.id) ? current : [...current, saved])
      succeed('숙제를 등록했어요.')
      return true
    } catch (error) {
      fail(error, '숙제를 등록하지 못했어요.')
      return false
    }
  }

  const updateHomework = async (id: number, body: Record<string, unknown>) => {
    // version은 서버에서 필수다(없으면 400). 목록이 내려준 값을 그대로 보낸다.
    const version = homeworks.find((item) => item.id === id)?.version
    try {
      const saved = await teacherApi.updateHomework(id, { ...body, version }) as Record<string, unknown>
      setHomeworks((current) => current.map((item) => item.id === id ? { ...item, ...mapHomework(saved), id, studentId: item.studentId } : item))
      succeed('숙제를 수정했어요.')
      return true
    } catch (error) {
      if (isVersionConflict(error)) {
        await refreshHomeworks()
        setSuccessMessage('')
        setMutationError(VERSION_CONFLICT_MESSAGE)
        return false
      }
      fail(error, '숙제를 수정하지 못했어요.')
      return false
    }
  }

  const toggleHomework = async (id: number) => {
    const homework = homeworks.find((item) => item.id === id)
    if (!homework) return
    try {
      const saved = mapHomework(await teacherApi.updateHomework(id, { done: !homework.done, version: homework.version }) as Record<string, unknown>)
      setHomeworks((current) => current.map((item) => item.id === id ? { ...item, done: saved.done, version: saved.version } : item))
      succeed(homework.done ? '숙제를 미완료로 바꿨어요.' : '숙제를 완료로 바꿨어요.')
    } catch (error) {
      if (isVersionConflict(error)) {
        await refreshHomeworks()
        setSuccessMessage('')
        setMutationError('다른 곳에서 먼저 바뀐 숙제예요. 최신 상태를 불러왔으니 다시 확인해 주세요.')
        return
      }
      fail(error, '숙제 상태를 변경하지 못했어요.')
    }
  }

  /** 반환값이 true면 삭제 확인 창을 닫는다. 버전 충돌이면 지우지 않고 최신 목록을 보여주기 위해 창을 닫는다. */
  const deleteHomework = async (id: number) => {
    const version = homeworks.find((item) => item.id === id)?.version ?? 0
    try {
      await teacherApi.deleteHomework(id, version)
      setHomeworks((current) => current.filter((item) => item.id !== id))
      succeed('숙제를 삭제했어요.')
      return true
    } catch (error) {
      if (isVersionConflict(error)) {
        await refreshHomeworks()
        setSuccessMessage('')
        setMutationError('다른 곳에서 먼저 바뀐 숙제라 삭제하지 않았어요. 최신 내용을 확인한 뒤 다시 삭제해 주세요.')
        return true
      }
      fail(error, '숙제를 삭제하지 못했어요.')
      return false
    }
  }

  return {
    students, homeworks, loadState, reload, studentRevision, centerName, mutationError, successMessage, setMutationError,
    saveStudent, removeStudent, addHomework, updateHomework, toggleHomework, deleteHomework,
  }
}
