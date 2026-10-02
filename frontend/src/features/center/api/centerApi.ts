import { apiClient } from '@/shared/api/client'

export type Center = { centerId: number; name: string }

export const centerApi = {
  /** 사용 중인 센터만 반환한다(공개 API). */
  list: () => apiClient.get<Center[]>('/api/v1/centers'),
}
