import type { AnalysisType, PhonemeCandidate, PhonemePosition, SpeechAnalysis } from '@/features/student/types'

export const analysisTypeLabel: Record<AnalysisType, string> = { WORD: '낱말', SENTENCE: '문장' }

/** 판정 보류 이유(선생님 화면용) */
export const holdReasonLabel: Record<string, string> = {
  SPEECH_CUT_OFF_END: '말하는 도중에 녹음이 끝났어요',
  SPEECH_CUT_OFF_START: '말을 시작한 뒤에 녹음이 시작됐어요',
  SPEECH_TOO_SHORT: '말소리 구간이 너무 짧아요',
  CLIPPING: '소리가 너무 커서 찌그러졌어요',
  LOW_VOLUME: '소리가 너무 작아요',
  NON_HANGUL_TRANSCRIPT: '인식 결과에 숫자·영문이 있어 비교가 어려워요',
  LENGTH_MISMATCH: '인식된 길이가 목표와 많이 달라요(반복·다른 말·일부만 녹음 가능)',
}

const slotLabel: Record<string, string> = { ONSET: '초성', NUCLEUS: '중성', CODA: '종성', SYLLABLE: '음절' }
const wordPositionLabel: Record<string, string> = { INITIAL: '어두', MEDIAL: '어중', FINAL: '어말' }
const candidateTypeLabel: Record<string, string> = {
  SUBSTITUTION: '대치', OMISSION: '생략', ADDITION: '첨가', SYLLABLE_OMISSION: '음절 생략', SYLLABLE_ADDITION: '음절 첨가',
}

export const errorTypeLabel: Record<string, string> = { SUBSTITUTION: '대치', OMISSION: '생략', DISTORTION: '왜곡', ADDITION: '첨가' }

export function positionText(position: PhonemePosition) {
  return `${position.word} ${position.syllableIndex}번째 음절 '${position.syllable}' ${wordPositionLabel[position.wordPosition] ?? ''} ${slotLabel[position.slot] ?? ''}`.replace(/\s+/g, ' ').trim()
}

export function candidateText(candidate: PhonemeCandidate) {
  const where = candidate.word ? `${candidate.word} ${candidate.syllableIndex}번째 음절${candidate.wordPosition ? ` (${wordPositionLabel[candidate.wordPosition] ?? candidate.wordPosition})` : ''}` : '목표에 없는 위치'
  const change = `${candidate.expected ?? '∅'} → ${candidate.produced ?? '∅'}`
  return `${where} · ${slotLabel[candidate.slot] ?? candidate.slot} ${candidateTypeLabel[candidate.type] ?? candidate.type} 후보 ${change}`
}

/** 텍스트 일치율. 이전 응답(matchRate만 있는 경우)과도 호환한다. */
export function textMatchRate(analysis: SpeechAnalysis): number | null {
  const value = analysis.textMatchRate ?? analysis.matchRate
  return typeof value === 'number' ? value : null
}

export function isWordAnalysis(analysis: SpeechAnalysis) {
  if (analysis.analysisType) return analysis.analysisType === 'WORD'
  return !/\s/.test((analysis.targetText ?? '').replace(/[^\p{L}\p{N}\s]/gu, '').trim())
}
