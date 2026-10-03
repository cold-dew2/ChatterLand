import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { TeacherSpeechAnalysis } from '@/features/teacher/types'

const speechAnalyses = vi.fn()
const reviewSpeechAnalysis = vi.fn()
vi.mock('@/features/teacher/api/teacherApi', () => ({
  teacherApi: {
    speechAnalyses: (...args: unknown[]) => speechAnalyses(...args),
    reviewSpeechAnalysis: (...args: unknown[]) => reviewSpeechAnalysis(...args),
    speechAnalysisAudio: vi.fn(),
  },
}))

import SpeechReviewPanel from '@/features/teacher/components/SpeechReviewPanel'

const analysis: TeacherSpeechAnalysis = {
  analysisId: 'an-1', studentId: 7, status: 'COMPLETED', evaluationMode: 'PRONUNCIATION_REVIEW', exerciseTitle: 'ㄹ 발음',
  targetText: '라디오', transcript: '타디오', reviewStatus: 'PENDING', pronunciationStatus: 'NOT_EVALUATED', hasAudio: true,
  analysisType: 'WORD', assessmentStatus: 'ERROR_CANDIDATES', holdReasons: [], analysisVersion: 'jamo-align-v1', modelName: 'ggml-large-v3-turbo-q5_0.bin',
  targetPhonemes: ['ㄹ'], targetPositions: [{ phoneme: 'ㄹ', wordIndex: 1, word: '라디오', syllableIndex: 1, syllable: '라', slot: 'ONSET', wordPosition: 'INITIAL' }],
  phonemeCandidates: [{ type: 'SUBSTITUTION', slot: 'ONSET', expected: 'ㄹ', produced: 'ㅌ', wordIndex: 1, word: '라디오', syllableIndex: 1,
    targetSyllable: '라', recognizedSyllable: '타', wordPosition: 'INITIAL', targetPhoneme: true }],
  speechTiming: { source: 'VAD', audioMs: 3000, speechMs: 600, leadingSilenceMs: 500, trailingSilenceMs: 1900, pauseCount: 0, longestPauseMs: 0, syllablesPerSecond: 5.26 },
  repetition: { previousAttempts: 0, sameTranscriptCount: 0, recurringCandidates: [], recent: [] },
  teacherConfirmedErrors: null,
}

beforeEach(() => { speechAnalyses.mockReset(); reviewSpeechAnalysis.mockReset() })

describe('SpeechReviewPanel', () => {
  it('shows automatic evidence as unconfirmed candidates, separate from the teacher result', async () => {
    speechAnalyses.mockResolvedValue({ content: [analysis], totalPages: 1, totalElements: 1 })
    render(<SpeechReviewPanel studentId={7} />)
    expect(await screen.findByText('자동 오류 후보 있음')).toBeTruthy()
    expect(screen.getByText('오류 후보 (자동 · 미확정)')).toBeTruthy()
    expect(screen.getByText(/라디오 1번째 음절 '라' 어두 초성/)).toBeTruthy()
    expect(screen.getByText(/말소리 0.6초/)).toBeTruthy()
    expect(screen.queryByText('선생님 확정 오류', { selector: 'p' })).toBeNull()
  })

  it('requires a judgement and sends only the errors the teacher confirmed (candidate + manual)', async () => {
    speechAnalyses.mockResolvedValue({ content: [analysis], totalPages: 1, totalElements: 1 })
    reviewSpeechAnalysis.mockResolvedValue({ ...analysis, reviewStatus: 'REVIEWED', teacherJudgement: 'NEEDS_PRACTICE',
      teacherConfirmedErrors: [{ phoneme: 'ㄹ', errorType: 'SUBSTITUTION', produced: 'ㅌ', position: '라디오 1번째 음절' }] })
    render(<SpeechReviewPanel studentId={7} />)
    await screen.findByText('자동 오류 후보 있음')
    fireEvent.click(screen.getByRole('button', { name: '검토 완료' }))
    expect(await screen.findByText('검토 결과를 선택해 주세요.')).toBeTruthy()
    expect(reviewSpeechAnalysis).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('checkbox', { name: /ㄹ → ㅌ/ }))
    fireEvent.change(screen.getByLabelText('음소'), { target: { value: 'ㄷ' } })
    fireEvent.click(screen.getByRole('button', { name: '추가' }))
    fireEvent.change(screen.getByLabelText(/선생님 검토 결과/), { target: { value: 'NEEDS_PRACTICE' } })
    fireEvent.click(screen.getByRole('button', { name: '검토 완료' }))
    await waitFor(() => expect(reviewSpeechAnalysis).toHaveBeenCalledOnce())
    expect(reviewSpeechAnalysis).toHaveBeenCalledWith('an-1', {
      judgement: 'NEEDS_PRACTICE', note: undefined,
      confirmedErrors: [
        { phoneme: 'ㄹ', errorType: 'SUBSTITUTION', produced: 'ㅌ', position: '라디오 1번째 음절' },
        { phoneme: 'ㄷ', errorType: 'DISTORTION', produced: null, position: null },
      ],
    })
    expect(await screen.findByText('검토 결과를 저장했어요.')).toBeTruthy()
  })

  it('shows an error state with retry when loading fails', async () => {
    speechAnalyses.mockRejectedValueOnce(new Error('서버 오류')).mockResolvedValueOnce({ content: [], totalPages: 0, totalElements: 0 })
    render(<SpeechReviewPanel studentId={7} />)
    expect((await screen.findByRole('alert')).textContent).toContain('서버 오류')
    fireEvent.click(screen.getByRole('button', { name: /다시/ }))
    expect(await screen.findByText('검토할 녹음이 없어요')).toBeTruthy()
  })
})
