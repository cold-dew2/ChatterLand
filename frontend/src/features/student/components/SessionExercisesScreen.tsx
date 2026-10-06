import { Mic } from "lucide-react";
import type { Session } from "@/features/student/types";
import EmptyState from "@/shared/components/feedback/EmptyState";
import MenuCard from "@/shared/components/menuCard/MenuCard";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

export default function SessionExercisesScreen({ session, onSelect, onBack }: {
  session: Session; onSelect: (idx: number) => void; onBack: () => void;
}) {
  return (
    <div>
      <PageHeader title={session.title} subtitle={session.date} onBack={onBack} />
      <div className="space-y-3 px-5 pt-2 pb-8">
        {session.exercises.length === 0 && <EmptyState title="이 세션에 등록된 활동이 없어요" />}
        {session.exercises.map((ex, i) => (
          <MenuCard key={ex.id} icon={Mic} color={ex.color} title={ex.label}
            description={`${ex.items.length}개 항목`} onClick={() => onSelect(i)} />
        ))}
      </div>
    </div>
  );
}
