import { apiClient } from '@/shared/api/client'

export type UserRole = 'STUDENT' | 'TEACHER'
export type User = { userId: number; role: UserRole; name: string; email: string; centerId?: number; centerName?: string }
export type AuthResult = { accessToken: string; refreshToken: string; user: User }

export type SignupConsents = {
  privacy: boolean; voice: boolean; aiChat: boolean; policyVersion: string
  guardianConfirmed?: boolean; guardianName?: string; guardianRelation?: string
}

export const authApi = {
  signup: (body: { role: UserRole; name: string; email: string; password: string; centerId: number; termsAgreed: boolean; age?: number; consents: SignupConsents }) => apiClient.post<User>('/api/v1/auth/signup', body),
  findId: (body: { name: string; centerId: number; role: UserRole }) => apiClient.post<{ maskedEmails: string[] }>('/api/v1/auth/find-id', body),
  requestPasswordReset: (email: string) => apiClient.post<{ message: string }>('/api/v1/auth/password-reset/request', { email }),
  verifyResetCode: (email: string, code: string) => apiClient.post<{ resetToken: string; expiresInSeconds: number }>('/api/v1/auth/password-reset/verify', { email, code }),
  confirmPasswordReset: (body: { resetToken: string; newPassword: string; newPasswordConfirm: string }) => apiClient.post<void>('/api/v1/auth/password-reset/confirm', body),
  login: (body: { email: string; password: string }) => apiClient.post<AuthResult>('/api/v1/auth/login', body),
  logout: () => apiClient.post<void>('/api/v1/auth/logout', { refreshToken: typeof window === 'undefined' ? '' : window.sessionStorage.getItem('chatterland.refreshToken') }),
  /** 역할이 맞지 않아 사용하지 않을 토큰을 서버에서 즉시 폐기한다. */
  revoke: (refreshToken: string) => apiClient.post<void>('/api/v1/auth/logout', { refreshToken }),
  refresh: (refreshToken: string) => apiClient.post<{ accessToken: string; refreshToken: string }>('/api/v1/auth/refresh', { refreshToken }),
  me: () => apiClient.get<User>('/api/v1/auth/me'),
  /** 로그인 상태 비밀번호 변경. 다른 기기는 로그아웃되고, 이 기기는 응답의 새 토큰으로 로그인을 유지한다. */
  changePassword: (body: { currentPassword: string; newPassword: string; newPasswordConfirm: string }) =>
    apiClient.patch<{ accessToken: string; refreshToken: string }>('/api/v1/auth/me/password', body),
}
