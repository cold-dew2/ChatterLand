export type ExerciseItem = { id: string; word: string; emoji: string }

export type Exercise = {
  id: string
  label: string
  color: string
  instruction: string
  items: ExerciseItem[]
  inputType: 'mic' | 'read' | 'speak'
}

export type Session = { id: string; title: string; date: string; done: boolean; exercises: Exercise[] }

export type Homework = { id: number; title: string; type: string; description: string; dueDate: string; done: boolean; targetMinutes: number }

export type PracticeCategory = { id: string; label: string; color: string; desc: string; exercises: Exercise[] }

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
 * NOT_GENERATED: 아직 만들지 않음 / READY: 설명 있음 / NOT_EVALUABLE: 근거가 부족해 설명할 수 없음(reason)
 */
export type AiFeedback = {
  analysisId: string
  status: 'NOT_GENERATED' | 'READY' | 'NOT_EVALUABLE'
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
}

export type LoadState = 'loading' | 'ready' | 'error'

/** 학생 화면 내부 이동 상태 */
export type AppScreen =
  | { kind: "tabs"; tab: "home" | "history" | "mypage" }
  | { kind: "practice-type" }
  | { kind: "practice-list" }
  | { kind: "practice-cat"; category: PracticeCategory }
  | { kind: "practice-activity"; category: PracticeCategory; exIdx: number; results: ExerciseResult[] }
  | { kind: "practice-result"; category: PracticeCategory; results: ExerciseResult[] }
  | { kind: "session-list" }
  | { kind: "homework-list" }
  | { kind: "session-exercises"; session: Session }
  | { kind: "activity"; session: Session; exIdx: number; results: ExerciseResult[] }
  | { kind: "session-result"; session: Session; results: ExerciseResult[] }
  | { kind: "ai-topic" }
  | { kind: "ai-chat"; topic: string };

export type StudentSummary = {
  name: string; grade: string; averageMatchRate: number | null; totalAttempts: number;
  sessionsTotal: number; sessionsDone: number; streak: number;
};

