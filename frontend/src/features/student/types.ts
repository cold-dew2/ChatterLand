export type ExerciseItem = { id: string; word: string; emoji: string }

export type Exercise = {
  id: string
  label: string
  color: string
  instruction: string
  items: ExerciseItem[]
  inputType: 'mic' | 'read' | 'speak'
  /** 연습 영역(articulation·vocabulary·fluency·expression·comprehension). 영역 목록에서 받은 세트에만 있다 */
  categoryId?: string
}

export type Session = { id: string; title: string; date: string; done: boolean; exercises: Exercise[] }

export type Homework = { id: number; title: string; type: string; description: string; dueDate: string; done: boolean; targetMinutes: number
  /** 선생님이 지정한 연습 세트(없으면 자유 숙제) */
  exerciseId?: number | null; exerciseTitle?: string | null; attemptCount?: number
  /** 숙제로 낸 연습 콘텐츠들(지정 순서). 없으면 자유 숙제 */
  exercises?: PracticeSelection[]
  /** 숙제 버전(선생님이 수정하면 늘어난다)·등록일. 숙제 알림에 쓴다 */
  version?: number; createdAt?: string }

export type PracticeCategory = { id: string; label: string; color: string; desc: string; exercises: Exercise[] }

/** sequential: 영역 전체 순서 · random: 랜덤 · retry: 다시 연습 · selected: 고른 콘텐츠(고른 순서, 숙제 콘텐츠 포함) · search: 검색 결과 순서 */
export type PracticeRunMode = 'sequential' | 'random' | 'retry' | 'selected' | 'search'

/** 선택 연습에 쓰는 최소 정보(ID와 이름만) */
export type PracticeSelection = { id: string; label: string; categoryName?: string }

export type EvaluationMode = 'SENTENCE_MATCH' | 'PRONUNCIATION_REVIEW' | 'EXTERNAL_PROVIDER'

export type SpeechWordDiff = {
  type: 'MATCH' | 'MISSING' | 'INSERTED' | 'SUBSTITUTED'
  expected: string | null
  recognized: string | null
}

export type AnalysisType = 'WORD' | 'SENTENCE'
/** 자동 분석 판정 상태. NO_CANDIDATES는 텍스트 기준 오류 후보가 없다는 뜻이며 발음이 정확하다는 뜻은 아니다. */
export type AssessmentStatus = 'NO_CANDIDATES' | 'ERROR_CANDIDATES' | 'HOLD'

export type PhonemePosition = {
  phoneme: string; wordIndex: number; word: string; syllableIndex: number; syllable: string
  slot: 'ONSET' | 'NUCLEUS' | 'CODA'; wordPosition: 'INITIAL' | 'MEDIAL' | 'FINAL'
}

/** 자동 오류 후보(미확정). 음성 인식 텍스트와 목표 텍스트를 자모 단위로 비교한 결과다. */
export type PhonemeCandidate = {
  type: 'SUBSTITUTION' | 'OMISSION' | 'ADDITION' | 'SYLLABLE_OMISSION' | 'SYLLABLE_ADDITION'
  slot: 'ONSET' | 'NUCLEUS' | 'CODA' | 'SYLLABLE'
  expected: string | null; produced: string | null
  wordIndex: number | null; word: string | null; syllableIndex: number | null
  targetSyllable: string | null; recognizedSyllable: string | null
  wordPosition: string | null; targetPhoneme: boolean
}

export type SpeechTiming = {
  source: 'VAD' | 'UNAVAILABLE'; audioMs: number; speechMs: number | null
  leadingSilenceMs: number | null; trailingSilenceMs: number | null
  pauseCount: number | null; longestPauseMs: number | null; syllablesPerSecond: number | null
}

/** 측정함 / 이 항목 미지원(평가 모델 없음·낱말 문항) / 이번 녹음에서 측정 못 함(VAD 없음·판정 보류·짧은 발화) */
export type SpeechMetricStatus = 'MEASURED' | 'NOT_SUPPORTED' | 'UNAVAILABLE'

/**
 * 말하기 측정값(점수 아님). 녹음 앞뒤 무음은 빼고 첫 말소리~마지막 말소리 구간만 쓴다.
 * speakingRate.syllablesPerSecond: 음절/발화 구간(멈춤 포함), articulationRate: 음절/말소리 시간(멈춤 제외).
 * pauses: 말소리 구간 사이 무음 pauseThresholdMs 이상을 멈춤으로 센 값(종합 유창성 점수 아님).
 */
