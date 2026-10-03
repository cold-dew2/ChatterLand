import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const history = vi.fn()
vi.mock('@/features/student/api/studentApi', () => ({ studentApi: { history: (...a: unknown[]) => history(...a) } }))

import StudentHistoryScreen from '@/features/student/components/StudentHistoryScreen'

beforeEach(() => history.mockReset())

describe('StudentHistoryScreen empty states', () => {
  it('explains an empty history and a filter with no matching records differently', async () => {
    history.mockResolvedValue({ content: [], totalPages: 0, totalElements: 0 })
    render(<StudentHistoryScreen />)
    expect(await screen.findByText('기록이 없어요')).toBeTruthy()
    fireEvent.click(screen.getByRole('tab', { name: '대화' }))
    expect(await screen.findByText('대화 기록이 없어요')).toBeTruthy()
    expect(history).toHaveBeenLastCalledWith('ai', 0, 3)
  })
})
