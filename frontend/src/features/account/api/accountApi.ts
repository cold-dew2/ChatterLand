import { apiClient } from '@/shared/api/client'
import type { User } from '@/features/auth/api/authApi'

/** 알림 설정: 실제로 일어나는 숙제 이벤트만(새 숙제·내용 변경·마감 임박·마감 지남) */
export type NotificationSettings = { homeworkAssigned: boolean; homeworkChanged: boolean; homeworkDueSoon: boolean; homeworkOverdue: boolean }

export const DEFAULT_NOTIFICATION_SETTINGS: NotificationSettings = { homeworkAssigned: true, homeworkChanged: true, homeworkDueSoon: true, homeworkOverdue: true }

/** 로그인한 사용자 자신의 계정 설정(비밀번호 변경은 authApi.changePassword) */
export const accountApi = {
  updateProfile: (body: { name: string }) => apiClient.patch<User>('/api/v1/account/profile', body),
  notificationSettings: () => apiClient.get<NotificationSettings>('/api/v1/account/notification-settings'),
  saveNotificationSettings: (body: NotificationSettings) => apiClient.put<NotificationSettings>('/api/v1/account/notification-settings', body),
}
