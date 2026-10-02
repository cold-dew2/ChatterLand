import { apiClient } from '@/shared/api/client'
import type { ConsentType } from '@/features/consent/consentPolicy'

export type Consent = {
  type: ConsentType
  agreed: boolean
  required: boolean
  policyVersion: string | null
  agreedAt: string | null
  withdrawnAt: string | null
  guardianName: string | null
  guardianRelation: string | null
  currentVersion: boolean
}

export type GuardianInfo = { guardianConfirmed: boolean; guardianName: string; guardianRelation: string }

export const consentApi = {
  mine: () => apiClient.get<Consent[]>('/api/v1/consents/me'),
  update: (type: ConsentType, body: { agreed: boolean; policyVersion: string } & Partial<GuardianInfo>) =>
    apiClient.put<Consent>(`/api/v1/consents/me/${type}`, body),
}
