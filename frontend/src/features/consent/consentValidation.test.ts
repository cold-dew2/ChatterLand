import { describe, expect, it } from 'vitest'
import { emptySignupConsents, validateSignupConsents } from '@/features/consent/components/SignupConsentFields'
import { guardianRequired } from '@/features/consent/consentPolicy'

describe('signup consent rules', () => {
  it('requires privacy consent, and guardian fields only when a guardian is needed', () => {
    expect(validateSignupConsents(emptySignupConsents, false)).toMatchObject({ privacy: expect.any(String), guardianName: undefined })
    const errors = validateSignupConsents({ ...emptySignupConsents, privacy: true }, true)
    expect(errors.privacy).toBeUndefined()
    expect(errors.guardianName).toBe('보호자 이름을 입력해 주세요.')
    expect(errors.guardianRelation).toBe('아동과의 관계를 입력해 주세요.')
    expect(errors.guardianConfirmed).toBeTruthy()
    expect(Object.values(validateSignupConsents({ ...emptySignupConsents, privacy: true, guardianConfirmed: true, guardianName: '김보호', guardianRelation: '부모' }, true)).filter(Boolean)).toEqual([])
  })
  it('asks guardian consent for students under 14 or with unknown age', () => {
    expect(guardianRequired('student', 13)).toBe(true)
    expect(guardianRequired('student', null)).toBe(true)
    expect(guardianRequired('student', 14)).toBe(false)
    expect(guardianRequired('teacher', 8)).toBe(false)
  })
})
