const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateEmail(value: string) {
  if (!value.trim()) return '이메일을 입력해 주세요.'
  return EMAIL_PATTERN.test(value.trim()) ? undefined : '올바른 이메일 주소를 입력해 주세요.'
}

/** 서버 SignupRequest 규칙(8~72자)과 동일하다. */
export function validatePassword(value: string) {
  if (!value) return '비밀번호를 입력해 주세요.'
  if (value.length < 8) return '비밀번호를 8자 이상 입력해 주세요.'
  if (value.length > 72) return '비밀번호는 72자 이하로 입력해 주세요.'
  return undefined
}

export function validateName(value: string) {
  if (!value.trim()) return '이름을 입력해 주세요.'
  return value.trim().length > 80 ? '이름은 80자 이하로 입력해 주세요.' : undefined
}
