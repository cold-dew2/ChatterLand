import { apiClient, queryString } from '@/shared/api/client'
import type { AiFeedback, SpeechAnalysis } from '@/features/student/types'

export const studentApi = {
  me: () => apiClient.get<Record<string, unknown>>('/api/v1/students/me'),
  sessions: (page = 0, size = 10, status?: string) => apiClient.get<Record<string, unknown>>(`/api/v1/students/me/sessions${queryString({ page, size, status })}`),
  homeworks: (page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/students/me/homeworks${queryString({ page, size })}`),
  completeHomework: (id: number) => apiClient.patch<Record<string, unknown>>(`/api/v1/students/me/homeworks/${id}/complete`, {}),
  session: (id: string) => apiClient.get<Record<string, unknown>>(`/api/v1/sessions/${encodeURIComponent(id)}`),
  categories: () => apiClient.get<Record<string, unknown>[]>('/api/v1/practice/categories'),
  exercises: (categoryId: string, page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/practice/${encodeURIComponent(categoryId)}/exercises${queryString({ page, size })}`),
  /** 다시 연습: 이 영역에서 연습했던 세트(세트당 한 번, 최근 연습 순) */
  practiced: (categoryId: string, page = 0, size = 20) => apiClient.get<Record<string, unknown>>(`/api/v1/practice/${encodeURIComponent(categoryId)}/practiced${queryString({ page, size })}`),
  /** score는 서버가 실제로 측정한 점수가 있을 때만 보낸다. */
  /** homeworkId: 숙제 연습. 숙제가 아니면 practiceType SELF(자율) 또는 LESSON(수업, sessionId 필요) */
  saveAttempt: (body: { exerciseId: string; itemId: string; audioId: string; score?: number; homeworkId?: number; practiceType?: 'SELF' | 'LESSON'; sessionId?: number }) => apiClient.post<{ saved: boolean; attemptId: number; score?: number | null; matchRate?: number | null }>('/api/v1/practice/attempts', body),
  /** requestKey: 녹음마다 만든 Idempotency-Key. 같은 녹음을 다시 보내면 서버가 같은 분석을 돌려준다(중복 분석 방지). */
  analyzeSpeech: (audio: Blob, exerciseId: string, itemId: string, requestKey?: string) => {
    const extension = audio.type.includes('wav') ? 'wav' : audio.type.includes('mp4') ? 'm4a' : audio.type.includes('ogg') ? 'ogg' : 'webm'
    const body = new FormData(); body.append('audio', audio, `speech.${extension}`); body.append('exerciseId', exerciseId); body.append('itemId', itemId)
    return apiClient.upload<{ analysisId: string; status: string; reused?: boolean }>('/api/v1/speech/analyze', body,
      requestKey ? { 'Idempotency-Key': requestKey } : undefined)
  },
  /** 연습 콘텐츠 검색(자율 연습). 검색어·카테고리·난이도·발음 유형·콘텐츠 유형 필터, 페이지 */
  practiceContents: (params: { keyword?: string; categoryId?: string; difficulty?: string; rule?: string; contentType?: string; page?: number; size?: number }) =>
    apiClient.get<{ content: Record<string, unknown>[]; totalElements: number; totalPages: number; page: number }>(`/api/v1/practice-contents${queryString({ ...params })}`),
  practiceContent: (exerciseId: number) => apiClient.get<Record<string, unknown>>(`/api/v1/practice-contents/${exerciseId}`),
  speechAnalysis: (id: string) => apiClient.get<SpeechAnalysis>(`/api/v1/speech/analyses/${encodeURIComponent(id)}`),
  /** AI 학습 피드백 상태(AI를 부르지 않음) */
  speechFeedback: (id: string) => apiClient.get<AiFeedback>(`/api/v1/speech/analyses/${encodeURIComponent(id)}/feedback`),
  /** AI 학습 피드백 만들기(근거가 부족하면 NOT_EVALUABLE) */
  generateSpeechFeedback: (id: string) => apiClient.post<AiFeedback>(`/api/v1/speech/analyses/${encodeURIComponent(id)}/feedback`),
  createConversation: (topic: string) => apiClient.post<{ conversationId: string }>('/api/v1/ai/conversations', { topic }),
  sendMessage: (id: string, message: { text?: string; audio?: Blob }) => {
    const path = `/api/v1/ai/conversations/${encodeURIComponent(id)}/messages`
    if (!message.audio) return apiClient.post<Record<string, unknown>>(path, { text: message.text })
    const body = new FormData(); body.append('audio', message.audio, 'message.webm')
    return apiClient.upload<Record<string, unknown>>(path, body)
  },
  conversations: (page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/ai/conversations${queryString({ page, size })}`),
  history: (type?: string, page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/students/me/history${queryString({ type, page, size })}`),
}
