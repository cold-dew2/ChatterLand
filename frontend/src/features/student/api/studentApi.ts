import { apiClient, queryString } from '@/shared/api/client'
import type { SpeechAnalysis } from '@/features/student/types'

export const studentApi = {
  me: () => apiClient.get<Record<string, unknown>>('/api/v1/students/me'),
  sessions: (page = 0, size = 10, status?: string) => apiClient.get<Record<string, unknown>>(`/api/v1/students/me/sessions${queryString({ page, size, status })}`),
  homeworks: (page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/students/me/homeworks${queryString({ page, size })}`),
  completeHomework: (id: number) => apiClient.patch<Record<string, unknown>>(`/api/v1/students/me/homeworks/${id}/complete`, {}),
  session: (id: string) => apiClient.get<Record<string, unknown>>(`/api/v1/sessions/${encodeURIComponent(id)}`),
  categories: () => apiClient.get<Record<string, unknown>[]>('/api/v1/practice/categories'),
  exercises: (categoryId: string, page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/practice/${encodeURIComponent(categoryId)}/exercises${queryString({ page, size })}`),
  /** score는 서버가 실제로 측정한 점수가 있을 때만 보낸다. */
  saveAttempt: (body: { exerciseId: string; itemId: string; audioId: string; score?: number }) => apiClient.post<{ saved: boolean; attemptId: number; score?: number | null; matchRate?: number | null }>('/api/v1/practice/attempts', body),
  analyzeSpeech: (audio: Blob, exerciseId: string, itemId: string) => {
    const extension = audio.type.includes('wav') ? 'wav' : audio.type.includes('mp4') ? 'm4a' : audio.type.includes('ogg') ? 'ogg' : 'webm'
    const body = new FormData(); body.append('audio', audio, `speech.${extension}`); body.append('exerciseId', exerciseId); body.append('itemId', itemId)
    return apiClient.upload<{ analysisId: string; status: string }>('/api/v1/speech/analyze', body)
  },
  speechAnalysis: (id: string) => apiClient.get<SpeechAnalysis>(`/api/v1/speech/analyses/${encodeURIComponent(id)}`),
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
