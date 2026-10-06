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
const ready = { analysisId: 'a-1', status: 'READY', source: 'AI', text: '신라를 끝까지 말했어요! ㄴ이 ㄹ 앞에서 ㄹ로 소리 나요 [S1].', basedOn: ['AUTO_ANALYSIS'], modelName: 'gemini-flash-latest',
  sources: [{ marker: 'S1', cited: true, chunkId: 'std-pron-20', title: '표준 발음법(표준어 규정 제2부)', location: '제5장 제20항', url: 'https://korean.go.kr/kornorms/' },
    { marker: 'S2', cited: false, chunkId: 'std-pron-13', title: '표준 발음법(표준어 규정 제2부)', location: '제4장 제13항' }] }

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
    expect(screen.getByText(/자동 분석 결과\(확정 아님\)/)).toBeTruthy()
    expect(screen.getByText(/제5장 제20항/)).toBeTruthy() // 인용한 자료만 표시
    expect(screen.queryByText(/제4장 제13항/)).toBeNull()
    expect(screen.getByText(/발음 정확도는 평가하지 않았어요/)).toBeTruthy()
    expect(generateSpeechFeedback).toHaveBeenCalledTimes(1)
  })

  it('shows an existing explanation directly and teacher-confirmed sources', async () => {
    speechFeedback.mockResolvedValue({ ...ready, basedOn: ['TEACHER_CONFIRMED'] })
    render(<AiFeedbackPanel analysisId="a-1" />)
    expect(await screen.findByText(ready.text)).toBeTruthy()
    expect(screen.getByText(/선생님 확인 결과/)).toBeTruthy()
    expect(generateSpeechFeedback).not.toHaveBeenCalled()
  })

  it('lists official sources by title and location and marks teacher-approved examples as their own type', async () => {
    speechFeedback.mockResolvedValue({ ...ready, text: '라디오를 끝까지 말해 봤어요! ㄴ과 ㄹ은 혀끝으로 내는 소리예요 [S1]. 천천히 따라 말해 봐요 [S2].',
      sources: [{ marker: 'S1', cited: true, chunkId: 'nikl-pron-place-02', sourceId: 'nikl-pron-place', category: 'ARTICULATION_PLACE',
        title: '표준 발음법 제2항(자음의 조음 위치) 해설', location: '제2장 제2항 해설(자음 분류표)', url: 'https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002' },
      { marker: 'S2', cited: true, chunkId: 'teacher-ex-01', sourceId: 'teacher-ex', category: 'TEACHER_EXAMPLE', title: 'ㄹ 첫소리 설명 예시', location: '예시 1', url: null }] })
    render(<AiFeedbackPanel analysisId="a-1" />)
    expect(await screen.findByText(/제2장 제2항 해설\(자음 분류표\)/)).toBeTruthy()
    expect(screen.getAllByRole('link', { name: '원문' })).toHaveLength(1) // 원문 URL이 있는 자료만 링크
    expect(screen.getByText(/ㄹ 첫소리 설명 예시 예시 1 · 선생님 승인 설명 예시/)).toBeTruthy()
    expect(screen.queryByText(/자음의 조음 위치\) 해설 제2장 제2항 해설\(자음 분류표\) · 선생님 승인/)).toBeNull()
  })

  it('shows "no reviewed sources" separately and offers no generate button', async () => {
    speechFeedback.mockResolvedValue({ analysisId: 'a-1', status: 'INSUFFICIENT_SOURCES', source: 'AI', reason: '이 결과를 설명할 검수된 교육 자료가 없어 AI 설명을 만들지 않았어요.' })
    render(<AiFeedbackPanel analysisId="a-1" />)
    expect(await screen.findByText(/근거 자료 부족/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'AI 설명 보기' })).toBeNull()
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
    expect(await screen.findByText(/AI 학습 피드백 외부 전송/)).toBeTruthy()
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
