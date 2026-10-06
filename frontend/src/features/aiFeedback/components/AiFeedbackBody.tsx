import type { AiFeedback } from "@/features/student/types";

const basisLabel = { AUTO_ANALYSIS: "자동 분석 결과(확정 아님)", TEACHER_CONFIRMED: "선생님 확인 결과" } as const;

/**
 * 저장된 AI 설명 본문(학생·선생님 화면 공통). 설명, 인용한 교육 자료(제목·원문 위치, 교사 승인 예시 표시), 근거 종류,
 * '점수·진단이 아님' 안내를 함께 보여 준다. 화면마다 다른 상태 처리(버튼·불러오기)는 각 화면이 맡는다.
 */
export default function AiFeedbackBody({ feedback }: { feedback: AiFeedback }) {
  const cited = (feedback.sources ?? []).filter((source) => source.cited !== false);
  return (
    <div className="space-y-3">
      <p className="text-sm leading-[1.65] text-[var(--ink-900)]">{feedback.text}</p>
      {cited.length > 0 && (
        <div className="rounded-[var(--radius-lg)] bg-[var(--surface-sunken)] px-3.5 py-3">
          <p className="mb-1.5 text-xs font-bold text-[var(--ink-600)]">참고한 교육 자료</p>
          <ul className="space-y-1">
            {cited.map((source) => (
              <li key={source.chunkId} className="text-xs leading-relaxed text-[var(--ink-700)]">
                <span className="font-bold">[{source.marker}]</span> {source.title} {source.location}
                {source.category === "TEACHER_EXAMPLE" && <> · 선생님 승인 설명 예시</>}
                {source.url && <> · <a href={source.url} target="_blank" rel="noreferrer" className="font-semibold text-[var(--meadow-800)] underline underline-offset-2">원문</a></>}
              </li>
            ))}
          </ul>
        </div>
      )}
      <p className="text-[11px] leading-relaxed text-[var(--ink-500)]">
        {(feedback.basedOn ?? []).map((basis) => basisLabel[basis]).join(" · ") || "분석 결과"}와 교육 자료를 바탕으로 AI가 쓴 설명이에요.
        발음 점수나 진단이 아니고, 발음 정확도는 평가하지 않았어요.
      </p>
    </div>
  );
}
