import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiClient, errorMessage, queryString, SESSION_EXPIRED_EVENT } from '@/shared/api/client'

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })

afterEach(() => vi.unstubAllGlobals())

describe('apiClient', () => {
  it('sends the bearer token and parses JSON', async () => {
    window.sessionStorage.setItem('chatterland.accessToken', 'access-1')
    const fetchMock = vi.fn().mockResolvedValue(json(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(apiClient.get('/api/v1/students/me')).resolves.toEqual({ ok: true })
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer access-1')
  })

  it('turns server error bodies into ApiError with status and code', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(403, { code: 'CONSENT_REQUIRED', message: '동의가 필요합니다.' })))
    const error = await apiClient.post('/api/v1/speech/analyze').catch((cause: unknown) => cause)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 403, code: 'CONSENT_REQUIRED', message: '동의가 필요합니다.' })
  })

  it('reports network failures as NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(apiClient.get('/api/v1/centers')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('refreshes once on 401 and retries with the new token', async () => {
    window.sessionStorage.setItem('chatterland.accessToken', 'old')
    window.sessionStorage.setItem('chatterland.refreshToken', 'refresh-1')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json(401, { message: 'expired' }))
      .mockResolvedValueOnce(json(200, { accessToken: 'new', refreshToken: 'refresh-2' }))
      .mockResolvedValueOnce(json(200, { name: '학생' }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(apiClient.get('/api/v1/students/me')).resolves.toEqual({ name: '학생' })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(window.sessionStorage.getItem('chatterland.accessToken')).toBe('new')
    expect(new Headers(fetchMock.mock.calls[2][1].headers).get('Authorization')).toBe('Bearer new')
  })

  it('clears tokens and emits session-expired when refresh fails', async () => {
    window.sessionStorage.setItem('chatterland.accessToken', 'old')
    window.sessionStorage.setItem('chatterland.refreshToken', 'bad')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(401, {})).mockResolvedValueOnce(json(401, {})))
    const listener = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, listener)
    await expect(apiClient.get('/api/v1/teachers/me/students')).rejects.toMatchObject({ status: 401, code: 'SESSION_EXPIRED' })
    window.removeEventListener(SESSION_EXPIRED_EVENT, listener)
    expect(listener).toHaveBeenCalledOnce()
    expect(window.sessionStorage.getItem('chatterland.refreshToken')).toBeNull()
  })

  it('does not treat a failed login (public auth path) as an expired session', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { message: '이메일 또는 비밀번호가 올바르지 않습니다.' })))
    const listener = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, listener)
    await expect(apiClient.post('/api/v1/auth/login', {})).rejects.toMatchObject({ status: 401, message: '이메일 또는 비밀번호가 올바르지 않습니다.' })
    window.removeEventListener(SESSION_EXPIRED_EVENT, listener)
    expect(listener).not.toHaveBeenCalled()
  })

  it('returns audio and PDF responses as Blob, and 204 as undefined', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(new Uint8Array([1, 2]), { status: 200, headers: { 'content-type': 'audio/wav' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 })))
    expect(await apiClient.get('/api/v1/teachers/me/speech-analyses/x/audio')).toBeInstanceOf(Blob)
    expect(await apiClient.delete('/api/v1/teachers/me/homeworks/1')).toBeUndefined()
  })
})

describe('apiClient concurrent 401 (refresh token is single-use on the server)', () => {
  it('shares one refresh for simultaneous 401s and keeps the session', async () => {
    window.sessionStorage.setItem('chatterland.accessToken', 'old')
    window.sessionStorage.setItem('chatterland.refreshToken', 'refresh-1')
    let refreshCalls = 0
    const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
      if (url.endsWith('/api/v1/auth/refresh')) {
        refreshCalls += 1
        // 서버는 refresh 토큰을 한 번만 회전시킨다. 같은 토큰으로 두 번째 갱신하면 401.
        const body = JSON.parse(String(init.body)) as { refreshToken: string }
        return body.refreshToken === 'refresh-1' && refreshCalls === 1
          ? json(200, { accessToken: 'new', refreshToken: 'refresh-2' }) : json(401, { message: 'invalid refresh' })
      }
      const auth = new Headers(init.headers).get('Authorization')
      return auth === 'Bearer new' ? json(200, { ok: url }) : json(401, { message: 'expired' })
    })
    vi.stubGlobal('fetch', fetchMock)
    const listener = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, listener)
    const results = await Promise.all([apiClient.get('/api/v1/students/me'), apiClient.get('/api/v1/students/me/homeworks'), apiClient.get('/api/v1/students/me/history')])
    window.removeEventListener(SESSION_EXPIRED_EVENT, listener)
    expect(results).toHaveLength(3)
    expect(refreshCalls).toBe(1)
    expect(listener).not.toHaveBeenCalled()
    expect(window.sessionStorage.getItem('chatterland.refreshToken')).toBe('refresh-2')
  })
})

describe('apiClient refresh edge cases', () => {
  it('a network error while refreshing keeps the session and reports NETWORK_ERROR', async () => {
    window.sessionStorage.setItem('chatterland.accessToken', 'old')
    window.sessionStorage.setItem('chatterland.refreshToken', 'refresh-1')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(401, {})).mockRejectedValueOnce(new TypeError('Failed to fetch')))
    const listener = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, listener)
    await expect(apiClient.get('/api/v1/students/me')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    window.removeEventListener(SESSION_EXPIRED_EVENT, listener)
    expect(listener).not.toHaveBeenCalled()
    expect(window.sessionStorage.getItem('chatterland.refreshToken')).toBe('refresh-1')
  })

  it('a later 401 after another request already refreshed retries with the new token without refreshing again', async () => {
    window.sessionStorage.setItem('chatterland.accessToken', 'old')
    window.sessionStorage.setItem('chatterland.refreshToken', 'refresh-2')
    const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
      if (url.endsWith('/auth/refresh')) return json(500, {})
      const auth = new Headers(init.headers).get('Authorization')
      if (auth === 'Bearer old') { window.sessionStorage.setItem('chatterland.accessToken', 'new'); return json(401, {}) }
      return json(200, { ok: true })
    })
    vi.stubGlobal('fetch', fetchMock)
    await expect(apiClient.get('/api/v1/students/me')).resolves.toEqual({ ok: true })
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/refresh'))).toBe(false)
  })
})

describe('helpers', () => {
  it('builds query strings without empty values', () => {
    expect(queryString({ page: 0, size: 20, q: '', status: undefined })).toBe('?page=0&size=20')
    expect(queryString({})).toBe('')
  })
  it('uses fallback text for unknown errors', () => {
    expect(errorMessage(new Error('서버 오류'), '실패')).toBe('서버 오류')
    expect(errorMessage('x', '실패')).toBe('실패')
  })
})
