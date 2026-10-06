import type { BadgeTone } from '@/shared/components/badge/Badge'

export const statusTone: Record<string, BadgeTone> = { 진행중: 'info', 예정: 'neutral', 완료: 'success' }
export const STATUS_FILTERS = [{ value: '', label: '전체 상태' }, { value: '진행중', label: '진행중' }, { value: '예정', label: '예정' }, { value: '완료', label: '완료' }]
/** 학생 이니셜 아바타 바탕(파스텔). 글자는 짙은 색으로 쓴다 */
export const avatarColors = ['var(--butter-200)', 'var(--meadow-200)', 'var(--coral-200)', 'var(--sky-200)', 'var(--lilac-100)']
export const numberOrNull = (value: unknown) => value == null || !Number.isFinite(Number(value)) ? null : Number(value)
