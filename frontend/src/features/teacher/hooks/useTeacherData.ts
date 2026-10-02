"use client"

import { useCallback, useEffect, useState } from 'react'
import { authApi } from '@/features/auth/api/authApi'
import { teacherApi } from '@/features/teacher/api/teacherApi'
import type { Homework, Student, StudentFormValues } from '@/features/teacher/types'
import { mapHomework, mapStudent } from '@/features/teacher/utils/mappers'
import { errorMessage } from '@/shared/api/client'

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

  const addHomework = async (homework: Omit<Homework, 'id' | 'done'>) => {
    try {
      const saved = await teacherApi.addHomework(homework) as Record<string, unknown>
      setHomeworks((current) => [...current, mapHomework(saved)])
      succeed('숙제를 등록했어요.')
      return true
    } catch (error) {
      fail(error, '숙제를 등록하지 못했어요.')
      return false
    }
  }

  const updateHomework = async (id: number, body: Record<string, unknown>) => {
    try {
      const saved = await teacherApi.updateHomework(id, body) as Record<string, unknown>
      setHomeworks((current) => current.map((item) => item.id === id ? { ...mapHomework(saved), id } : item))
      succeed('숙제를 수정했어요.')
      return true
    } catch (error) {
      fail(error, '숙제를 수정하지 못했어요.')
      return false
    }
  }

  const toggleHomework = async (id: number) => {
    const homework = homeworks.find((item) => item.id === id)
    if (!homework) return
    try {
      await teacherApi.updateHomework(id, { done: !homework.done })
      setHomeworks((current) => current.map((item) => item.id === id ? { ...item, done: !item.done } : item))
      succeed(homework.done ? '숙제를 미완료로 바꿨어요.' : '숙제를 완료로 바꿨어요.')
    } catch (error) {
      fail(error, '숙제 상태를 변경하지 못했어요.')
    }
  }

  const deleteHomework = async (id: number) => {
    try {
      await teacherApi.deleteHomework(id)
      setHomeworks((current) => current.filter((item) => item.id !== id))
      succeed('숙제를 삭제했어요.')
      return true
    } catch (error) {
      fail(error, '숙제를 삭제하지 못했어요.')
      return false
    }
  }

  return {
    students, homeworks, loadState, reload, studentRevision, centerName, mutationError, successMessage, setMutationError,
    saveStudent, removeStudent, addHomework, updateHomework, toggleHomework, deleteHomework,
  }
}
