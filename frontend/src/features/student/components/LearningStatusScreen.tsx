"use client";

import LearningReportView from "@/features/learningReport/components/LearningReportView";
import { studentApi } from "@/features/student/api/studentApi";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

/**
 * 학생 학습 현황 = 누적 학습 리포트(기간: 최근 7일·30일·전체). 선생님·보호자 화면과 같은 집계를 쓴다.
 * 저장된 연습 기록만 세고 발음 점수·진단을 만들지 않는다.
 */
export default function LearningStatusScreen({ onBack, backLabel = "마이페이지로 돌아가기" }: { onBack: () => void; backLabel?: string }) {
  return (
    <div className="pb-8">
      <PageHeader title="학습 상태" subtitle="내가 저장한 연습 기록을 기간별로 봐요" onBack={onBack} backLabel={backLabel} />
      <div className="px-5 pt-2">
        <LearningReportView load={(period) => studentApi.learningStatus(period)} initialPeriod="7d" />
      </div>
    </div>
  );
}
