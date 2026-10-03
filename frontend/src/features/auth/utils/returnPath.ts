/**
 * 로그인 후 돌아갈 경로를 검증한다. 외부 주소로 보내는 열린 리다이렉트를 막기 위해
 * 같은 사이트의 상대 경로이면서 로그인한 역할의 화면(/student, /teacher)만 허용한다.
 */
export function safeReturnPath(value: string | null | undefined, role: 'STUDENT' | 'TEACHER'): string | null {
  if (!value || value.length > 300) return null
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\') || /[\u0000-\u001f]/.test(value)) return null
  const prefix = role === 'TEACHER' ? '/teacher' : '/student'
  const pathname = value.split(/[?#]/)[0]
  return pathname === prefix || pathname.startsWith(`${prefix}/`) ? value : null
}
