import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/client'

const students = vi.fn()
const homeworks = vi.fn()
const addHomework = vi.fn()
const updateHomework = vi.fn()
const deleteHomework = vi.fn()
vi.mock('@/features/teacher/api/teacherApi', () => ({
  teacherApi: {
    students: (...a: unknown[]) => students(...a), homeworks: (...a: unknown[]) => homeworks(...a),
    addHomework: (...a: unknown[]) => addHomework(...a), updateHomework: (...a: unknown[]) => updateHomework(...a),
    deleteHomework: (...a: unknown[]) => deleteHomework(...a),
  },
}))
vi.mock('@/features/auth/api/authApi', () => ({ authApi: { me: () => Promise.resolve({ centerName: '센터' }) } }))

import { useTeacherData } from '@/features/teacher/hooks/useTeacherData'

const row = (id: number, version: number, extra: Record<string, unknown> = {}) => ({
  homeworkId: id, studentId: 7, title: `숙제 ${id}`, type: '말하기 연습', dueDate: '2099-12-31', done: false, targetMinutes: 10, version, ...extra,
})
const newHomework = { studentId: 7, title: 'ㄹ 연습', type: '말하기 연습', dueDate: '2099-12-31', description: '설명', targetMinutes: 10 }
const conflict = () => new ApiError('다른 곳에서 먼저 바뀐 숙제예요.', 409, 'VERSION_CONFLICT')

async function loaded(rows: unknown[]) {
  homeworks.mockResolvedValue({ content: rows })
  const hook = renderHook(() => useTeacherData())
  await waitFor(() => expect(hook.result.current.loadState).toBe('ready'))
  return hook
}

beforeEach(() => {
  for (const mock of [students, homeworks, addHomework, updateHomework, deleteHomework]) mock.mockReset()
  students.mockResolvedValue({ content: [{ studentId: 7, name: '학생' }] })
})

describe('useTeacherData homework creation (Idempotency-Key)', () => {
  it('reuses the key when the same homework is sent again after a failure, and uses a new key for new content', async () => {
    const { result } = await loaded([])
    addHomework.mockRejectedValueOnce(new ApiError('서버에 연결할 수 없어요.', 0, 'NETWORK_ERROR')).mockResolvedValueOnce(row(1, 0))
    await act(async () => { expect(await result.current.addHomework(newHomework)).toBe(false) })
    await act(async () => { expect(await result.current.addHomework(newHomework)).toBe(true) })
    const [firstKey, retryKey] = addHomework.mock.calls.map((call) => call[1] as string)
    expect(firstKey).toMatch(/^[A-Za-z0-9_-]{8,64}$/)
    expect(retryKey).toBe(firstKey)

    addHomework.mockResolvedValueOnce(row(2, 0))
    await act(async () => { await result.current.addHomework({ ...newHomework, title: '다른 숙제' }) })
    expect(addHomework.mock.calls[2][1]).not.toBe(firstKey)
    // 성공한 뒤 같은 내용을 다시 등록하면 새 등록 의도이므로 새 키를 쓴다.
    addHomework.mockResolvedValueOnce(row(3, 0))
    await act(async () => { await result.current.addHomework(newHomework) })
    expect(addHomework.mock.calls[3][1]).not.toBe(firstKey)
  })

  it('does not list the same homework twice when the server returns the earlier one (reused)', async () => {
    const { result } = await loaded([row(1, 0)])
    addHomework.mockResolvedValue({ ...row(1, 0), reused: true })
    await act(async () => { await result.current.addHomework(newHomework) })
    expect(result.current.homeworks.map((item) => item.id)).toEqual([1])
  })
})

