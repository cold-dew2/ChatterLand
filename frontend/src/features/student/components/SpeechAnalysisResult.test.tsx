import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import SpeechAnalysisResult from '@/features/student/components/SpeechAnalysisResult'
import type { SpeechAnalysis } from '@/features/student/types'

const completed: SpeechAnalysis = {
  analysisId: 'a1', status: 'COMPLETED', evaluationMode: 'SENTENCE_MATCH', targetText: '라디오', transcript: '라디오',
  matchRate: 100, textMatchRate: 100, analysisType: 'WORD', assessmentStatus: 'NO_CANDIDATES', holdReasons: [],
  pronunciationStatus: 'NOT_EVALUATED', comparison: { words: [{ type: 'MATCH', expected: '라디오', recognized: '라디오' }] },
}

describe('SpeechAnalysisResult', () => {
  it('shows text match rate, word type and leaves pronunciation metrics as 미평가', () => {
    render(<SpeechAnalysisResult analysis={completed} />)
    expect(screen.getByText('텍스트 일치율')).toBeTruthy()
    expect(screen.getByText('낱말')).toBeTruthy()
    expect(screen.getAllByText('미평가')).toHaveLength(3)
    expect(screen.getByText(/낱말 하나는 컴퓨터가 다르게 알아듣는 경우가 많아요/)).toBeTruthy()
    expect(screen.queryByText('판정 보류')).toBeNull()
    expect(screen.queryByText(/발음 정확도 \d+/)).toBeNull()
  })

  it('distinguishes a held result from a normal result', () => {
    render(<SpeechAnalysisResult analysis={{ ...completed, analysisType: 'SENTENCE', targetText: '오늘은 날씨가 좋아요.', assessmentStatus: 'HOLD', holdReasons: ['SPEECH_CUT_OFF_END'] }} />)
    expect(screen.getByText('판정 보류', { selector: 'span' })).toBeTruthy()
    expect(screen.getByText(/말이 끝나기 전에 녹음이 멈췄어요/)).toBeTruthy()
    expect(screen.queryByText(/낱말 하나는/)).toBeNull()
  })

  it('therapy learners see teacher review pending, no text match rate, and hold guidance', () => {
    render(<SpeechAnalysisResult analysis={{ ...completed, evaluationMode: 'PRONUNCIATION_REVIEW', matchRate: null, textMatchRate: null, reviewStatus: 'PENDING', assessmentStatus: 'HOLD', holdReasons: ['LOW_VOLUME'] }} />)
    expect(screen.getByText('선생님 확인 대기')).toBeTruthy()
    expect(screen.queryByText('텍스트 일치율')).toBeNull()
    expect(screen.getByText(/소리가 작게 녹음됐어요/)).toBeTruthy()
  })

  it('shows only provider values for the external engine and never invents scores', () => {
    render(<SpeechAnalysisResult analysis={{ analysisId: 'x', status: 'COMPLETED', evaluationMode: null, overallScore: 80, pronunciationScore: 70 }} />)
    expect(screen.getByText('외부 분석 참고 점수')).toBeTruthy()
    expect(screen.getAllByText('미평가')).toHaveLength(2)
  })
})
