import { describe, expect, it } from 'vitest'
import { validateEmail, validateName, validatePassword } from '@/features/auth/utils/validation'

describe('validateEmail', () => {
  it('rejects blank and malformed addresses', () => {
    expect(validateEmail('')).toBe('이메일을 입력해 주세요.')
    expect(validateEmail('   ')).toBe('이메일을 입력해 주세요.')
    expect(validateEmail('wrong-email')).toBe('올바른 이메일 주소를 입력해 주세요.')
    expect(validateEmail('a@b')).toBe('올바른 이메일 주소를 입력해 주세요.')
  })
  it('accepts a valid address with surrounding spaces', () => {
    expect(validateEmail(' child@example.test ')).toBeUndefined()
  })
})

describe('validatePassword (server rule 8~72 chars)', () => {
  it('enforces boundaries', () => {
    expect(validatePassword('')).toBe('비밀번호를 입력해 주세요.')
    expect(validatePassword('1234567')).toBe('비밀번호를 8자 이상 입력해 주세요.')
    expect(validatePassword('12345678')).toBeUndefined()
    expect(validatePassword('x'.repeat(72))).toBeUndefined()
    expect(validatePassword('x'.repeat(73))).toBe('비밀번호는 72자 이하로 입력해 주세요.')
  })
})

describe('validateName', () => {
  it('requires 1~80 chars', () => {
    expect(validateName(' ')).toBe('이름을 입력해 주세요.')
    expect(validateName('가'.repeat(80))).toBeUndefined()
    expect(validateName('가'.repeat(81))).toBe('이름은 80자 이하로 입력해 주세요.')
  })
})
