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

/** GET /api/v1/speech/analyses/{analysisId} 응답. 값이 없는 필드는 응답에서 빠질 수 있다. */
export type SpeechAnalysis = {
  analysisId: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  evaluationMode?: EvaluationMode | null
  targetText?: string | null
  transcript?: string | null
  matchRate?: number | null
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

