"use client";

import SpeechActivityScreen from "@/features/student/components/SpeechActivityScreen";
import { usePracticeSequence } from "@/features/student/hooks/usePracticeSequence";
import type { ExerciseResult, PracticeCategory, PracticeRunMode, PracticeSelection } from "@/features/student/types";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

/** 흐름 이름: 전체·랜덤·다시·선택·검색·숙제 연습의 진행 숫자가 서로 다른 뜻이라 함께 보여 준다 */
export function runLabel(mode: PracticeRunMode, category: PracticeCategory, keyword?: string, homework = false) {
  if (homework) return "숙제 연습";
  switch (mode) {
    case "sequential": return `전체 ${category.label} 연습`;
    case "random": return "랜덤 연습";
    case "retry": return "다시 연습";
    case "selected": return `선택한 ${category.label} 연습`;
    case "search": return `'${keyword ?? ""}' 검색 결과 연습`;
  }
}

/**
 * 영역 연습 흐름(전체 순서·랜덤·다시 연습·선택·검색 결과·숙제). 세 가지 이동을 나눈다.
 * - 위쪽 뒤로 가기: 흐름을 끝내고 직전 목록 화면으로 돌아간다(이전 세트로 가지 않는다).
 * - 아래 이전/다음: 같은 흐름 안에서 바로 앞·뒤 세트로 간다(녹음하지 않고 넘기면 건너뛰기). 첫/마지막에서는 비활성.
 * - 세트를 마치면 다음 세트로, 마지막 세트를 마치면 연습 결과로 간다.
 * 연습 기록은 세트를 연습할 때마다 기존 저장 방식 그대로(자율 연습 SELF, 숙제면 숙제 연습) 하나씩 쌓이며 이전 기록을 바꾸지 않는다.
 */
export default function PracticeRunScreen({ category, mode, index, order, selection, keyword, homeworkId, onIndexChange, onExit, onComplete, onOpenConsent }: {
  category: PracticeCategory; mode: PracticeRunMode; index: number; order?: number[];
  selection?: PracticeSelection[]; keyword?: string; homeworkId?: number;
  onIndexChange: (index: number) => void;
  onExit: () => void;
  /** last: 이 흐름의 마지막 세트였는지 */
  onComplete: (result: ExerciseResult, last: boolean) => void;
  onOpenConsent: () => void;
}) {
  const sequence = usePracticeSequence({ categoryId: category.id, color: category.color, mode, index, order, selection, keyword });
  const length = sequence.length ?? 0;
  const label = runLabel(mode, category, keyword, homeworkId !== undefined);
  const backLabel = homeworkId !== undefined ? "숙제 목록으로 돌아가기" : "카테고리로 돌아가기";

  if (sequence.state.status !== "ready") {
    return (
      <div className="flex min-h-screen flex-col">
        <PageHeader title={label} subtitle={sequence.length ? `${index + 1} / ${sequence.length}` : undefined} onBack={onExit} backLabel={backLabel} />
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
  return (
    <SpeechActivityScreen key={`${mode}-${index}-${exercise.id}`} exercises={[exercise]} exIdx={0}
      subtitle={`${label} ${index + 1} / ${length}`} backLabel={backLabel} completeLabel={last ? "연습 마치기" : "다음 연습"}
      homeworkId={homeworkId}
      navigation={{ onPrev: index > 0 ? () => onIndexChange(index - 1) : undefined, onNext: last ? undefined : () => onIndexChange(index + 1) }}
      onBack={onExit} onOpenConsent={onOpenConsent} onComplete={(result) => onComplete(result, last)} />
  );
}
