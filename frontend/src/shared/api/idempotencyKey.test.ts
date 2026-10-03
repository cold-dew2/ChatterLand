import { afterEach, describe, expect, it, vi } from 'vitest'
import { newIdempotencyKey } from '@/shared/api/idempotencyKey'

const SERVER_FORMAT = /^[A-Za-z0-9_-]{8,64}$/

afterEach(() => vi.unstubAllGlobals())

describe('newIdempotencyKey', () => {
  it('uses crypto.randomUUID when available and matches the server format', () => {
    const keys = new Set(Array.from({ length: 50 }, () => newIdempotencyKey()))
    expect(keys.size).toBe(50)
    for (const key of keys) expect(key).toMatch(SERVER_FORMAT)
  })

  it('falls back to getRandomValues where randomUUID is missing (plain HTTP page)', () => {
    const real = globalThis.crypto
    vi.stubGlobal('crypto', { getRandomValues: real.getRandomValues.bind(real) })
    const key = newIdempotencyKey()
    expect(key).toMatch(/^[0-9a-f]{32}$/)
    expect(key).toMatch(SERVER_FORMAT)
    expect(newIdempotencyKey()).not.toBe(key)
  })
})
