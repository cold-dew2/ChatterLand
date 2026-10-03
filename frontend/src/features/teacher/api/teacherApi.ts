import { apiClient, queryString } from '@/shared/api/client'
import type { LearnerType, SpeechJudgement, TeacherSpeechAnalysis } from '@/features/teacher/types'
import type { ConfirmedError } from '@/features/student/types'

type StudentBody = { name: string; age?: number; parentPhone?: string; tags?: string[]; memo?: string; sessionsTotal?: number; status?: string; learnerType?: LearnerType }

export const teacherApi = {
  students: (keyword?: string, status?: string, page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/teachers/me/students${queryString({ keyword, status, page, size })}`),
  availableStudents: () => apiClient.get<Record<string, unknown>[]>('/api/v1/teachers/me/students/available'),
  addStudent: (body: StudentBody & { studentId?: number }) => apiClient.post<Record<string, unknown>>('/api/v1/teachers/me/students', body),
  updateStudent: (id: number, body: StudentBody) => apiClient.put<Record<string, unknown>>(`/api/v1/teachers/me/students/${id}`, body),
  deleteStudent: (id: number) => apiClient.delete<void>(`/api/v1/teachers/me/students/${id}`),
  student: (id: string | number) => apiClient.get<Record<string, unknown>>(`/api/v1/teachers/me/students/${id}`),
  sessions: (id: string | number, page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/teachers/me/students/${id}/sessions${queryString({ page, size })}`),
  homeworks: (studentId?: number, status?: string, page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`/api/v1/teachers/me/homeworks${queryString({ studentId, status, page, size })}`),
  /** idempotencyKey: 등록 의도마다 만든 키. 응답을 못 받아 같은 내용으로 다시 보내면 서버가 처음 만든 숙제를 돌려준다(중복 등록 방지). */
  addHomework: (body: { studentId: number; title: string; type: string; dueDate: string; targetMinutes: number; description?: string }, idempotencyKey?: string) =>
    apiClient.post<Record<string, unknown>>('/api/v1/teachers/me/homeworks', body, idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined),
  /** body.version(필수): 화면이 불러온 숙제 버전. 그 사이 다른 곳에서 바뀌었으면 409(VERSION_CONFLICT). */
  updateHomework: (id: number, body: Record<string, unknown>) => apiClient.patch<Record<string, unknown>>(`/api/v1/teachers/me/homeworks/${id}`, body),
  /** version(필수): 화면이 불러온 숙제 버전. 그 사이 수정·완료되었으면 지우지 않고 409(VERSION_CONFLICT). */
  deleteHomework: (id: number, version: number) => apiClient.delete<void>(`/api/v1/teachers/me/homeworks/${id}${queryString({ version })}`),
  analytics: (id: number, startDate: string, endDate: string) => apiClient.get<Record<string, unknown>>(`/api/v1/teachers/me/students/${id}/analytics${queryString({ startDate, endDate })}`),
  report: (id: number, startDate: string, endDate: string) => apiClient.get<Record<string, unknown>>(`/api/v1/teachers/me/students/${id}/report${queryString({ startDate, endDate })}`),
  speechAnalyses: (studentId: number, reviewStatus?: string, page = 0, size = 10) => apiClient.get<{ content: TeacherSpeechAnalysis[]; totalPages: number; totalElements: number }>(`/api/v1/teachers/me/students/${studentId}/speech-analyses${queryString({ reviewStatus, page, size })}`),
  speechAnalysisAudio: (analysisId: string) => apiClient.get<Blob>(`/api/v1/teachers/me/speech-analyses/${encodeURIComponent(analysisId)}/audio`),
  reviewSpeechAnalysis: (analysisId: string, body: { judgement: SpeechJudgement; note?: string; confirmedErrors?: ConfirmedError[] }) => apiClient.patch<TeacherSpeechAnalysis>(`/api/v1/teachers/me/speech-analyses/${encodeURIComponent(analysisId)}/review`, body),
  downloadReport: (id: number, startDate: string, endDate: string) => apiClient.get<Blob>(`/api/v1/teachers/me/students/${id}/report/download${queryString({ startDate, endDate })}`),
}
