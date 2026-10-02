import { ApiError, errorMessage } from '@/shared/api/client'

/** 동의가 필요한 기능을 호출했는지(서버 403 CONSENT_REQUIRED) */
export function isConsentRequired(cause: unknown) {
  return cause instanceof ApiError && cause.code === 'CONSENT_REQUIRED'
}

export function speechErrorMessage(cause: unknown, fallback: string) {
  return errorMessage(cause, fallback)
}
