import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/client'

const speechFeedback = vi.fn()
vi.mock('@/features/teacher/api/teacherApi', () => ({ teacherApi: { speechFeedback: (...a: unknown[]) => speechFeedback(...a) } }))

import TeacherAiFeedback from '@/features/teacher/components/TeacherAiFeedback'

const ready = { analysisId: 'a-1', status: 'READY', source: 'AI', text: '신라를 끝까지 말했어요! ㄴ이 ㄹ로 소리 나요 [S1].', basedOn: ['TEACHER_CONFIRMED'],
  generatedAt: '2026-10-06T09:00:00', outdated: false, sources: [{ marker: 'S1', cited: true, chunkId: 'std-pron-20', title: '표준 발음법', location: '제5장 제20항' }] }

beforeEach(() => speechFeedback.mockReset())

describe('TeacherAiFeedback', () => {
  it('loads only when opened and shows the same stored explanation with sources, labelled as not a score or diagnosis', async () => {
    speechFeedback.mockResolvedValue(ready)
    render(<TeacherAiFeedback analysisId="a-1" />)
    expect(speechFeedback).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: /학생이 본 AI 설명 보기/ }))
    expect(await screen.findByText(ready.text)).toBeTruthy()
    expect(screen.getByText('점수·진단 아님')).toBeTruthy()
    expect(screen.getByText(/제5장 제20항/)).toBeTruthy()
    expect(screen.getByText(/선생님 확인 결과/)).toBeTruthy()
    expect(screen.getByText('만든 시각 2026-10-06 09:00:00')).toBeTruthy()
    expect(speechFeedback).toHaveBeenCalledWith('a-1')
  })

  it('warns when the explanation was made before the teacher changed the judgement', async () => {
    speechFeedback.mockResolvedValue({ ...ready, outdated: true })
    render(<TeacherAiFeedback analysisId="a-1" />)
    fireEvent.click(screen.getByRole('button', { name: /학생이 본 AI 설명 보기/ }))
    expect(await screen.findByText(/이전 근거로 만든 설명이에요/)).toBeTruthy()
  })

  it('shows not-generated / insufficient-source reasons and errors with retry', async () => {
    speechFeedback.mockResolvedValueOnce({ analysisId: 'a-1', status: 'NOT_GENERATED', source: 'AI', reason: '학생이 아직 AI 설명을 만들지 않았어요.' })
    const first = render(<TeacherAiFeedback analysisId="a-1" />)
    fireEvent.click(screen.getByRole('button', { name: /학생이 본 AI 설명 보기/ }))
    expect(await screen.findByText('학생이 아직 AI 설명을 만들지 않았어요.')).toBeTruthy()
    first.unmount()

    speechFeedback.mockRejectedValueOnce(new ApiError('담당 학생의 분석 결과를 찾을 수 없습니다.', 404)).mockResolvedValueOnce({ analysisId: 'a-1', status: 'INSUFFICIENT_SOURCES', source: 'AI', reason: '검수된 교육 자료가 없어요.' })
    render(<TeacherAiFeedback analysisId="a-1" />)
    fireEvent.click(screen.getByRole('button', { name: /학생이 본 AI 설명 보기/ }))
    expect(await screen.findByText('담당 학생의 분석 결과를 찾을 수 없습니다.')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText(/근거 자료 부족/)).toBeTruthy()
  })
})
