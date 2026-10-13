import type { UserRole } from '@/features/auth/api/authApi'
import { paths } from '@/routes/path/paths'

/** 역할별 홈 */
export function homePathFor(role: string): string {
  return role === 'TEACHER' ? paths.teacher.home : role === 'GUARDIAN' ? paths.guardian.home : paths.student.home
}

/**
 * 로그인 후 돌아갈 경로를 검증한다. 외부 주소로 보내는 열린 리다이렉트를 막기 위해
 * 같은 사이트의 상대 경로이면서 로그인한 역할의 화면(/student, /teacher, /guardian)만 허용한다.
 */
export function safeReturnPath(value: string | null | undefined, role: UserRole): string | null {
  if (!value || value.length > 300) return null
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\') || /[\u0000-\u001f]/.test(value)) return null
  const prefix = homePathFor(role)
  const pathname = value.split(/[?#]/)[0]
  return pathname === prefix || pathname.startsWith(`${prefix}/`) ? value : null
}
