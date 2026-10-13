import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { ApiError } from '@/shared/api/client'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const me = vi.fn()
const updateProfile = vi.fn()
const mine = vi.fn()
const notificationSettings = vi.fn()
const changePassword = vi.fn()
vi.mock('@/features/auth/api/authApi', () => ({ authApi: { me: () => me(), changePassword: (...a: unknown[]) => changePassword(...a) } }))
vi.mock('@/features/account/api/accountApi', () => ({
  DEFAULT_NOTIFICATION_SETTINGS: { homeworkAssigned: true, homeworkChanged: true, homeworkDueSoon: true, homeworkOverdue: true },
  accountApi: { updateProfile: (...a: unknown[]) => updateProfile(...a), notificationSettings: () => notificationSettings(), saveNotificationSettings: vi.fn() },
}))
vi.mock('@/features/consent/api/consentApi', () => ({ consentApi: { mine: () => mine(), update: vi.fn() } }))

import StudentConsentScreen from '@/features/student/components/StudentConsentScreen'
import StudentMyPageScreen from '@/features/student/components/StudentMyPageScreen'
import StudentProfileEditScreen from '@/features/student/components/StudentProfileEditScreen'

const student = { name: '김하늘', grade: '학습 중', averageMatchRate: null, totalAttempts: 0, sessionsTotal: 0, sessionsDone: 0, streak: 0, age: 8 }

beforeEach(() => {
  me.mockReset(); updateProfile.mockReset(); mine.mockReset(); notificationSettings.mockReset(); changePassword.mockReset()
  notificationSettings.mockResolvedValue({ homeworkAssigned: true, homeworkChanged: false, homeworkDueSoon: true, homeworkOverdue: true })
  me.mockResolvedValue({ userId: 1, role: 'STUDENT', name: '김하늘', email: 'sky@example.test', centerName: '해솔 언어발달센터' })
  mine.mockResolvedValue([{ type: 'AI_FEEDBACK', required: false, agreed: false, currentVersion: true }])
})

describe('마이페이지', () => {
  it('담당 선생님이 있으면 센터와 선생님을, 없으면 개인 학습을 보여 준다(실제 정보만)', async () => {
    const { unmount } = render(<StudentMyPageScreen student={{ ...student, teachers: ['이선생'] }} loggingOut={false} onLogout={vi.fn()} />)
    expect((await screen.findAllByText('해솔 언어발달센터')).length).toBeGreaterThan(0)
    expect(screen.getByText('담당 선생님 이선생')).toBeTruthy()
    unmount()
    render(<StudentMyPageScreen student={{ ...student, teachers: [] }} loggingOut={false} onLogout={vi.fn()} />)
    expect(await screen.findByText('개인 학습')).toBeTruthy()
    expect(screen.queryByText(/담당 선생님/)).toBeNull()
  })

  it('내 정보 수정·동의 관리는 별도 화면으로 이동하고, 마이페이지에는 비밀번호 변경 폼이 없다', async () => {
    const onOpenProfile = vi.fn()
    const onOpenConsents = vi.fn()
    render(<StudentMyPageScreen student={student} loggingOut={false} onLogout={vi.fn()} onOpenProfile={onOpenProfile} onOpenConsents={onOpenConsents} />)
    await screen.findByText(/해솔 언어발달센터/)
    fireEvent.click(screen.getByRole('button', { name: /내 정보 수정/ }))
    fireEvent.click(screen.getByRole('button', { name: /동의 관리/ }))
    expect(onOpenProfile).toHaveBeenCalledOnce()
    expect(onOpenConsents).toHaveBeenCalledOnce()
    expect(screen.queryByLabelText('이름')).toBeNull()
    expect(screen.queryByRole('region', { name: '동의 관리' })).toBeNull()
    expect(screen.queryByRole('button', { name: '비밀번호 변경' })).toBeNull()
    expect(screen.queryByLabelText(/현재 비밀번호/)).toBeNull()
  })
})

describe('알림 설정', () => {
  it('지금 동작하는 숙제 알림만 켜고 끌 수 있고, 메시지·피드백·푸시 알림은 준비 중으로만 보여 준다', async () => {
    render(<StudentMyPageScreen student={student} loggingOut={false} onLogout={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: /알림 설정/ }))
    const dialog = await screen.findByRole('dialog', { name: '알림 설정' })
    const switches = await within(dialog).findAllByRole('switch')
    expect(switches).toHaveLength(4)
    expect((within(dialog).getByRole('switch', { name: /숙제 내용 변경/ }) as HTMLInputElement).checked).toBe(false)
    const upcoming = within(dialog).getByRole('region', { name: '준비 중인 알림' })
    for (const label of ['선생님 메시지 알림', '학습 피드백 알림', '휴대폰 푸시 알림']) expect(within(upcoming).getByText(label)).toBeTruthy()
    expect(within(upcoming).queryAllByRole('switch')).toHaveLength(0)
    expect(within(upcoming).getAllByText('준비 중')).toHaveLength(3)
  })
})

