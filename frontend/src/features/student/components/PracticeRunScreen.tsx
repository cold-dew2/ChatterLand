"use client";

import SpeechActivityScreen from "@/features/student/components/SpeechActivityScreen";
import { usePracticeSequence } from "@/features/student/hooks/usePracticeSequence";
import type { ExerciseResult, PracticeCategory, PracticeRunMode } from "@/features/student/types";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

const modeLabel: Record<PracticeRunMode, (category: PracticeCategory) => string> = {
  sequential: (category) => `전체 ${category.label} 연습`,
  random: () => "랜덤 연습",
  retry: () => "다시 연습",
};

/**
 * 영역 연습 흐름(전체 순서·랜덤·다시 연습). 세 가지 이동을 나눈다.
 * - 위쪽 뒤로 가기: 흐름을 끝내고 직전 영역 화면(목록)으로 돌아간다(이전 세트로 가지 않는다).
 * - 아래 이전/다음: 같은 흐름 안에서 바로 앞·뒤 세트로 간다(다른 영역으로 넘어가지 않는다). 첫/마지막에서는 비활성.
 * - 세트를 마치면 다음 세트로, 마지막 세트를 마치면 연습 결과로 간다.
 * 연습 기록은 기존 저장 방식 그대로(자율 연습 SELF) 새로 쌓이며 이전 기록을 바꾸지 않는다.
 */
export default function PracticeRunScreen({ category, mode, index, order, onIndexChange, onExit, onComplete, onOpenConsent }: {
  category: PracticeCategory; mode: PracticeRunMode; index: number; order?: number[];
  onIndexChange: (index: number) => void;
  onExit: () => void;
  /** last: 이 흐름의 마지막 세트였는지 */
  onComplete: (result: ExerciseResult, last: boolean) => void;
  onOpenConsent: () => void;
}) {
  const sequence = usePracticeSequence({ categoryId: category.id, color: category.color, mode, index, order });
  const length = sequence.length ?? 0;
  const title = modeLabel[mode](category);

  if (sequence.state.status !== "ready") {
    return (
      <div className="flex min-h-screen flex-col">
        <PageHeader title={title} subtitle={sequence.length ? `${index + 1} / ${sequence.length}` : undefined} onBack={onExit} backLabel="카테고리로 돌아가기" />
        <div className="px-5 py-6">
          {sequence.state.status === "loading"
            ? <LoadingState label="연습을 불러오고 있어요…" />
            : <ErrorState message={sequence.state.message} onRetry={sequence.retry} />}
        </div>
      </div>
    );
  }

  const { exercise } = sequence.state;
  const last = index >= length - 1;
  const position = `${index + 1} / ${length}`;
  const subtitle = mode === "sequential" ? `${category.label} ${position}` : mode === "random" ? `랜덤 ${position}` : `다시 연습 ${position}`;
  return (
    <SpeechActivityScreen key={`${mode}-${index}-${exercise.id}`} exercises={[exercise]} exIdx={0}
      subtitle={subtitle} backLabel="카테고리로 돌아가기" completeLabel={last ? "연습 마치기" : "다음 연습"}
      navigation={{ onPrev: index > 0 ? () => onIndexChange(index - 1) : undefined, onNext: last ? undefined : () => onIndexChange(index + 1) }}
      onBack={onExit} onOpenConsent={onOpenConsent} onComplete={(result) => onComplete(result, last)} />
  );
}
