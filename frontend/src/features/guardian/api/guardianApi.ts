import { apiClient, queryString } from '@/shared/api/client'
import type { ReportPeriod } from '@/features/learningReport/utils/learningReport'

/** 연결된 자녀(서버가 보호자 계정과 ACTIVE 연결을 확인한 학생만 돌려준다) */
export type GuardianChild = { studentId: number; name: string; age: number | null; centerName: string; linkedOn: string; linkStatus: string }
export type ChildConsent = { type: string; agreed: boolean; policyVersion: string; changedOn: string | null }

const childPath = (studentId: number) => `/api/v1/guardians/me/children/${studentId}`

export const guardianApi = {
  children: () => apiClient.get<GuardianChild[]>('/api/v1/guardians/me/children'),
  /** 자녀 계정에서 만든 연결 코드로 연결(학생 ID를 보내지 않는다) */
  link: (code: string) => apiClient.post<GuardianChild>('/api/v1/guardians/me/children', { code }),
  child: (studentId: number) => apiClient.get<GuardianChild & { consents: ChildConsent[] }>(childPath(studentId)),
  unlink: (studentId: number) => apiClient.delete<void>(childPath(studentId)),
  report: (studentId: number, period?: ReportPeriod) => apiClient.get<Record<string, unknown>>(`${childPath(studentId)}/learning-report${queryString({ period })}`),
  history: (studentId: number, page = 0, size = 10) => apiClient.get<Record<string, unknown>>(`${childPath(studentId)}/history${queryString({ page, size })}`),
}
