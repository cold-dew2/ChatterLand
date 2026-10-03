import { averageMatchRate } from "@/features/student/components/SpeechActivityScreen";
import type { ExerciseResult } from "@/features/student/types";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import ProgressBar from "@/shared/components/progressBar/ProgressBar";

/**
 * 세션/연습 결과. 저장된 분석 결과에서 계산 가능한 값(텍스트 일치율, 문항 수)만 보여준다.
 * 측정하지 않은 점수나 미리 작성된 칭찬·평가 문구를 만들어 내지 않는다.
 */
export default function PracticeResultScreen({ title, results, onClose }: { title: string; results: ExerciseResult[]; onClose: () => void }) {
  const allItems = results.flatMap((result) => result.items);
  const average = averageMatchRate(allItems);
  const pendingReview = allItems.filter((item) => item.mode === "PRONUNCIATION_REVIEW").length;

  return (
    <div className="flex min-h-screen flex-col bg-white">
      <PageHeader title={title} subtitle="연습 결과" onBack={onClose} backLabel="홈으로 돌아가기" />
      <div className="flex-1 space-y-6 overflow-y-auto px-5 py-6">
        <div className="text-center">
          <p className="mb-3 text-sm text-gray-400">평균 텍스트 일치율</p>
          {average !== null
            ? <p className="mb-3 text-6xl font-black leading-none text-gray-900">{average}<span className="ml-1 text-2xl font-bold text-gray-400">%</span></p>
            : <p className="mb-3 text-2xl font-bold text-gray-400">계산된 값 없음</p>}
          <p className="text-xs text-gray-500">녹음한 문항 {allItems.length}개 · 활동 {results.length}개</p>
          {pendingReview > 0 && <div className="mt-2 flex justify-center"><Badge tone="info">선생님 확인 대기 {pendingReview}개</Badge></div>}
        </div>

        <section aria-labelledby="activity-result-title">
          <h3 id="activity-result-title" className="mb-4 text-sm font-bold text-gray-700">활동별 결과</h3>
          <ul className="space-y-3.5">
            {results.map((result) => {
              const rate = averageMatchRate(result.items);
              return (
                <li key={result.exerciseId} className="flex items-center gap-3">
                  <span className="w-24 shrink-0 truncate text-sm text-gray-500">{result.label}</span>
                  {rate !== null
                    ? <><ProgressBar value={rate} label={`${result.label} 텍스트 일치율 ${rate}%`} size="md" color="var(--brand-primary)" /><span className="w-10 shrink-0 text-right text-sm font-bold text-gray-700">{rate}%</span></>
                    : <Badge tone="neutral">미평가</Badge>}
                </li>
              );
            })}
          </ul>
        </section>

        <Card tone="muted" className="space-y-1 text-xs leading-relaxed text-gray-500">
          <p>텍스트 일치율은 음성 인식 결과와 목표 문장의 글자 일치 정도예요.</p>
          <p>발음 정확도, 말하기 속도, 유창성은 아직 평가하지 않아요. 선생님의 평가를 대신하지 않아요.</p>
        </Card>
      </div>
      <div className="shrink-0 border-t border-gray-100 px-5 py-4">
        <Button variant="neutral" fullWidth onClick={onClose}>홈으로 돌아가기</Button>
      </div>
    </div>
  );
}
