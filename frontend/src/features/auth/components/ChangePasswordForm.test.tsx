import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/client'

const changePassword = vi.fn()
vi.mock('@/features/auth/api/authApi', () => ({ authApi: { changePassword: (...a: unknown[]) => changePassword(...a) } }))

import ChangePasswordForm from '@/features/auth/components/ChangePasswordForm'

const fill = (current: string, next: string, confirm: string) => {
  fireEvent.change(screen.getByLabelText(/현재 비밀번호/), { target: { value: current } })
  fireEvent.change(screen.getByLabelText(/^새 비밀번호(\s*\*)?$/), { target: { value: next } })
  fireEvent.change(screen.getByLabelText(/새 비밀번호 확인/), { target: { value: confirm } })
}
const submit = () => fireEvent.click(screen.getByRole('button', { name: '비밀번호 변경' }))

beforeEach(() => {
  changePassword.mockReset()
  window.sessionStorage.setItem('chatterland.accessToken', 'old-access')
  window.sessionStorage.setItem('chatterland.refreshToken', 'old-refresh')
})

describe('ChangePasswordForm', () => {
  it('validates input on the screen before calling the server', () => {
    render(<ChangePasswordForm />)
    submit()
    expect(screen.getByText('현재 비밀번호를 입력해 주세요.')).toBeTruthy()
    fill('Current!123', 'short', 'short')
    submit()
    expect(screen.getByText('비밀번호를 8자 이상 입력해 주세요.')).toBeTruthy()
    fill('Current!123', 'Current!123', 'Current!123')
    submit()
    expect(screen.getByText('현재 비밀번호와 다른 새 비밀번호를 입력해 주세요.')).toBeTruthy()
    fill('Current!123', 'Changed!456', 'Changed!457')
    submit()
    expect(screen.getByText('새 비밀번호가 일치하지 않아요.')).toBeTruthy()
    expect(changePassword).not.toHaveBeenCalled()
  })

  it('saves the new tokens for this device, clears the fields and reports success once', async () => {
    let finish: (value: unknown) => void = () => undefined
    changePassword.mockReturnValue(new Promise((resolve) => { finish = resolve }))
    const onChanged = vi.fn()
    render(<ChangePasswordForm onChanged={onChanged} />)
    fill('Current!123', 'Changed!456', 'Changed!456')
    submit()
    expect(screen.getByRole('button', { name: /변경 중/ })).toHaveProperty('disabled', true)
    fireEvent.submit(screen.getByRole('button', { name: /변경 중/ }).closest('form')!) // 처리 중 두 번째 제출(Enter 등)은 무시
    expect(changePassword).toHaveBeenCalledTimes(1)
    expect(changePassword).toHaveBeenCalledWith({ currentPassword: 'Current!123', newPassword: 'Changed!456', newPasswordConfirm: 'Changed!456' })
    finish({ accessToken: 'new-access', refreshToken: 'new-refresh' })
    expect(await screen.findByText(/비밀번호를 변경했어요/)).toBeTruthy()
    expect(window.sessionStorage.getItem('chatterland.accessToken')).toBe('new-access')
    expect(window.sessionStorage.getItem('chatterland.refreshToken')).toBe('new-refresh')
    expect((screen.getByLabelText(/현재 비밀번호/) as HTMLInputElement).value).toBe('')
    expect(onChanged).toHaveBeenCalledTimes(1)
  })

  it('shows the server reason and keeps the current session when the current password is wrong', async () => {
    changePassword.mockRejectedValue(new ApiError('현재 비밀번호가 올바르지 않아요.', 400))
    render(<ChangePasswordForm />)
    fill('Wrong!1234', 'Changed!456', 'Changed!456')
    submit()
    expect(await screen.findByText('현재 비밀번호가 올바르지 않아요.')).toBeTruthy()
    await waitFor(() => expect(screen.getByRole('button', { name: '비밀번호 변경' })).toBeTruthy())
    expect(window.sessionStorage.getItem('chatterland.accessToken')).toBe('old-access')
    expect(screen.queryByText(/비밀번호를 변경했어요/)).toBeNull()
  })

  it('explains the temporary lock after too many wrong attempts without logging out', async () => {
    changePassword.mockRejectedValue(new ApiError('현재 비밀번호를 여러 번 틀렸어요. 15분 뒤에 다시 시도해 주세요.', 429))
    render(<ChangePasswordForm />)
    fill('Wrong!1234', 'Changed!456', 'Changed!456')
    submit()
    expect(await screen.findByText('현재 비밀번호를 여러 번 틀렸어요. 15분 뒤에 다시 시도해 주세요.')).toBeTruthy()
    expect(window.sessionStorage.getItem('chatterland.accessToken')).toBe('old-access')
  })
})
