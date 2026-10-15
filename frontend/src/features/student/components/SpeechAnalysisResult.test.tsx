import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import SpeechAnalysisResult from '@/features/student/components/SpeechAnalysisResult'
import type { SpeechAnalysis, SpeechMetrics } from '@/features/student/types'

const completed: SpeechAnalysis = {
  analysisId: 'a1', status: 'COMPLETED', evaluationMode: 'SENTENCE_MATCH', targetText: '라디오', transcript: '라디오',
  matchRate: 100, textMatchRate: 100, analysisType: 'WORD', assessmentStatus: 'NO_CANDIDATES', holdReasons: [],
  pronunciationStatus: 'NOT_EVALUATED', comparison: { words: [{ type: 'MATCH', expected: '라디오', recognized: '라디오' }] },
}

const sentence: SpeechAnalysis = { ...completed, analysisType: 'SENTENCE', targetText: '오늘은 날씨가 좋아요.', transcript: '오늘은 날씨가 좋아요.' }
const measured: SpeechMetrics = {
  version: 'speech-metrics-v1',
  pronunciation: { status: 'NOT_SUPPORTED', reason: 'NO_PRONUNCIATION_MODEL' },
  speakingRate: { status: 'MEASURED', syllables: 9, syllableBasis: 'ASR_TRANSCRIPT_SYLLABLES', utteranceMs: 2300, speechMs: 1720, syllablesPerSecond: 3.91, articulationRate: 5.23 },
  pauses: { status: 'MEASURED', pauseCount: 1, totalPauseMs: 580, longestPauseMs: 580, pauseRatio: 0.252, speechSegmentCount: 2, pauseThresholdMs: 250 },
}
const notMeasured = (status: 'NOT_SUPPORTED' | 'UNAVAILABLE', reason: string): SpeechMetrics => ({
  ...measured, speakingRate: { status, reason }, pauses: { status, reason, pauseThresholdMs: 250 },
})

describe('SpeechAnalysisResult', () => {
  it('shows measured speaking rate and pauses as values, not scores, and pronunciation analysis as 준비 중', () => {
    render(<SpeechAnalysisResult analysis={{ ...sentence, speechMetrics: measured }} />)
    expect(screen.getByText('초당 3.9글자')).toBeTruthy()
    expect(screen.getByText('9글자를 2.3초 동안 말했어요')).toBeTruthy()
    expect(screen.getByText('1번 멈췄어요')).toBeTruthy()
    expect(screen.getByText('가장 길게 0.6초')).toBeTruthy()
    expect(screen.getByText('준비 중')).toBeTruthy()
    expect(screen.getByText('발음을 분석하는 기능을 준비하고 있어요.')).toBeTruthy()
    expect(screen.getByText('현재는 발음 정확도 점수를 제공하지 않아요.')).toBeTruthy()
    expect(screen.getByText(/점수나 유창성 평가가 아니에요. 멈춤은 0.25초 넘게 쉰 곳을 셌어요/)).toBeTruthy()
    expect(screen.queryByText('미평가')).toBeNull()
    // 측정값은 점수 막대로 그리지 않는다(막대는 텍스트 일치율 하나뿐)
    expect(screen.getAllByRole('progressbar').map((bar) => bar.getAttribute('aria-label'))).toEqual(['텍스트 일치율 100%'])
    expect(screen.getByText('텍스트 일치율')).toBeTruthy()
  })

  it('shows "no pauses" when none were measured', () => {
    render(<SpeechAnalysisResult analysis={{ ...sentence, speechMetrics: { ...measured, pauses: { ...measured.pauses, pauseCount: 0, totalPauseMs: 0, longestPauseMs: 0 } } }} />)
    expect(screen.getByText('멈춤 없음')).toBeTruthy()
    expect(screen.queryByText(/가장 길게/)).toBeNull()
  })

  it('separates unsupported items from recordings that could not be measured, with the reason', () => {
    const { unmount } = render(<SpeechAnalysisResult analysis={{ ...completed, speechMetrics: notMeasured('NOT_SUPPORTED', 'WORD_ITEM') }} />)
    expect(screen.getAllByText('해당 없음')).toHaveLength(2) // 낱말 문항의 속도·멈춤
    expect(screen.getAllByText('준비 중')).toHaveLength(1) // 발음 분석은 문항과 관계없이 준비 중
    expect(screen.getAllByText('낱말 하나는 재지 않아요')).toHaveLength(2)
    unmount()
    for (const [reason, text] of [['ASSESSMENT_HOLD', '녹음이 또렷하지 않아 재지 않았어요'], ['VAD_UNAVAILABLE', '말소리 구간을 확인하지 못했어요']] as const) {
      const view = render(<SpeechAnalysisResult analysis={{ ...sentence, speechMetrics: notMeasured('UNAVAILABLE', reason) }} />)
      expect(screen.getAllByText('측정 불가')).toHaveLength(2)
      expect(screen.queryByText('해당 없음')).toBeNull()
      expect(screen.getAllByText(text)).toHaveLength(2)
      expect(screen.queryByText(/초당/)).toBeNull()
      view.unmount()
    }
    render(<SpeechAnalysisResult analysis={{ ...sentence, speechMetrics: { ...measured, speakingRate: { status: 'UNAVAILABLE', reason: 'UTTERANCE_TOO_SHORT', syllables: 3 } } }} />)
    expect(screen.getByText('말이 짧아서 재지 않았어요')).toBeTruthy()
    expect(screen.getByText('1번 멈췄어요')).toBeTruthy()
  })

  it('shows text match rate and word type; an older analysis without measurements shows 기록 없음, not invented values', () => {
    render(<SpeechAnalysisResult analysis={completed} />)
    expect(screen.getByText('텍스트 일치율')).toBeTruthy()
    expect(screen.getByText('낱말')).toBeTruthy()
    expect(screen.queryByText('미평가')).toBeNull()
    expect(screen.getByText('준비 중')).toBeTruthy()
    expect(screen.getAllByText('기록 없음')).toHaveLength(2)
    expect(screen.queryByText(/초당/)).toBeNull()
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
    expect(screen.getByText('준비 중')).toBeTruthy() // 자동 발음 분석은 준비 중(선생님 확인과 별개)
    expect(screen.queryByText('미평가')).toBeNull()
  })

  it('shows only provider values for the external engine and never invents scores', () => {
    render(<SpeechAnalysisResult analysis={{ analysisId: 'x', status: 'COMPLETED', evaluationMode: null, overallScore: 80, pronunciationScore: 70 }} />)
    expect(screen.getByText('외부 분석 참고 점수')).toBeTruthy()
    expect(screen.getAllByText('미평가')).toHaveLength(2)
  })
})
