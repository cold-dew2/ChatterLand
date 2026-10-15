import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const findId = vi.fn()
vi.mock('@/features/auth/api/authApi', () => ({ authApi: { findId: (...a: unknown[]) => findId(...a) } }))
vi.mock('@/features/center/hooks/useCenters', () => ({ useCenters: () => ({ state: 'ready', options: [{ value: '1', label: '해솔 센터' }], error: '', retry: vi.fn() }) }))
const recommendations = vi.fn()
vi.mock('@/features/student/api/studentApi', () => ({ studentApi: { recommendations: () => recommendations(), guardians: () => Promise.resolve([]) } }))

import FindIdPage from '@/features/auth/pages/FindIdPage'
import StudentHomeScreen from '@/features/student/components/StudentHomeScreen'
import StudentGuardianScreen from '@/features/student/components/StudentGuardianScreen'
import { guardianNotices, mapGuardianEvents } from '@/features/student/utils/guardianNotifications'
import { GUARDIAN_LINK_SCOPE } from '@/features/guardian/utils/linkPolicy'

const student = { name: '김하늘', grade: '학습 중', averageMatchRate: null, totalAttempts: 0, sessionsTotal: 0, sessionsDone: 0, streak: 0 }

beforeEach(() => {
  findId.mockReset(); recommendations.mockReset()
  recommendations.mockResolvedValue({ personalized: false, basis: '', items: [], pendingHomeworks: 0 })
})

describe('보호자 연결·해제 알림', () => {
  it('makes one notice per event key, says who unlinked, and keeps records-preserved wording', () => {
    const events = mapGuardianEvents([
      { key: 'guardian-linked-3-2026-10-14 10:00:00', type: 'GUARDIAN_LINKED', guardianName: '김보호', occurredAt: '2026-10-14 10:00:00' },
      { key: 'guardian-linked-3-2026-10-14 10:00:00', type: 'GUARDIAN_LINKED', guardianName: '김보호', occurredAt: '2026-10-14 10:00:00' },
      { key: 'guardian-unlinked-3-2026-10-14 11:00:00', type: 'GUARDIAN_UNLINKED', guardianName: '김보호', occurredAt: '2026-10-14 11:00:00', unlinkedBy: 'GUARDIAN' },
      { key: 'x', type: 'SOMETHING_ELSE' },
    ])
    const notices = guardianNotices(events)
    expect(notices).toHaveLength(2)
    expect(notices[0]).toMatchObject({ kind: 'guardianLinked', title: '김보호님과 보호자 연결' })
    expect(notices[1].message).toContain('보호자가 연결을 해제했어요')
    expect(notices[1].message).toContain('기록은 그대로 남아요')
  })

  it('shows guardian notices in the home bell and opens the guardian page', async () => {
    const onNavigate = vi.fn()
    render(<StudentHomeScreen student={student} nextSession={null} homeworks={[]} homeworkState="ready" profileState="ready" recent={[]} recentState="ready"
      onRetry={vi.fn()} onNavigate={onNavigate}
      guardianEvents={[{ key: 'k1', type: 'GUARDIAN_LINKED', guardianName: '김보호', occurredAt: '2026-10-14 10:00:00' }]} />)
    fireEvent.click(screen.getByRole('button', { name: '알림 1개' }))
    fireEvent.click(await screen.findByRole('button', { name: /김보호님과 보호자 연결/ }))
    expect(onNavigate).toHaveBeenCalledWith({ kind: 'my-guardians' })
  })

  it('says when guardian notices could not be loaded instead of hiding the error', async () => {
    render(<StudentHomeScreen student={student} nextSession={null} homeworks={[]} homeworkState="ready" profileState="ready" recent={[]} recentState="ready"
      onRetry={vi.fn()} onNavigate={vi.fn()} guardianEvents={null} />)
    fireEvent.click(screen.getByRole('button', { name: '알림 0개' }))
    expect(await screen.findByText(/보호자 연결 알림을 불러오지 못했어요/)).toBeTruthy()
  })
})

describe('보호자 인증 한계 안내·아이디 찾기', () => {
  it('tells the child that the code only approves the link, not legal guardianship', async () => {
    render(<StudentGuardianScreen onBack={vi.fn()} />)
    expect(await screen.findByText(new RegExp(GUARDIAN_LINK_SCOPE.slice(0, 20)))).toBeTruthy()
    expect(screen.getByText(/법정대리인 확인이 필요한 일은 할 수 없어요/)).toBeTruthy()
  })

  it('does not look up guardian accounts in find-ID (nothing about existence is revealed)', () => {
    render(<FindIdPage />)
    fireEvent.click(screen.getByRole('tab', { name: /보호자/ }))
    expect(screen.getByText('보호자 계정은 아이디(이메일) 찾기를 아직 지원하지 않아요')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '아이디 찾기' })).toBeNull()
    expect(screen.queryByLabelText(/이름/)).toBeNull()
    expect(findId).not.toHaveBeenCalled()
    // 학생·선생님 탭은 기존대로 이름·센터로 찾는다
    fireEvent.click(screen.getByRole('tab', { name: /학생/ }))
    expect(screen.getByRole('button', { name: '아이디 찾기' })).toBeTruthy()
  })
})
