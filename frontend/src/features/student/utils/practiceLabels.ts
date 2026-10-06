import type { ContentType, Difficulty, PronunciationRule } from '@/features/student/types'

/** 연습 콘텐츠 분류 표시 이름(학생 연습 찾기·교사 숙제 만들기 공통). 발음 분류는 글자 기준이며 학생 발음 평가가 아니다. */
export const ruleLabel: Record<PronunciationRule, string> = {
  BASIC_CONSONANT: '기본 자음', BASIC_VOWEL: '기본 모음', CODA: '받침', LIAISON: '연음', NASALIZATION: '비음화',
  TENSIFICATION: '된소리', PALATALIZATION: '구개음화', ASPIRATION: '격음화', CONSONANT_ASSIMILATION: '유음화(자음동화)', COMPREHENSIVE: '종합 발음',
}
export const difficultyLabel: Record<Difficulty, string> = { BEGINNER: '초급', INTERMEDIATE: '중급', ADVANCED: '고급' }
export const contentTypeLabel: Record<ContentType, string> = { WORD: '낱말', SHORT_SENTENCE: '짧은 문장', LONG_SENTENCE: '긴 문장' }

/** 연습 기록 유형. PRACTICE는 유형을 구분하기 전에 저장된 기존 기록(자율·수업 출처 모름) */
export type AttemptType = 'SELF' | 'LESSON' | 'HOMEWORK' | 'PRACTICE'
export const attemptTypeLabel: Record<AttemptType, string> = { SELF: '자율 연습', LESSON: '수업 연습', HOMEWORK: '숙제 연습', PRACTICE: '이전 기록' }

/** 연습 영역(practice_categories.category_id) 표시 이름. 서버 목록을 쓸 수 없는 선생님 화면의 검색 필터에서 쓴다 */
export const practiceCategoryLabel: Record<string, string> = { articulation: '발음', vocabulary: '어휘력', fluency: '유창성', expression: '표현력', comprehension: '이해력' }
export const practiceCategoryOptions = Object.entries(practiceCategoryLabel).map(([value, label]) => ({ value, label }))
export const ruleOptions = Object.entries(ruleLabel).map(([value, label]) => ({ value, label }))
export const difficultyOptions = Object.entries(difficultyLabel).map(([value, label]) => ({ value, label }))
export const contentTypeOptions = Object.entries(contentTypeLabel).map(([value, label]) => ({ value, label }))