describe('useTeacherData homework edits (version)', () => {
  it('sends the loaded version and keeps the new version from the response', async () => {
    const { result } = await loaded([row(1, 4)])
    updateHomework.mockResolvedValue(row(1, 5, { title: '바뀐 제목' }))
    await act(async () => { expect(await result.current.updateHomework(1, { title: '바뀐 제목' })).toBe(true) })
    expect(updateHomework).toHaveBeenCalledWith(1, { title: '바뀐 제목', version: 4 })
    expect(result.current.homeworks[0]).toMatchObject({ title: '바뀐 제목', version: 5 })
  })

  it('on a version conflict reloads the latest homework, explains it and keeps the modal open', async () => {
    const { result } = await loaded([row(1, 0)])
    updateHomework.mockRejectedValue(conflict())
    homeworks.mockResolvedValue({ content: [row(1, 2, { title: '다른 탭에서 바꾼 제목' })] })
    await act(async () => { expect(await result.current.updateHomework(1, { title: '내 제목' })).toBe(false) })
    expect(homeworks).toHaveBeenCalledTimes(2)
    expect(result.current.homeworks[0]).toMatchObject({ title: '다른 탭에서 바꾼 제목', version: 2 })
    expect(result.current.mutationError).toContain('최신 내용을 불러왔으니')
    expect(result.current.successMessage).toBe('')

    // 최신 버전을 본 뒤 다시 저장하면 그 버전으로 보낸다.
    updateHomework.mockReset().mockResolvedValue(row(1, 3, { title: '내 제목' }))
    await act(async () => { expect(await result.current.updateHomework(1, { title: '내 제목' })).toBe(true) })
    expect(updateHomework).toHaveBeenCalledWith(1, { title: '내 제목', version: 2 })
  })

  it('toggles completion with the version and refreshes instead of overwriting on conflict', async () => {
    const { result } = await loaded([row(1, 1)])
    updateHomework.mockResolvedValueOnce(row(1, 2, { done: true }))
    await act(async () => { await result.current.toggleHomework(1) })
    expect(updateHomework).toHaveBeenLastCalledWith(1, { done: true, version: 1 })
    expect(result.current.homeworks[0]).toMatchObject({ done: true, version: 2 })

    updateHomework.mockRejectedValueOnce(conflict())
    homeworks.mockResolvedValue({ content: [row(1, 4, { done: true, title: '학생이 끝낸 숙제' })] })
    await act(async () => { await result.current.toggleHomework(1) })
    expect(updateHomework).toHaveBeenLastCalledWith(1, { done: false, version: 2 })
    expect(result.current.homeworks[0]).toMatchObject({ done: true, version: 4 })
    expect(result.current.mutationError).toContain('최신 상태를 불러왔으니')
  })

})

describe('useTeacherData homework deletion (version)', () => {
  it('deletes with the loaded version', async () => {
    const { result } = await loaded([row(1, 3), row(2, 0)])
    deleteHomework.mockResolvedValue(undefined)
    await act(async () => { expect(await result.current.deleteHomework(1)).toBe(true) })
    expect(deleteHomework).toHaveBeenCalledWith(1, 3)
    expect(result.current.homeworks.map((item) => item.id)).toEqual([2])
    expect(result.current.successMessage).toBe('숙제를 삭제했어요.')
  })

  it('does not remove a homework changed elsewhere: reloads it and explains', async () => {
    const { result } = await loaded([row(1, 0)])
    deleteHomework.mockRejectedValue(conflict())
    homeworks.mockResolvedValue({ content: [row(1, 1, { title: '다른 곳에서 고친 숙제' })] })
    await act(async () => { expect(await result.current.deleteHomework(1)).toBe(true) }) // 확인 창은 닫고 최신 목록을 보여준다
    expect(result.current.homeworks[0]).toMatchObject({ id: 1, title: '다른 곳에서 고친 숙제', version: 1 })
    expect(result.current.mutationError).toContain('삭제하지 않았어요')
  })

  it('keeps the dialog open on other failures', async () => {
    const { result } = await loaded([row(1, 0)])
    deleteHomework.mockRejectedValue(new ApiError('숙제를 찾을 수 없습니다.', 404))
    await act(async () => { expect(await result.current.deleteHomework(1)).toBe(false) })
    expect(result.current.mutationError).toBe('숙제를 찾을 수 없습니다.')
  })
})
