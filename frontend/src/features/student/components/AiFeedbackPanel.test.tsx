import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/client'

const speechFeedback = vi.fn()
const generateSpeechFeedback = vi.fn()
vi.mock('@/features/student/api/studentApi', () => ({
  studentApi: { speechFeedback: (...a: unknown[]) => speechFeedback(...a), generateSpeechFeedback: (...a: unknown[]) => generateSpeechFeedback(...a) },
}))

import AiFeedbackPanel from '@/features/student/components/AiFeedbackPanel'

const notGenerated = { analysisId: 'a-1', status: 'NOT_GENERATED', source: 'AI', available: true, consentRequired: false, basedOn: ['AUTO_ANALYSIS'] }
const ready = { analysisId: 'a-1', status: 'READY', source: 'AI', text: '라디오를 끝까지 말했어요! 첫소리를 천천히 연습해 봐요.', basedOn: ['AUTO_ANALYSIS'], modelName: 'gemini-flash-latest' }

beforeEach(() => { speechFeedback.mockReset(); generateSpeechFeedback.mockReset() })

describe('AiFeedbackPanel', () => {
  it('starts without calling the AI and generates only when asked, labelled as an AI explanation (not a score)', async () => {
    speechFeedback.mockResolvedValue(notGenerated)
    let finish: (value: unknown) => void = () => undefined
    generateSpeechFeedback.mockReturnValue(new Promise((resolve) => { finish = resolve }))
    render(<AiFeedbackPanel analysisId="a-1" />)
    fireEvent.click(await screen.findByRole('button', { name: 'AI 설명 보기' }))
    expect(screen.getByText('AI가 설명을 쓰고 있어요…')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'AI 설명 보기' })).toBeNull() // 만드는 중 중복 요청 없음
    finish(ready)
    expect(await screen.findByText(ready.text)).toBeTruthy()
    expect(screen.getByText('점수 아님')).toBeTruthy()
    expect(screen.getByText(/자동 분석를 바탕으로|자동 분석을 바탕으로|자동 분석/)).toBeTruthy()
    expect(generateSpeechFeedback).toHaveBeenCalledTimes(1)
  })

  it('shows an existing explanation directly and teacher-confirmed sources', async () => {
    speechFeedback.mockResolvedValue({ ...ready, basedOn: ['TEACHER_CONFIRMED'] })
    render(<AiFeedbackPanel analysisId="a-1" />)
    expect(await screen.findByText(ready.text)).toBeTruthy()
    expect(screen.getByText(/선생님 확인 결과/)).toBeTruthy()
    expect(generateSpeechFeedback).not.toHaveBeenCalled()
  })

  it('separates "cannot be evaluated" from "not generated yet"', async () => {
    speechFeedback.mockResolvedValue({ analysisId: 'a-1', status: 'NOT_EVALUABLE', source: 'AI', reason: '녹음이 또렷하지 않아 결과를 판단하지 않았어요. 다시 녹음하면 설명을 볼 수 있어요.' })
    render(<AiFeedbackPanel analysisId="a-1" />)
    expect(await screen.findByText(/녹음이 또렷하지 않아/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'AI 설명 보기' })).toBeNull()
  })

  it('explains consent, missing configuration and AI errors with a retry', async () => {
    speechFeedback.mockResolvedValue({ ...notGenerated, consentRequired: true })
    const { unmount } = render(<AiFeedbackPanel analysisId="a-1" />)
    expect(await screen.findByText(/AI 대화 외부 전송/)).toBeTruthy()
    unmount()

    speechFeedback.mockResolvedValue({ ...notGenerated, available: false })
    const second = render(<AiFeedbackPanel analysisId="a-1" />)
    expect(await screen.findByText('지금은 AI 설명을 쓸 수 없어요.')).toBeTruthy()
    second.unmount()

    speechFeedback.mockResolvedValue(notGenerated)
    generateSpeechFeedback.mockRejectedValueOnce(new ApiError('AI 서비스 응답 시간이 초과되었어요. 잠시 뒤 다시 시도해 주세요.', 504, 'AI_TIMEOUT'))
      .mockResolvedValueOnce(ready)
    render(<AiFeedbackPanel analysisId="a-1" />)
    fireEvent.click(await screen.findByRole('button', { name: 'AI 설명 보기' }))
    expect(await screen.findByText(/응답 시간이 초과/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText(ready.text)).toBeTruthy()
  })

  it('shows a load error with retry', async () => {
    speechFeedback.mockRejectedValueOnce(new ApiError('서버에 연결할 수 없어요.', 0, 'NETWORK_ERROR')).mockResolvedValueOnce(notGenerated)
    render(<AiFeedbackPanel analysisId="a-1" />)
    fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('button', { name: 'AI 설명 보기' })).toBeTruthy()
  })
})
