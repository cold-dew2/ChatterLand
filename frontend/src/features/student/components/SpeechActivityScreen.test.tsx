import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const recorderState = { status: 'recorded', recording: new Blob(['first'], { type: 'audio/webm' }) as Blob | null }
const reset = vi.fn()
vi.mock('@/features/student/hooks/useAudioRecorder', () => ({
  useAudioRecorder: () => ({ ...recorderState, previewUrl: 'blob:preview', elapsed: 2, error: '', maxSeconds: 30, start: vi.fn(), stop: vi.fn(), cancel: vi.fn(), reset }),
}))
vi.mock('@/features/student/utils/wavEncoder', () => ({ toWav16k: (blob: Blob) => Promise.resolve(new Blob([blob], { type: 'audio/wav' })) }))
const analyzeSpeech = vi.fn()
const speechAnalysis = vi.fn()
const saveAttempt = vi.fn()
vi.mock('@/features/student/api/studentApi', () => ({
  studentApi: {
    analyzeSpeech: (...args: unknown[]) => analyzeSpeech(...args),
    speechAnalysis: (...args: unknown[]) => speechAnalysis(...args),
    saveAttempt: (...args: unknown[]) => saveAttempt(...args),
    // 결과 아래 AI 설명 패널(이 테스트의 검증 대상 아님): 아직 만들지 않은 상태
    speechFeedback: () => Promise.resolve({ analysisId: 'a', status: 'NOT_GENERATED', source: 'AI', available: true, consentRequired: false }),
  },
}))

import SpeechActivityScreen from '@/features/student/components/SpeechActivityScreen'

const exercises = [{ id: '1', label: 'ㄹ 발음', color: '', instruction: '말해보세요', inputType: 'mic' as const, items: [{ id: '11', word: '라디오', emoji: '' }] }]
const completed = { analysisId: 'an-1', status: 'COMPLETED', evaluationMode: 'SENTENCE_MATCH', targetText: '라디오', transcript: '라디오', matchRate: 100, textMatchRate: 100 }
const renderScreen = () => render(<SpeechActivityScreen exercises={exercises} exIdx={0} onComplete={vi.fn()} onBack={vi.fn()} onOpenConsent={vi.fn()} />)

beforeEach(() => {
  analyzeSpeech.mockReset(); speechAnalysis.mockReset(); saveAttempt.mockReset(); reset.mockReset()
  recorderState.recording = new Blob(['first'], { type: 'audio/webm' })
  saveAttempt.mockResolvedValue({ saved: true })
})

describe('SpeechActivityScreen 중복 분석 방지', () => {
  it('분석 중에는 버튼을 막아 두 번 눌러도 요청이 한 번만 간다', async () => {
    let finish: (value: unknown) => void = () => undefined
    analyzeSpeech.mockReturnValue(new Promise((resolve) => { finish = resolve }))
    speechAnalysis.mockResolvedValue(completed)
    renderScreen()
    const button = screen.getByRole('button', { name: '분석하기' })
    fireEvent.click(button); fireEvent.click(button)
    await waitFor(() => expect(analyzeSpeech).toHaveBeenCalledTimes(1))
    // 분석 중에는 분석 버튼이 사라지거나(진행 표시로 바뀜) 비활성화되어야 한다.
    const pending = screen.queryByRole('button', { name: '분석하기' }) as HTMLButtonElement | null
    expect(pending === null || pending.disabled).toBe(true)
    await act(async () => finish({ analysisId: 'an-1', status: 'COMPLETED' }))
    expect(await screen.findByText('텍스트 일치율')).toBeTruthy()
    expect(analyzeSpeech).toHaveBeenCalledTimes(1)
  })

  it('실패 후 다시 분석하기는 같은 녹음의 같은 요청 키로 보낸다', async () => {
    analyzeSpeech.mockRejectedValueOnce(new Error('네트워크 오류')).mockResolvedValueOnce({ analysisId: 'an-1', status: 'COMPLETED', reused: true })
    speechAnalysis.mockResolvedValue(completed)
    renderScreen()
    fireEvent.click(screen.getByRole('button', { name: '분석하기' }))
    expect(await screen.findByText('네트워크 오류')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '다시 분석하기' }))
    await waitFor(() => expect(analyzeSpeech).toHaveBeenCalledTimes(2))
    const [firstKey, secondKey] = analyzeSpeech.mock.calls.map((call) => call[3])
    expect(firstKey).toMatch(/^[0-9a-f-]{36}$/)
    expect(secondKey).toBe(firstKey)
  })

  it('새로 녹음하면 새 요청 키를 쓴다', async () => {
    analyzeSpeech.mockRejectedValue(new Error('서버 오류'))
    const { rerender } = renderScreen()
    fireEvent.click(screen.getByRole('button', { name: '분석하기' }))
    await screen.findByText('서버 오류')
    recorderState.recording = new Blob(['second'], { type: 'audio/webm' })
    rerender(<SpeechActivityScreen exercises={exercises} exIdx={0} onComplete={vi.fn()} onBack={vi.fn()} onOpenConsent={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: '다시 분석하기' }))
    await waitFor(() => expect(analyzeSpeech).toHaveBeenCalledTimes(2))
    expect(analyzeSpeech.mock.calls[1][3]).not.toBe(analyzeSpeech.mock.calls[0][3])
  })

  it('저장할 때 연습 유형을 보낸다: 자율 SELF, 수업 LESSON(수업 ID), 숙제는 homeworkId', async () => {
    analyzeSpeech.mockResolvedValue({ analysisId: 'an-1', status: 'COMPLETED' })
    speechAnalysis.mockResolvedValue(completed)
    const cases: [Record<string, unknown>, Record<string, unknown>][] = [
      [{}, { practiceType: 'SELF' }],
      [{ lessonSessionId: '31' }, { practiceType: 'LESSON', sessionId: 31 }],
      [{ homeworkId: 9 }, { homeworkId: 9 }],
    ]
    for (const [props, expected] of cases) {
      saveAttempt.mockClear()
      const view = render(<SpeechActivityScreen exercises={exercises} exIdx={0} onComplete={vi.fn()} onBack={vi.fn()} onOpenConsent={vi.fn()} {...props} />)
      fireEvent.click(screen.getByRole('button', { name: '분석하기' }))
      await waitFor(() => expect(saveAttempt).toHaveBeenCalledTimes(1))
      expect(saveAttempt).toHaveBeenCalledWith(expect.objectContaining(expected))
      if (!('homeworkId' in expected)) expect(saveAttempt.mock.calls[0][0]).not.toHaveProperty('homeworkId')
      if ('homeworkId' in expected) expect(saveAttempt.mock.calls[0][0]).not.toHaveProperty('practiceType')
      view.unmount()
    }
  })
})

