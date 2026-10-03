import { describe, expect, it } from 'vitest'
import { safeReturnPath } from '@/features/auth/utils/returnPath'

describe('safeReturnPath (열린 리다이렉트 방지)', () => {
  it('allows in-app paths of the signed-in role, with query', () => {
    expect(safeReturnPath('/student/history', 'STUDENT')).toBe('/student/history')
    expect(safeReturnPath('/student', 'STUDENT')).toBe('/student')
    expect(safeReturnPath('/teacher/students/7?tab=voice', 'TEACHER')).toBe('/teacher/students/7?tab=voice')
  })
  it('rejects external, protocol-relative, backslash and other-role paths', () => {
    for (const bad of ['https://evil.example', '//evil.example/student', '/\\evil.example', 'javascript:alert(1)', 'student/history', '/studentx', '/login', '', undefined, null, `/student/${'a'.repeat(400)}`, '/student\n/x'])
      expect(safeReturnPath(bad, 'STUDENT')).toBeNull()
    expect(safeReturnPath('/teacher/students', 'STUDENT')).toBeNull()
    expect(safeReturnPath('/student/history', 'TEACHER')).toBeNull()
  })
})
