import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useAudioRecorder } from '@/features/student/hooks/useAudioRecorder'

const setMedia = (getUserMedia: unknown) => Object.defineProperty(navigator, 'mediaDevices', { value: getUserMedia ? { getUserMedia } : undefined, configurable: true })

afterEach(() => { vi.unstubAllGlobals(); setMedia(undefined) })

describe('useAudioRecorder', () => {
  it('reports unsupported browsers without starting', async () => {
    setMedia(undefined)
    const { result } = renderHook(() => useAudioRecorder())
    await act(() => result.current.start())
    expect(result.current.status).toBe('idle')
    expect(result.current.error).toContain('음성 녹음을 지원하지 않아요')
  })

  it('explains a denied microphone permission and allows retry', async () => {
    vi.stubGlobal('MediaRecorder', class {})
    setMedia(vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError')))
    const { result } = renderHook(() => useAudioRecorder())
    await act(() => result.current.start())
    expect(result.current.status).toBe('idle')
    expect(result.current.error).toContain('마이크 권한이 거부되었어요')
  })

  it('explains a missing microphone', async () => {
    vi.stubGlobal('MediaRecorder', class {})
    setMedia(vi.fn().mockRejectedValue(new DOMException('none', 'NotFoundError')))
    const { result } = renderHook(() => useAudioRecorder())
    await act(() => result.current.start())
    expect(result.current.error).toContain('마이크를 찾지 못했어요')
  })

  it('records, stops into a previewable blob, and resets for re-recording', async () => {
    const track = { stop: vi.fn() }
    class FakeRecorder {
      state = 'inactive'; mimeType = 'audio/webm'
      ondataavailable: ((e: { data: Blob }) => void) | null = null
      onstop: (() => void) | null = null
      start() { this.state = 'recording' }
      stop() { this.state = 'inactive'; this.ondataavailable?.({ data: new Blob(['x'], { type: 'audio/webm' }) }); this.onstop?.() }
    }
    vi.stubGlobal('MediaRecorder', FakeRecorder)
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:preview'), revokeObjectURL: vi.fn() }))
    setMedia(vi.fn().mockResolvedValue({ getTracks: () => [track] }))
    const { result } = renderHook(() => useAudioRecorder())
    await act(() => result.current.start())
    expect(result.current.status).toBe('recording')
    act(() => result.current.stop())
    expect(result.current.status).toBe('recorded')
    expect(result.current.previewUrl).toBe('blob:preview')
    expect(track.stop).toHaveBeenCalled()
    act(() => result.current.reset())
    expect(result.current.status).toBe('idle')
    expect(result.current.recording).toBeNull()
  })
})
