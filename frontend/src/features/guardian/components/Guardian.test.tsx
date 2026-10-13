import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/client'

const guardian = { link: vi.fn(), child: vi.fn(), report: vi.fn(), history: vi.fn(), unlink: vi.fn() }
vi.mock('@/features/guardian/api/guardianApi', () => ({ guardianApi: new Proxy({}, { get: (_, key: string) => (...a: unknown[]) => (guardian as Record<string, (...x: unknown[]) => unknown>)[key](...a) }) }))
const student = { createGuardianLinkCode: vi.fn(), guardians: vi.fn(), unlinkGuardian: vi.fn() }
vi.mock('@/features/student/api/studentApi', () => ({ studentApi: new Proxy({}, { get: (_, key: string) => (...a: unknown[]) => (student as Record<string, (...x: unknown[]) => unknown>)[key](...a) }) }))

import GuardianChildDetail from '@/features/guardian/components/GuardianChildDetail'
import LinkChildForm from '@/features/guardian/components/LinkChildForm'
import StudentGuardianScreen from '@/features/student/components/StudentGuardianScreen'
import { homePathFor, safeReturnPath } from '@/features/auth/utils/returnPath'

beforeEach(() => {
  Object.values(guardian).forEach((fn) => fn.mockReset())
  Object.values(student).forEach((fn) => fn.mockReset())
})

describe('보호자: 자녀 연결', () => {
  it('needs the 8-character code from the child account and never sends a student id', async () => {
    const onLinked = vi.fn()
    guardian.link.mockRejectedValueOnce(new ApiError('연결 코드가 맞지 않거나 시간이 지났어요.', 400))
      .mockResolvedValueOnce({ studentId: 7, name: '김하늘', age: 8, centerName: '센터', linkedOn: '2026-10-13', linkStatus: 'ACTIVE' })
    render(<LinkChildForm onLinked={onLinked} />)
    fireEvent.change(screen.getByLabelText('연결 코드'), { target: { value: 'abc' } })
    fireEvent.click(screen.getByRole('button', { name: '자녀 연결' }))
    expect(screen.getByText('연결 코드 8자리를 입력해 주세요.')).toBeTruthy()
    expect(guardian.link).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('연결 코드'), { target: { value: 'abcd-2345' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '자녀 연결' })) })
    expect(guardian.link).toHaveBeenCalledWith('ABCD-2345')
    expect(screen.getByText('연결 코드가 맞지 않거나 시간이 지났어요.')).toBeTruthy()
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '자녀 연결' })) })
    expect(onLinked).toHaveBeenCalledWith(expect.objectContaining({ studentId: 7 }))
    expect(screen.getByText(/보호자 본인 확인이나 법정대리인 증명 절차는 아직 제공하지 않아요/)).toBeTruthy()
  })

  it('shows link and consent status read-only, the shared report, history, and unlinks after confirmation', async () => {
    guardian.child.mockResolvedValue({ studentId: 7, name: '김하늘', age: 8, centerName: '해솔 센터', linkedOn: '2026-10-13', linkStatus: 'ACTIVE',
      consents: [{ type: 'VOICE', agreed: true, policyVersion: 'v', changedOn: '2026-10-01' }, { type: 'AI_CHAT', agreed: false, policyVersion: 'v', changedOn: null }] })
    guardian.report.mockResolvedValue({ period: '30d', startDate: '2026-09-14', endDate: '2026-10-13', totalAttempts: 0, categories: [], daily: [], repeated: [], recentAttempts: [], comparison: null })
    guardian.history.mockResolvedValue({ content: [{ id: 1, type: 'word', title: 'ㄹ 발음', date: '2026-10-13' }], totalElements: 1 })
    guardian.unlink.mockResolvedValue(undefined)
    const onUnlinked = vi.fn()
    render(<GuardianChildDetail studentId={7} onBack={vi.fn()} onUnlinked={onUnlinked} />)
    expect(await screen.findByText('해솔 센터')).toBeTruthy()
    const consents = screen.getByRole('region', { name: '자녀 계정의 동의 상태' })
    expect(within(consents).getByText('동의')).toBeTruthy()
    expect(within(consents).getByText('미동의')).toBeTruthy()
    expect(within(consents).queryByRole('button')).toBeNull()
    expect(await screen.findByText('ㄹ 발음')).toBeTruthy()
    expect(guardian.report).toHaveBeenCalledWith(7, '30d')
    fireEvent.click(screen.getByRole('button', { name: '연결 해제' }))
    expect(guardian.unlink).not.toHaveBeenCalled()
    await act(async () => { fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '연결 해제' })) })
    expect(guardian.unlink).toHaveBeenCalledWith(7)
    expect(onUnlinked).toHaveBeenCalledOnce()
  })

  it('reports a child that is not (or no longer) linked as not found', async () => {
    guardian.child.mockRejectedValue(new ApiError('연결된 자녀 정보를 찾을 수 없습니다.', 404))
    render(<GuardianChildDetail studentId={99} onBack={vi.fn()} onUnlinked={vi.fn()} />)
    expect(await screen.findByText('연결된 자녀 정보를 찾을 수 없습니다.')).toBeTruthy()
    expect(guardian.report).not.toHaveBeenCalled()
    expect(guardian.history).not.toHaveBeenCalled()
  })
})

describe('학생: 보호자 연결', () => {
  it('makes a one-time code and unlinks a guardian after confirmation', async () => {
    student.guardians.mockResolvedValueOnce([{ guardianId: 3, name: '김보호', linkedOn: '2026-10-13' }]).mockResolvedValueOnce([])
    student.createGuardianLinkCode.mockResolvedValue({ code: 'ABCD-2345', expiresAt: '2026-10-13T19:30:00', validMinutes: 10 })
    student.unlinkGuardian.mockResolvedValue(undefined)
    render(<StudentGuardianScreen onBack={vi.fn()} />)
    expect(await screen.findByText('김보호')).toBeTruthy()
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '연결 코드 만들기' })) })
    expect(screen.getByText('ABCD-2345')).toBeTruthy()
    expect(screen.getByText('오후 7:30까지 한 번만 쓸 수 있어요')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '김보호 연결 해제' }))
    await act(async () => { fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '연결 해제' })) })
    expect(student.unlinkGuardian).toHaveBeenCalledWith(3)
    expect(await screen.findByText('아직 연결된 보호자가 없어요.')).toBeTruthy()
  })
})

describe('보호자 화면 경로', () => {
  it('sends each role to its own home and only allows its own paths', () => {
    expect(homePathFor('GUARDIAN')).toBe('/guardian')
    expect(safeReturnPath('/guardian/children/7', 'GUARDIAN')).toBe('/guardian/children/7')
    expect(safeReturnPath('/student/history', 'GUARDIAN')).toBeNull()
    expect(safeReturnPath('/guardian', 'STUDENT')).toBeNull()
    expect(safeReturnPath('/guardianx', 'GUARDIAN')).toBeNull()
  })
})
