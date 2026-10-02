export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'

export type ApiErrorBody = { code?: string; message?: string }

export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly code?: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export const SESSION_EXPIRED_EVENT = 'chatterland:session-expired'

/** 사용자에게 보여줄 오류 문구. 알 수 없는 오류는 fallback을 사용한다. */
export function errorMessage(cause: unknown, fallback: string) {
  return cause instanceof Error && cause.message ? cause.message : fallback
}

export function saveAccessToken(value: string) { window.sessionStorage.setItem('chatterland.accessToken', value) }
export function saveAuthTokens(accessToken: string, refreshToken: string) {
  window.sessionStorage.setItem('chatterland.accessToken', accessToken)
  window.sessionStorage.setItem('chatterland.refreshToken', refreshToken)
}
export function clearAuth() {
  if (typeof window === 'undefined') return
  window.sessionStorage.removeItem('chatterland.accessToken')
  window.sessionStorage.removeItem('chatterland.refreshToken')
}

async function request<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const headers = new Headers(init.headers)
  if (!(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const accessToken = typeof window === 'undefined' ? null : window.sessionStorage.getItem('chatterland.accessToken')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers, credentials: 'include', cache: 'no-store' })
  } catch {
    throw new ApiError('서버에 연결할 수 없어요. 네트워크 연결을 확인한 뒤 다시 시도해 주세요.', 0, 'NETWORK_ERROR')
  }
  const publicAuthPath = path.startsWith('/api/v1/auth/') && !path.endsWith('/auth/me')
  if (response.status === 401 && !retried && typeof window !== 'undefined' && !publicAuthPath) {
    const refreshToken = window.sessionStorage.getItem('chatterland.refreshToken')
    if (refreshToken) {
      const refreshed = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }), credentials: 'include', cache: 'no-store' })
      if (refreshed.ok) {
        const body = await refreshed.json() as { accessToken?: string; refreshToken?: string }
        if (body.accessToken && body.refreshToken) { saveAuthTokens(body.accessToken, body.refreshToken); return request<T>(path, init, true) }
      }
      clearAuth()
    }
    // /auth/me 실패는 RoleLayout이 처리한다(로그인하지 않은 방문자에게 '세션 만료'를 띄우지 않기 위함).
    if (!path.startsWith('/api/v1/auth/')) {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
      throw new ApiError('로그인 시간이 만료되었어요. 다시 로그인해 주세요.', 401, 'SESSION_EXPIRED')
    }
  }
  if (response.status === 204) return undefined as T
  const contentType = response.headers.get('content-type') ?? ''
  if (!response.ok) {
    const body = contentType.includes('json') ? await response.json() as ApiErrorBody : { message: await response.text() }
    throw new ApiError(body.message ?? `요청에 실패했습니다. (${response.status})`, response.status, body.code)
  }
  if (contentType.includes('application/pdf') || contentType.startsWith('audio/')) return await response.blob() as T
  return await response.json() as T
}

export const queryString = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) if (value !== undefined && value !== '') search.set(key, String(value))
  const value = search.toString()
  return value ? `?${value}` : ''
}
const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) })
export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, json('POST', body)),
  put: <T>(path: string, body: unknown) => request<T>(path, json('PUT', body)),
  patch: <T>(path: string, body: unknown) => request<T>(path, json('PATCH', body)),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, body: FormData) => request<T>(path, { method: 'POST', body }),
}
