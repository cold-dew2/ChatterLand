import { describe, expect, it } from 'vitest'
import type { PhonemeCandidate, SpeechAnalysis } from '@/features/student/types'
import { candidateText, holdReasonLabel, isWordAnalysis, positionText, textMatchRate } from '@/features/student/utils/speechAssessment'

const base: SpeechAnalysis = { analysisId: 'a', status: 'COMPLETED' }

describe('speech assessment display helpers', () => {
  it('reads textMatchRate and falls back to legacy matchRate only for the same measurement', () => {
    expect(textMatchRate({ ...base, textMatchRate: 66.67 })).toBe(66.67)
    expect(textMatchRate({ ...base, matchRate: 100 })).toBe(100)
    expect(textMatchRate({ ...base, pronunciationScore: 90 })).toBeNull()
  })

  it('uses analysisType from the server, with a word-count fallback', () => {
    expect(isWordAnalysis({ ...base, analysisType: 'SENTENCE', targetText: '라디오' })).toBe(false)
    expect(isWordAnalysis({ ...base, targetText: '라디오.' })).toBe(true)
    expect(isWordAnalysis({ ...base, targetText: '오늘은 날씨가 좋아요.' })).toBe(false)
  })

  it('describes candidates as unconfirmed candidates with position', () => {
    const candidate: PhonemeCandidate = { type: 'SUBSTITUTION', slot: 'ONSET', expected: 'ㄹ', produced: 'ㅌ', wordIndex: 1, word: '라디오',
      syllableIndex: 1, targetSyllable: '라', recognizedSyllable: '타', wordPosition: 'INITIAL', targetPhoneme: true }
    expect(candidateText(candidate)).toBe('라디오 1번째 음절 (어두) · 초성 대치 후보 ㄹ → ㅌ')
    expect(candidateText({ ...candidate, type: 'SYLLABLE_ADDITION', slot: 'SYLLABLE', expected: null, produced: '요', word: null, syllableIndex: null, wordPosition: null }))
      .toBe('목표에 없는 위치 · 음절 음절 첨가 후보 ∅ → 요')
  })

  it('formats target phoneme positions and has labels for every server hold reason', () => {
    expect(positionText({ phoneme: 'ㄹ', wordIndex: 1, word: '불', syllableIndex: 1, syllable: '불', slot: 'CODA', wordPosition: 'FINAL' }))
      .toBe("불 1번째 음절 '불' 어말 종성")
    for (const reason of ['SPEECH_CUT_OFF_END', 'SPEECH_CUT_OFF_START', 'SPEECH_TOO_SHORT', 'CLIPPING', 'LOW_VOLUME', 'NON_HANGUL_TRANSCRIPT', 'LENGTH_MISMATCH'])
      expect(holdReasonLabel[reason]).toBeTruthy()
  })
})