export type SpeechMetrics = {
  version: string
  pronunciation: { status: SpeechMetricStatus; reason?: string | null }
  speakingRate: {
    status: SpeechMetricStatus; reason?: string | null; syllables?: number | null; syllableBasis?: string | null
    utteranceMs?: number | null; speechMs?: number | null; syllablesPerSecond?: number | null; articulationRate?: number | null
  }
  pauses: {
    status: SpeechMetricStatus; reason?: string | null; pauseCount?: number | null; totalPauseMs?: number | null
    longestPauseMs?: number | null; pauseRatio?: number | null; speechSegmentCount?: number | null; pauseThresholdMs: number
  }
}

export type Repetition = {
  previousAttempts: number; sameTranscriptCount: number; recurringCandidates: string[]
  recent: { createdAt: string; transcript: string | null; textMatchRate: number | null; assessmentStatus: string | null }[]
}

/** 선생님이 직접 듣고 확정한 오류(자동 후보와 별도 저장) */
export type ConfirmedError = { phoneme: string; errorType: 'SUBSTITUTION' | 'OMISSION' | 'DISTORTION' | 'ADDITION'; produced?: string | null; position?: string | null }

/** GET /api/v1/speech/analyses/{analysisId} 응답. 값이 없는 필드는 응답에서 빠질 수 있다. */
export type SpeechAnalysis = {
  analysisId: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  evaluationMode?: EvaluationMode | null
  targetText?: string | null
  transcript?: string | null
  /** 기존 계약 호환용. textMatchRate와 같은 값이다. */
  matchRate?: number | null
  /** 텍스트 일치율(음성 인식 텍스트와 목표 텍스트의 음절 일치 정도). 발음 점수가 아니다. */
  textMatchRate?: number | null
  analysisType?: AnalysisType | null
  assessmentStatus?: AssessmentStatus | null
  holdReasons?: string[] | null
  assessmentBasis?: string | null
  targetPhonemes?: string[] | null
  targetPositions?: PhonemePosition[] | null
  phonemeCandidates?: PhonemeCandidate[] | null
  speechTiming?: SpeechTiming | null
  /** 말하기 속도·멈춤 측정값과 발음 정확도 지원 여부. 이 기능 이전 분석·실패한 분석이면 없다. */
  speechMetrics?: SpeechMetrics | null
  repetition?: Repetition | null
  analysisVersion?: string | null
  teacherConfirmedErrors?: ConfirmedError[] | null
  comparison?: { words: SpeechWordDiff[] } | null
  recognitionConfidence?: number | null
  pronunciationScore?: number | null
  speechRateScore?: number | null
  fluencyScore?: number | null
  overallScore?: number | null
  pronunciationStatus?: string | null
  reviewStatus?: 'NOT_REQUIRED' | 'PENDING' | 'REVIEWED' | null
  teacherJudgement?: string | null
  teacherNote?: string | null
  feedback?: string | null
  errorCode?: string | null
  modelName?: string | null
}

/**
 * AI 학습 피드백(GET/POST /speech/analyses/{id}/feedback). 점수가 아니라 확인된 분석 근거를 쉽게 풀어 쓴 설명이다.
 * NOT_GENERATED: 아직 만들지 않음 / READY: 설명 있음 / NOT_EVALUABLE: 분석 결과가 부족해 설명할 수 없음(reason)
 * INSUFFICIENT_SOURCES: 이 결과를 설명할 검수된 교육 자료가 없음(reason, AI를 부르지 않음)
 */
export type AiFeedbackSource = {
  marker: string
  cited?: boolean
  chunkId: string
  documentId?: string
  title: string
  location: string
  citation?: string
  url?: string | null
  version?: number
  reviewStatus?: string
  excerpt?: string
  /** 출처 ID(= documentId) */
  sourceId?: string
  /** ARTICULATION_PLACE / ARTICULATION_MANNER / CONSONANT / VOWEL / CODA / PRONUNCIATION_RULE / TEACHER_EXAMPLE */
  category?: string | null
  sourceVersion?: string | null
  license?: string | null
}

export type AiFeedback = {
  analysisId: string
  status: 'NOT_GENERATED' | 'READY' | 'NOT_EVALUABLE' | 'INSUFFICIENT_SOURCES'
  source: 'AI'
  text?: string | null
  reason?: string | null
  modelName?: string | null
  generatedAt?: string | null
  /** AUTO_ANALYSIS(자동 분석) · TEACHER_CONFIRMED(선생님 확인 결과) */
  basedOn?: ('AUTO_ANALYSIS' | 'TEACHER_CONFIRMED')[] | null
  /** 서버에 AI 설정이 있는지(NOT_GENERATED일 때) */
  available?: boolean | null
  /** AI 외부 전송 동의가 필요한지(NOT_GENERATED일 때) */
  consentRequired?: boolean | null
  /** 설명의 근거로 쓴 교육 자료(READY일 때). cited=true가 설명에서 [S1]처럼 인용한 자료 */
  sources?: AiFeedbackSource[] | null
  /** 선생님 화면: 학생이 본 뒤 선생님 판정·자료가 바뀌어 이전 근거로 만든 설명인지 */
  outdated?: boolean | null
}

