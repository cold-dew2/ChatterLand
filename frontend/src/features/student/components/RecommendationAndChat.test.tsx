import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/client'

vi.mock('@/features/student/hooks/useAudioRecorder', () => ({
  useAudioRecorder: () => ({ status: 'idle', recording: null, previewUrl: null, elapsed: 0, error: '', maxSeconds: 30, start: vi.fn(), stop: vi.fn(), cancel: vi.fn(), reset: vi.fn() }),
}))
const api = {
  recommendations: vi.fn(), createConversation: vi.fn(), sendMessage: vi.fn(), conversationContents: vi.fn(), endConversation: vi.fn(),
}
vi.mock('@/features/student/api/studentApi', () => ({ studentApi: new Proxy({}, { get: (_, key: string) => (...args: unknown[]) => (api as Record<string, (...a: unknown[]) => unknown>)[key](...args) }) }))

import AiChatScreen from '@/features/student/components/AiChatScreen'
import RecommendationSection, { practiceScreenFor } from '@/features/student/components/RecommendationSection'

const item = (id: number, reasonCode: string, reason: string, kind = 'NEW') => ({ exerciseId: id, title: `콘텐츠 ${id}`, categoryId: 'articulation', categoryName: '발음', color: '#3e7a47', kind, reasonCode, reason })
const related = { exerciseId: 31, title: '과일 어휘', categoryId: 'vocabulary', categoryName: '어휘력', color: '#5B8BC8', keyword: '사과', reason: "대화에서 나온 '사과' 낱말이 들어 있는 연습이에요" }

beforeEach(() => {
  Object.values(api).forEach((fn) => fn.mockReset())
  window.HTMLElement.prototype.scrollIntoView = vi.fn()
})

describe('추천 연습', () => {
  it('shows each reason, opens the existing practice flow, and points pending homework to 숙제하기', async () => {
    api.recommendations.mockResolvedValue({ personalized: true, basis: '저장된 연습 기록 12회와 연습 영역을 바탕으로 골랐어요.', pendingHomeworks: 2,
      items: [item(1, 'REPEATED', '최근 여러 번 연습한 콘텐츠예요', 'REVIEW'), item(2, 'UNTRIED_AREA', '아직 연습하지 않은 영역이에요')] })
    const onNavigate = vi.fn()
    render(<RecommendationSection onNavigate={onNavigate} />)
    expect(await screen.findByText('내 기록 기반')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '콘텐츠 1 연습하기, 최근 여러 번 연습한 콘텐츠예요' }))
    expect(onNavigate).toHaveBeenCalledWith(expect.objectContaining({ kind: 'practice-run', mode: 'selected', selection: [{ id: '1', label: '콘텐츠 1' }],
      category: expect.objectContaining({ id: 'articulation' }), origin: 'category' }))
    fireEvent.click(screen.getByRole('button', { name: /숙제 2개는 숙제하기에서/ }))
    expect(onNavigate).toHaveBeenLastCalledWith({ kind: 'homework-list' })
  })

  it('does not call starter content personalized', async () => {
    api.recommendations.mockResolvedValue({ personalized: false, basis: '아직 연습 기록이 없어 영역별 기본 콘텐츠를 보여 드려요.', pendingHomeworks: 0,
      items: [item(5, 'STARTER', '처음 시작하기 좋은 발음 기본 콘텐츠예요')] })
    render(<RecommendationSection onNavigate={vi.fn()} />)
    expect(await screen.findByText('기본 추천')).toBeTruthy()
    expect(screen.getByRole('heading', { name: '이렇게 시작해 보세요' })).toBeTruthy()
    expect(screen.queryByText('내 기록 기반')).toBeNull()
  })

  it('keeps the practice target shape the run screen expects', () => {
    expect(practiceScreenFor(related)).toMatchObject({ kind: 'practice-run', index: 0, results: [], category: { id: 'vocabulary', label: '어휘력' } })
  })
})

