import type { BadgeTone } from '@/shared/components/badge/Badge'

export const statusTone: Record<string, BadgeTone> = { 진행중: 'info', 예정: 'neutral', 완료: 'success' }
export const STATUS_FILTERS = [{ value: '', label: '전체 상태' }, { value: '진행중', label: '진행중' }, { value: '예정', label: '예정' }, { value: '완료', label: '완료' }]
export const avatarColors = ['var(--brand-blue)', 'var(--brand-teal)', 'var(--brand-coral)', 'var(--brand-purple)', 'var(--brand-slate)']
export const numberOrNull = (value: unknown) => value == null || !Number.isFinite(Number(value)) ? null : Number(value)
