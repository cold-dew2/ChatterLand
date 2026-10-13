import { useState } from 'react'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Mic } from 'lucide-react'
import ContentBrowser from '@/features/practiceContent/components/ContentBrowser'
import type { SelectedContent } from '@/features/practiceContent/utils/selection'
import type { Exercise } from '@/features/student/types'

const TOTAL = 45
const SIZE = 20
const words = ['사과', '학교', '친구', '사과나무', '사과를 먹어요']
const item = (n: number): Exercise => ({ id: String(100 + n), label: n < words.length ? words[n] : `낱말 세트 ${n + 1}`, color: '#3e7a47', instruction: '', inputType: 'mic', items: [] })
/** 서버 검색 흉내: 검색어가 들어간 항목만, 쪽 단위로 */
const fetchPage = vi.fn(async (page: number, keyword: string) => {
  const all = Array.from({ length: TOTAL }, (_, n) => item(n)).filter((entry) => !keyword || entry.label.includes(keyword))
  return { items: all.slice(page * SIZE, page * SIZE + SIZE), total: all.length }
})

function Browser({ onOpen = vi.fn(), initialSelect = false }: { onOpen?: (exercise: Exercise, index: number, keyword: string) => void; initialSelect?: boolean }) {
  const [selection, setSelection] = useState<SelectedContent[]>([])
  const [selectMode, setSelectMode] = useState(initialSelect)
  return <>
    <ContentBrowser sourceKey="test" fetchPage={fetchPage} pageSize={SIZE} selectMode={selectMode} onSelectModeChange={setSelectMode}
      selection={selection} onSelectionChange={setSelection} onOpen={onOpen} icon={Mic} color={() => '#3e7a47'}
      emptyTitle="비어 있어요" listLabel="테스트 목록" />
    <p data-testid="picked">{selection.map((entry) => entry.label).join(',')}</p>
  </>
}

describe('ContentBrowser', () => {
  it('서버 검색으로 실제 개수를 보여 주고, 검색어를 지우면 전체 목록으로 돌아간다', async () => {
    render(<Browser />)
    expect(await screen.findByText(`전체 ${TOTAL}개`)).toBeTruthy()
    fireEvent.change(screen.getByLabelText('콘텐츠 검색'), { target: { value: '사과' } })
    fireEvent.click(screen.getByRole('button', { name: '검색' }))
    expect(await screen.findByText('검색 결과 3개')).toBeTruthy()
    expect(fetchPage).toHaveBeenLastCalledWith(0, '사과')
    fireEvent.click(screen.getByRole('button', { name: '검색어 사과 지우기' }))
    expect(await screen.findByText(`전체 ${TOTAL}개`)).toBeTruthy()
  })

  it('맨 끝 쪽으로 바로 이동하고, 더 보기·앞쪽 항목 보기로 이어서 볼 수 있다', async () => {
    render(<Browser />)
    await screen.findByText(`전체 ${TOTAL}개`)
    fireEvent.click(screen.getByRole('button', { name: '3쪽' }))
    expect(await screen.findByRole('button', { name: /^45 낱말 세트 45/ })).toBeTruthy()
    expect(screen.getAllByRole('listitem')).toHaveLength(5)
    fireEvent.click(screen.getByRole('button', { name: /앞쪽 항목 보기/ }))
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(25))
    expect(screen.getByRole('button', { name: /^21 낱말 세트 21/ })).toBeTruthy()
  })

  it('일반 모드에서는 카드를 누르면 바로 연다(전체 순서 위치·검색어와 함께)', async () => {
    const onOpen = vi.fn()
    render(<Browser onOpen={onOpen} />)
    fireEvent.click(await screen.findByRole('button', { name: /^2 학교/ }))
    expect(onOpen).toHaveBeenCalledWith(expect.objectContaining({ label: '학교' }), 1, '')
  })

  it('최대 30개: 전체 선택은 30개까지만 담고 못 담은 개수를 알리며, 고르지 않은 카드는 막는다', async () => {
    render(<Browser initialSelect />)
    await screen.findByText(`전체 ${TOTAL}개`)
    fireEvent.click(screen.getByRole('button', { name: '현재 목록 전체 선택' }))
    expect(screen.getByText('선택 20개')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /더 보기/ }))
    await screen.findByRole('button', { name: '낱말 세트 40 선택' })
    fireEvent.click(screen.getByRole('button', { name: '현재 목록 전체 선택' }))
    expect(screen.getByText('선택 30개')).toBeTruthy()
    expect(screen.getByText(/한 번에 30개까지 고를 수 있어요\. 10개는 담지 못했어요\./)).toBeTruthy()
    expect((screen.getByRole('button', { name: '낱말 세트 31 선택' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: '현재 목록 전체 선택' }) as HTMLButtonElement).disabled).toBe(true)
    // 고른 것은 계속 뺄 수 있고, 빼면 다시 고를 수 있다
    fireEvent.click(screen.getByRole('button', { name: '학교 선택' }))
    expect(screen.getByText('선택 29개')).toBeTruthy()
    expect((screen.getByRole('button', { name: '낱말 세트 31 선택' }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('선택 모드에서는 카드를 눌러도 열지 않고, 쪽·검색어가 바뀌어도 고른 순서가 유지된다', async () => {
    const onOpen = vi.fn()
    render(<Browser onOpen={onOpen} />)
    await screen.findByText(`전체 ${TOTAL}개`)
    fireEvent.click(screen.getByRole('button', { name: '선택하기' }))
    fireEvent.click(screen.getByRole('button', { name: '학교 선택' }))
    expect(onOpen).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '학교 선택' }).getAttribute('aria-pressed')).toBe('true')
    fireEvent.click(screen.getByRole('button', { name: /더 보기/ }))
    fireEvent.click(await screen.findByRole('button', { name: '낱말 세트 25 선택' }))
    fireEvent.change(screen.getByLabelText('콘텐츠 검색'), { target: { value: '사과' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '검색' })) })
    fireEvent.click(await screen.findByRole('button', { name: '사과나무 선택' }))
    expect(screen.getByTestId('picked').textContent).toBe('학교,낱말 세트 25,사과나무')
    expect(screen.getByText('선택 3개')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '현재 목록 전체 선택' }))
    expect(screen.getByTestId('picked').textContent).toBe('학교,낱말 세트 25,사과나무,사과,사과를 먹어요')
    fireEvent.click(screen.getByRole('button', { name: '선택 해제' }))
    expect(screen.getByTestId('picked').textContent).toBe('')
  })
})
