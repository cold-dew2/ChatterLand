import { BarChart2, Bell, ClipboardList, Users } from "lucide-react";
import type { Homework, Student, TeacherView } from "@/features/teacher/types";
import Badge from "@/shared/components/badge/Badge";
import Card from "@/shared/components/card/Card";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import MenuCard from "@/shared/components/menuCard/MenuCard";
import Stat from "@/shared/components/stat/Stat";

export default function TeacherHomeView({ students, homeworks, centerName, loadState, onRetry, onNavigate }: {
  students: Student[]; homeworks: Homework[]; centerName: string; loadState: "loading" | "ready" | "error"; onRetry: () => void; onNavigate: (v: TeacherView) => void;
}) {
  const activeCount = students.filter((s) => s.status === "진행중").length;
  const pendingHomeworks = homeworks.filter((homework) => !homework.done).length;
  return (
    <div className="space-y-4 px-4 pt-6 sm:px-6">
      <div>
        <p className="text-xs font-medium text-[var(--ink-500)]">담당 센터</p>
        <p className="mt-1 font-display text-2xl leading-tight text-[var(--ink-900)]">{centerName || "센터 정보"}</p>
      </div>

      <div>
        {loadState === "loading" && <LoadingState label="담당 현황을 불러오고 있어요…" />}
        {loadState === "error" && <ErrorState message="담당 학생과 숙제 정보를 불러오지 못했어요." onRetry={onRetry} />}
        {loadState === "ready" && (
          <Card padding="lg">
            <p className="mb-3 text-[13px] font-semibold text-[var(--ink-600)]">오늘 현황</p>
            <div className="grid grid-cols-2">
              <Stat label="진행중 · 전체 학생" value={activeCount} unit={` / ${students.length}명`} size="lg" />
              <Stat label="미완료 · 전체 숙제" value={pendingHomeworks} unit={` / ${homeworks.length}건`} size="lg" tone={pendingHomeworks > 0 ? "danger" : "neutral"}
                className="border-l border-[var(--ink-100)] pl-4" />
            </div>
          </Card>
        )}
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        <MenuCard icon={Users} color="var(--brand-blue)" title="제자 관리" description="제자 정보와 음성 연습 기록을 관리해요" onClick={() => onNavigate("students")} />
        <MenuCard icon={ClipboardList} color="var(--brand-coral)" title="숙제 관리" description="숙제 등록 및 수정 과정을 확인해요" onClick={() => onNavigate("homework")} />
        <MenuCard icon={BarChart2} color="var(--meadow-600)" title="분석 및 레포트" description="학습 결과를 분석하고 리포트를 확인해요" onClick={() => onNavigate("analytics")} />
      </div>

      <Card tone="muted" className="flex items-center justify-between" aria-disabled="true">
        <p className="text-sm font-medium text-[var(--ink-600)]">공지사항</p>
        <span className="flex items-center gap-2"><Badge tone="neutral" className="bg-white">준비 중</Badge><Bell size={18} className="text-[var(--ink-400)]" aria-hidden="true" /></span>
      </Card>
    </div>
  );
}