/** 한 문항의 분석 결과 요약 */
export type ItemResult = {
  itemId: string
  word: string
  mode: EvaluationMode
  matchRate: number | null
  overallScore: number | null
  saved: boolean
}

export type ExerciseResult = { exerciseId: string; label: string; items: ItemResult[] }

export type HistoryItem = {
  id: string | number
  type: 'ai' | 'word' | 'hw'
  title: string
  date: string
  time: string
  score: number | null
  matchRate: number | null
  /** 연습 기록: SELF(자율)·LESSON(수업)·HOMEWORK(숙제)·PRACTICE(유형 구분 전 기존 기록) */
  attemptType?: 'SELF' | 'LESSON' | 'HOMEWORK' | 'PRACTICE' | null
}

/** 연습 콘텐츠 분류(GET /practice-contents) */
export type PronunciationRule = 'BASIC_CONSONANT' | 'BASIC_VOWEL' | 'CODA' | 'LIAISON' | 'NASALIZATION' | 'TENSIFICATION'
  | 'PALATALIZATION' | 'ASPIRATION' | 'CONSONANT_ASSIMILATION' | 'COMPREHENSIVE'
export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type ContentType = 'WORD' | 'SHORT_SENTENCE' | 'LONG_SENTENCE'
export type PracticeContent = Exercise & {
  categoryName?: string
  difficulty?: Difficulty | null
  contentType?: ContentType | null
  pronunciationRule?: PronunciationRule | null
}

export type LoadState = 'loading' | 'ready' | 'error'

/** 학생 화면 내부 이동 상태 */
export type AppScreen =
  | { kind: "tabs"; tab: "home" | "history" | "mypage" }
  | { kind: "practice-type" }
  | { kind: "practice-list" }
  /** focusIndex: 연속 연습에서 돌아왔을 때 목록에서 보여 줄 위치(전체 순서 기준) */
  | { kind: "practice-cat"; category: PracticeCategory; focusIndex?: number }
  /**
   * 영역 연습 흐름. sequential: 영역 전체를 순서대로, random: 시작할 때 정한 무작위 순서(order: 전체 순서의 위치 목록),
   * retry: 이 영역에서 연습했던 세트(최근 연습 순). index는 이 흐름 안의 위치이고, 이전/다음 버튼만 바꾼다.
   */
  | { kind: "practice-run"; category: PracticeCategory; mode: PracticeRunMode; index: number; order?: number[]; results: ExerciseResult[];
      /** selected: 고른 콘텐츠 순서 · search: 검색어 · homeworkId: 숙제 연습이면 숙제 ID(숙제 연습으로 저장) · origin: 뒤로 가기가 돌아갈 화면 */
      selection?: PracticeSelection[]; keyword?: string; homeworkId?: number; origin?: "category" | "homework" }
  /** origin: 전체 연습 찾기(browse)·숙제(homework)에서 시작했으면 뒤로 가기가 그 화면으로 간다. homeworkId가 있으면 숙제 연습으로 저장 */
  | { kind: "practice-activity"; category: PracticeCategory; exIdx: number; results: ExerciseResult[]; origin?: "browse" | "homework"; homeworkId?: number }
  | { kind: "practice-browse" }
  | { kind: "practice-result"; category: PracticeCategory; results: ExerciseResult[] }
  | { kind: "session-list" }
  | { kind: "homework-list" }
  | { kind: "session-exercises"; session: Session }
  | { kind: "activity"; session: Session; exIdx: number; results: ExerciseResult[] }
  | { kind: "session-result"; session: Session; results: ExerciseResult[] }
  /** 학습 상태(마이페이지·홈에서 들어온다. 돌아갈 탭) */
  | { kind: "learning-status"; from: "home" | "mypage" }
  /** 마이페이지 하위 화면(각각 /student/mypage/profile, /student/mypage/consents) */
  | { kind: "my-profile" }
  | { kind: "my-consents" }
  | { kind: "my-guardians" }
  | { kind: "ai-topic" }
  | { kind: "ai-chat"; topic: string };

export type StudentSummary = {
  name: string; grade: string; averageMatchRate: number | null; totalAttempts: number;
  sessionsTotal: number; sessionsDone: number; streak: number;
  /** 나이(학생 프로필)·담당 선생님 이름(실제 담당 연결이 있을 때만, 없으면 개인 학습) */
  age?: number | null; teachers?: string[];
};