describe('내 정보 수정 화면', () => {
  it('이름만 바꿀 수 있고, 이메일·나이·센터는 보기만 한다(생년월일 입력 없음)', async () => {
    const onProfileChanged = vi.fn()
    updateProfile.mockResolvedValue({ userId: 1, role: 'STUDENT', name: '김바다', email: 'sky@example.test' })
    render(<StudentProfileEditScreen age={8} onBack={vi.fn()} onProfileChanged={onProfileChanged} />)
    await screen.findByText('sky@example.test')
    expect(screen.getAllByRole('textbox')).toHaveLength(1)
    expect(screen.queryByLabelText(/생년월일|이메일/)).toBeNull()
    expect(screen.getByText('8세')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '김바다' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '이름 저장' })) })
    expect(updateProfile).toHaveBeenCalledWith({ name: '김바다' })
    expect(screen.getByText('이름을 바꿨어요.')).toBeTruthy()
    expect(onProfileChanged).toHaveBeenCalledOnce()
  })

  it('비밀번호 변경 폼이 이 화면에 있고, 이름 저장과 비밀번호 변경은 서로 따로 저장된다', async () => {
    updateProfile.mockResolvedValue({ userId: 1, role: 'STUDENT', name: '김바다', email: 'sky@example.test' })
    changePassword.mockRejectedValue(new ApiError('현재 비밀번호가 올바르지 않아요.', 400, 'INVALID_CURRENT_PASSWORD'))
    render(<StudentProfileEditScreen age={8} onBack={vi.fn()} />)
    await screen.findByText('sky@example.test')
    const section = screen.getByRole('region', { name: '비밀번호 변경' })
    // 이름만 저장: 비밀번호 API는 부르지 않는다
    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '김바다' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '이름 저장' })) })
    expect(updateProfile).toHaveBeenCalledOnce()
    expect(changePassword).not.toHaveBeenCalled()
    // 비밀번호만 저장: 이름 API는 다시 부르지 않고, 실패해도 바꾼 이름은 그대로다
    fireEvent.change(within(section).getByLabelText(/현재 비밀번호/), { target: { value: 'Wrong-Pass!000' } })
    fireEvent.change(within(section).getByLabelText(/^새 비밀번호(\s*\*)?$/), { target: { value: 'Changed-Pass!789' } })
    fireEvent.change(within(section).getByLabelText(/새 비밀번호 확인/), { target: { value: 'Changed-Pass!789' } })
    await act(async () => { fireEvent.click(within(section).getByRole('button', { name: '비밀번호 변경' })) })
    expect(changePassword).toHaveBeenCalledOnce()
    expect(updateProfile).toHaveBeenCalledOnce()
    expect(within(section).getByText(/현재 비밀번호가 올바르지 않아요/)).toBeTruthy()
    expect((screen.getByLabelText('이름') as HTMLInputElement).value).toBe('김바다')
  })

  it('뒤로 가기는 마이페이지로 돌아간다', async () => {
    const onBack = vi.fn()
    render(<StudentProfileEditScreen age={8} onBack={onBack} />)
    await screen.findByText('sky@example.test')
    fireEvent.click(screen.getByRole('button', { name: '마이페이지로 돌아가기' }))
    expect(onBack).toHaveBeenCalledOnce()
  })
})

describe('동의 관리 화면', () => {
  it('저장된 동의 상태를 보여 주고, AI 동의 창의 안내 내용은 같은 창 안에서 펼치고 접는다(팝업을 겹쳐 띄우지 않음)', async () => {
    render(<StudentConsentScreen onBack={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: '동의' }))
    const dialog = screen.getByRole('dialog', { name: /AI 학습 피드백/ })
    const toggle = within(dialog).getByRole('button', { name: '안내 내용 보기' })
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    fireEvent.click(toggle)
    expect(toggle.getAttribute('aria-expanded')).toBe('true')
    expect(within(dialog).getByText(/안내문 버전/)).toBeTruthy()
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    fireEvent.click(toggle)
    expect(within(dialog).queryByText(/안내문 버전/)).toBeNull()
  })
})