describe('AI 대화 학습', () => {
  const setup = () => {
    api.createConversation.mockResolvedValue({ conversationId: 'c-1' })
    const handlers = { onBack: vi.fn(), onOpenConsent: vi.fn(), onPractice: vi.fn(), onNewTopic: vi.fn() }
    render(<AiChatScreen topic="좋아하는 음식" {...handlers} />)
    return handlers
  }
  const say = async (text: string) => {
    fireEvent.change(screen.getByLabelText('대화 메시지'), { target: { value: text } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '보내기' })) })
  }

  it('leaves without a summary when nothing was said', async () => {
    const { onBack } = setup()
    await act(async () => { await Promise.resolve() })
    fireEvent.click(screen.getByRole('button', { name: '대화 종료' }))
    expect(onBack).toHaveBeenCalledOnce()
    expect(api.endConversation).not.toHaveBeenCalled()
  })

  it('links related practice, summarizes the real conversation, and offers next activities', async () => {
    const { onPractice, onNewTopic } = setup()
    api.sendMessage.mockResolvedValue({ response: '사과 맛있지! 사과는 어떤 맛이 나서 좋아?' })
    api.conversationContents.mockResolvedValue({ items: [related] })
    await act(async () => { await Promise.resolve() })
    await say('저는 사과를 좋아해요')
    expect(await screen.findByText('사과 맛있지! 사과는 어떤 맛이 나서 좋아?')).toBeTruthy()
    fireEvent.click(await screen.findByRole('button', { name: /과일 어휘 연습하기/ }))
    expect(onPractice).toHaveBeenCalledWith(expect.objectContaining({ exerciseId: 31, categoryId: 'vocabulary' }))

    api.endConversation.mockResolvedValue({ summary: '오늘은 좋아하는 사과 이야기를 나눴어요.', studentTurns: 1, relatedContents: [related],
      nextActivities: [{ type: 'PRACTICE', label: '대화와 관련된 연습 해 보기' }] })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '대화 끝내고 요약 보기' })) })
    expect(api.endConversation).toHaveBeenCalledWith('c-1')
    expect(screen.getByText('오늘은 좋아하는 사과 이야기를 나눴어요.')).toBeTruthy()
    expect(screen.getByText(/발음은 평가하지 않아요/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '다른 주제로 이야기하기' }))
    expect(onNewTopic).toHaveBeenCalledOnce()
  })

  it('keeps the conversation open with a retry when the summary fails, and guides to consent', async () => {
    const { onOpenConsent } = setup()
    api.sendMessage.mockResolvedValue({ response: '좋아! 왜 좋아?' })
    api.conversationContents.mockRejectedValue(new Error('x'))
    await act(async () => { await Promise.resolve() })
    await say('바나나가 좋아요')
    expect(await screen.findByText('관련 연습을 불러오지 못했어요. 대화는 계속할 수 있어요.')).toBeTruthy()
    api.endConversation.mockRejectedValue(new ApiError('AI 서비스 응답 시간이 초과되었어요.', 504, 'AI_TIMEOUT'))
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '대화 끝내고 요약 보기' })) })
    expect(screen.getByText('AI 서비스 응답 시간이 초과되었어요.')).toBeTruthy()
    expect(screen.getByRole('button', { name: '다시 시도: 대화 끝내고 요약 보기' })).toBeTruthy()
    expect(screen.queryByText(/오늘 나눈 이야기/)).toBeNull()
    expect(screen.getByText('바나나가 좋아요')).toBeTruthy() // 대화는 그대로

    api.sendMessage.mockRejectedValue(new ApiError("AI 대화를 이용하려면 마이페이지에서 'AI 대화 외부 전송'에 동의해 주세요.", 403, 'CONSENT_REQUIRED'))
    await say('또 말할래요')
    fireEvent.click(screen.getByRole('button', { name: '동의 관리로 가기' }))
    expect(onOpenConsent).toHaveBeenCalledOnce()
    expect(screen.queryByText('또 말할래요')).toBeNull() // 실패한 메시지는 대화에 남기지 않는다
  })
})
