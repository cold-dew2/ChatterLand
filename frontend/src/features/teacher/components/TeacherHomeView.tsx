import { BarChart2, Bell, ClipboardList, Users } from "lucide-react";
import type { Homework, Student, TeacherView } from "@/features/teacher/types";
import Badge from "@/shared/components/badge/Badge";
import Card from "@/shared/components/card/Card";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import MenuCard from "@/shared/components/menuCard/MenuCard";

export default function TeacherHomeView({ students, homeworks, centerName, loadState, onRetry, onNavigate }: {
  students: Student[]; homeworks: Homework[]; centerName: string; loadState: "loading" | "ready" | "error"; onRetry: () => void; onNavigate: (v: TeacherView) => void;
}) {
  const activeCount = students.filter((s) => s.status === "진행중").length;
  return (
    <div>
      <div className="px-5 pt-10 pb-6">
        <p className="text-xs text-gray-400 mb-1.5">담당 센터</p>
        <p className="text-base font-bold text-gray-900">{centerName || "센터 정보"}</p>
      </div>

      <div className="mx-5 mb-5">
        {loadState === "loading" && <LoadingState label="담당 현황을 불러오고 있어요…" />}
        {loadState === "error" && <ErrorState message="담당 학생과 숙제 정보를 불러오지 못했어요." onRetry={onRetry} />}
        {loadState === "ready" && (
          <Card tone="muted">
            <p className="text-xs text-gray-400 mb-1">오늘 현황</p>
            <p className="text-sm font-semibold text-gray-700">진행중 {activeCount}명 · 전체 {students.length}명</p>
            <p className="mt-1 text-xs text-gray-500">미완료 숙제 {homeworks.filter((homework) => !homework.done).length}건 · 전체 {homeworks.length}건</p>
          </Card>
        )}
      </div>

      <div className="px-5 space-y-3">
        <MenuCard icon={Users} color="var(--brand-blue)" title="제자 관리" description="제자 정보와 음성 연습 기록을 관리해요" onClick={() => onNavigate("students")} />
        <MenuCard icon={ClipboardList} color="var(--brand-coral)" title="숙제 관리" description="숙제 등록 및 수정 과정을 확인해요" onClick={() => onNavigate("homework")} />
        <MenuCard icon={BarChart2} color="var(--brand-purple)" title="분석 및 레포트" description="학습 결과를 분석하고 리포트를 확인해요" onClick={() => onNavigate("analytics")} />
      </div>

      <Card tone="muted" className="mx-5 mt-6 flex items-center justify-between" aria-disabled="true">
        <p className="text-sm font-medium text-gray-700">공지사항</p>
        <span className="flex items-center gap-2"><Badge tone="neutral">준비 중</Badge><Bell size={18} className="text-gray-400" aria-hidden="true" /></span>
      </Card>
    </div>
  );
}
