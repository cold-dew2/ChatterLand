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

/**
 * 진행 중인 토큰 갱신. 서버는 refresh 토큰을 한 번만 회전시키므로, 동시에 여러 요청이 401을 받아도
 * 갱신은 한 번만 하고 모두 그 결과를 기다린다(각자 갱신하면 두 번째가 실패해 로그아웃된다).
 */
type RefreshResult = 'refreshed' | 'rejected' | 'network-error'
let refreshing: Promise<RefreshResult> | null = null

function refreshSession(): Promise<RefreshResult> {
  if (refreshing) return refreshing
  const refreshToken = window.sessionStorage.getItem('chatterland.refreshToken')
  if (!refreshToken) return Promise.resolve('rejected')
  refreshing = (async () => {
    try {
      const refreshed = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }), credentials: 'include', cache: 'no-store' })
      if (refreshed.ok) {
        const body = await refreshed.json() as { accessToken?: string; refreshToken?: string }
        if (body.accessToken && body.refreshToken) { saveAuthTokens(body.accessToken, body.refreshToken); return 'refreshed' }
      }
      clearAuth()
      return 'rejected'
    } catch {
      // 네트워크 오류는 세션 만료가 아니다. 토큰을 지우지 않고 연결 오류로 알린다.
      return 'network-error'
    } finally {
      refreshing = null
    }
  })()
  return refreshing
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
    // 이미 다른 요청이 갱신한 토큰이 있으면(요청 보낸 뒤 바뀜) 갱신 없이 그 토큰으로 다시 시도한다.
    const latestToken = window.sessionStorage.getItem('chatterland.accessToken')
    if (latestToken && latestToken !== accessToken) return request<T>(path, init, true)
    const refresh = await refreshSession()
    if (refresh === 'refreshed') return request<T>(path, init, true)
    if (refresh === 'network-error') throw new ApiError('서버에 연결할 수 없어요. 네트워크 연결을 확인한 뒤 다시 시도해 주세요.', 0, 'NETWORK_ERROR')
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
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) => request<T>(path, { ...json('POST', body), headers }),
  put: <T>(path: string, body: unknown) => request<T>(path, json('PUT', body)),
  patch: <T>(path: string, body: unknown) => request<T>(path, json('PATCH', body)),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, body: FormData, headers?: Record<string, string>) => request<T>(path, { method: 'POST', body, headers }),
}
