import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.fn()
const replace = vi.fn()
// 실제 Next 라우터처럼 같은 객체를 돌려준다(렌더링마다 새 객체면 effect가 반복 실행된다).
const router = { push, replace }
vi.mock('next/navigation', () => ({ useRouter: () => router }))
const login = vi.fn()
const revoke = vi.fn()
const me = vi.fn()
vi.mock('@/features/auth/api/authApi', () => ({ authApi: { login: (...args: unknown[]) => login(...args), revoke: (...args: unknown[]) => revoke(...args), me: (...args: unknown[]) => me(...args) } }))

import LoginPage from '@/features/auth/pages/LoginPage'

const fill = (email: string, password: string) => {
  fireEvent.change(document.getElementById('login-email')!, { target: { value: email } })
  fireEvent.change(document.getElementById('login-password')!, { target: { value: password } })
}
const submit = () => fireEvent.submit(document.getElementById('login-email')!.closest('form')!)

beforeEach(() => { push.mockReset(); replace.mockReset(); login.mockReset(); revoke.mockReset(); me.mockReset() })

describe('LoginPage', () => {
  it('shows field errors and does not call the API for invalid input', () => {
    render(<LoginPage initialStep="login" />)
    submit()
    expect(screen.getByText('이메일을 입력해 주세요.')).toBeTruthy()
    expect(screen.getByText('비밀번호를 입력해 주세요.')).toBeTruthy()
    fill('wrong-email', 'pw')
    submit()
    expect(screen.getByText('올바른 이메일 주소를 입력해 주세요.')).toBeTruthy()
    expect(login).not.toHaveBeenCalled()
  })

  it('stores tokens and routes a student to the student home', async () => {
    login.mockResolvedValue({ accessToken: 'a1', refreshToken: 'r1', user: { role: 'STUDENT' } })
    render(<LoginPage initialStep="login" />)
    fill('kid@example.test', 'Chatterland!234')
    submit()
    await waitFor(() => expect(push).toHaveBeenCalledWith('/student'))
    expect(window.sessionStorage.getItem('chatterland.accessToken')).toBe('a1')
  })

  it('rejects a role mismatch and revokes the issued refresh token', async () => {
    login.mockResolvedValue({ accessToken: 'a1', refreshToken: 'r1', user: { role: 'TEACHER' } })
    revoke.mockResolvedValue(undefined)
    render(<LoginPage initialStep="login" />)
    fill('teacher@example.test', 'Chatterland!234')
    submit()
    expect(await screen.findByText(/선택한 학생 계정이 아닙니다/)).toBeTruthy()
    expect(revoke).toHaveBeenCalledWith('r1')
    expect(window.sessionStorage.getItem('chatterland.accessToken')).toBeNull()
    expect(push).not.toHaveBeenCalled()
  })

  it('shows the server error message (wrong password / unknown account)', async () => {
    login.mockRejectedValue(new Error('이메일 또는 비밀번호가 올바르지 않습니다.'))
    render(<LoginPage initialStep="login" />)
    fill('kid@example.test', 'wrong-password')
    submit()
    expect(await screen.findByText('이메일 또는 비밀번호가 올바르지 않습니다.')).toBeTruthy()
  })

  it('ignores a second submit while the first login is in flight', async () => {
    let resolve: (value: unknown) => void = () => undefined
    login.mockReturnValue(new Promise((r) => { resolve = r }))
    render(<LoginPage initialStep="login" />)
    fill('kid@example.test', 'Chatterland!234')
    submit(); submit()
    expect(login).toHaveBeenCalledTimes(1)
    await act(async () => resolve({ accessToken: 'a', refreshToken: 'r', user: { role: 'STUDENT' } }))
  })

  it('explains an expired session', () => {
    render(<LoginPage initialStep="login" sessionExpired />)
    expect(screen.getByText('로그인 시간이 만료되었어요. 다시 로그인해 주세요.')).toBeTruthy()
  })

  it('returns to the safe path after an expired session, and ignores unsafe paths', async () => {
    login.mockResolvedValue({ accessToken: 'a1', refreshToken: 'r1', user: { role: 'STUDENT' } })
    const { unmount } = render(<LoginPage initialStep="login" sessionExpired returnTo="/student/history" />)
    fill('kid@example.test', 'Chatterland!234'); submit()
    await waitFor(() => expect(push).toHaveBeenCalledWith('/student/history'))
    unmount(); push.mockReset(); window.sessionStorage.clear()
    render(<LoginPage initialStep="login" returnTo="https://evil.example/student" />)
    fill('kid@example.test', 'Chatterland!234'); submit()
    await waitFor(() => expect(push).toHaveBeenCalledWith('/student'))
  })

  it('sends an already signed-in user away from the login form without asking again', async () => {
    window.sessionStorage.setItem('chatterland.accessToken', 'valid')
    me.mockResolvedValue({ userId: 1, role: 'TEACHER', name: '선생님', email: 't@example.test' })
    render(<LoginPage initialStep="login" returnTo="/teacher/students/3" />)
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/teacher/students/3'))
    expect(login).not.toHaveBeenCalled()
  })

  it('does not call the server on the login form when there is no saved session', () => {
    render(<LoginPage initialStep="login" />)
    expect(me).not.toHaveBeenCalled()
  })
})
