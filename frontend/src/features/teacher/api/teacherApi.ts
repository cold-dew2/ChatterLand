import { apiClient, queryString } from '@/shared/api/client'
import type { LearnerType, SpeechJudgement, TeacherSpeechAnalysis } from '@/features/teacher/types'

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
  addHomework: (body: { studentId: number; title: string; type: string; dueDate: string; targetMinutes: number; description?: string }) => apiClient.post<Record<string, unknown>>('/api/v1/teachers/me/homeworks', body),
  updateHomework: (id: number, body: Record<string, unknown>) => apiClient.patch<Record<string, unknown>>(`/api/v1/teachers/me/homeworks/${id}`, body),
  deleteHomework: (id: number) => apiClient.delete<void>(`/api/v1/teachers/me/homeworks/${id}`),
  analytics: (id: number, startDate: string, endDate: string) => apiClient.get<Record<string, unknown>>(`/api/v1/teachers/me/students/${id}/analytics${queryString({ startDate, endDate })}`),
  report: (id: number, startDate: string, endDate: string) => apiClient.get<Record<string, unknown>>(`/api/v1/teachers/me/students/${id}/report${queryString({ startDate, endDate })}`),
  speechAnalyses: (studentId: number, reviewStatus?: string, page = 0, size = 10) => apiClient.get<{ content: TeacherSpeechAnalysis[]; totalPages: number; totalElements: number }>(`/api/v1/teachers/me/students/${studentId}/speech-analyses${queryString({ reviewStatus, page, size })}`),
  speechAnalysisAudio: (analysisId: string) => apiClient.get<Blob>(`/api/v1/teachers/me/speech-analyses/${encodeURIComponent(analysisId)}/audio`),
  reviewSpeechAnalysis: (analysisId: string, body: { judgement: SpeechJudgement; note?: string }) => apiClient.patch<TeacherSpeechAnalysis>(`/api/v1/teachers/me/speech-analyses/${encodeURIComponent(analysisId)}/review`, body),
  downloadReport: (id: number, startDate: string, endDate: string) => apiClient.get<Blob>(`/api/v1/teachers/me/students/${id}/report/download${queryString({ startDate, endDate })}`),
}
