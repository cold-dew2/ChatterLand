"use client";

import { useState } from "react";
import { CheckCircle, ClipboardList, Pencil, Plus, Trash2 } from "lucide-react";
import type { Homework, Student } from "@/features/teacher/types";
import { localDateInput } from "@/features/teacher/utils/mappers";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import ConfirmDialog from "@/shared/components/feedback/ConfirmDialog";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import Tabs from "@/shared/components/tabs/Tabs";

export default function HomeworkView({ students, homeworks, loadState, onRetry, onBack, onAssignHw, onEdit, onToggle, onDelete }: {
  students: Student[]; homeworks: Homework[]; loadState: "loading" | "ready" | "error"; onRetry: () => void; onBack: () => void;
  onAssignHw: () => void; onEdit: (homework: Homework) => void; onToggle: (id: number) => Promise<void>; onDelete: (id: number) => Promise<boolean>;
}) {
  const [filter, setFilter] = useState<number | "all">("all");
  const [deleteTarget, setDeleteTarget] = useState<Homework | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const filtered = filter === "all" ? homeworks : homeworks.filter((h) => h.studentId === filter);
  const today = localDateInput(new Date());

  return (
    <div>
      {deleteTarget && <ConfirmDialog
        title="숙제를 삭제할까요?"
        description={`“${deleteTarget.title}” 숙제가 학생과 치료사 화면에서 삭제됩니다.`}
        pending={deleting}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={async () => {
          setDeleting(true);
          const removed = await onDelete(deleteTarget.id);
          setDeleting(false);
          if (removed) setDeleteTarget(null);
        }}
      />}
      <PageHeader title="숙제 관리" onBack={onBack} action={<Button size="sm" onClick={onAssignHw}><Plus size={14} aria-hidden="true" /> 등록</Button>} />

      <Tabs ariaLabel="학생별 숙제 필터" variant="chip" value={filter} onChange={setFilter} className="px-5 pt-4 pb-2"
        items={[{ value: "all" as const, label: "전체" }, ...students.map((s) => ({ value: s.id, label: s.name }))]} />

      <div className="px-5 py-3 space-y-3">
        {loadState === "loading" && <LoadingState label="숙제를 불러오고 있어요…" />}
        {loadState === "error" && <ErrorState message="숙제 목록을 불러오지 못했어요." onRetry={onRetry} />}
        {loadState === "ready" && filtered.length === 0 && (
          <EmptyState title="숙제가 없어요" variant="plain"
            icon={<ClipboardList size={32} className="text-gray-200 mx-auto mb-2" aria-hidden="true" />}
            action={<Button onClick={onAssignHw}>첫 숙제 등록</Button>} />
        )}
        {loadState === "ready" && filtered.map((hw) => {
          const student = students.find((s) => s.id === hw.studentId);
          const overdue = !hw.done && Boolean(hw.dueDate) && hw.dueDate < today;
          return (
            <Card as="article" key={hw.id} className={hw.done ? "opacity-60" : ""}>
              <div className="flex items-start gap-3">
                <button type="button" onClick={async () => { setTogglingId(hw.id); await onToggle(hw.id); setTogglingId(null); }} disabled={togglingId !== null}
                  role="checkbox" aria-checked={hw.done} aria-label={`${hw.title} 완료 상태 변경`}
                  className={`mt-0.5 w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 transition-colors disabled:opacity-50 ${hw.done ? "bg-green-500 border-green-500" : "border-gray-300 hover:border-gray-500"}`}>
                  {hw.done && <CheckCircle size={11} className="text-white" aria-hidden="true" />}
                </button>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <p className={`text-sm font-semibold ${hw.done ? "line-through text-gray-400" : "text-gray-900"}`}>{hw.title}</p>
                    <Badge tone="neutral">{hw.type}</Badge>
                    {hw.done && <Badge tone="success">완료</Badge>}
                  </div>
                  {student && <p className="text-xs text-gray-400 mb-1">{student.name}</p>}
                  {hw.description && <p className="text-xs text-gray-500 leading-relaxed mb-1.5">{hw.description}</p>}
                  <p className={`text-xs font-medium ${overdue ? "text-red-400" : "text-gray-400"}`}>마감: {hw.dueDate}{overdue ? " (기한 초과)" : ""}</p>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Button variant="ghost" size="icon" aria-label={`${hw.title} 숙제 수정`} onClick={() => onEdit(hw)} className="p-1.5 text-gray-300 hover:text-[var(--brand-primary)]"><Pencil size={14} /></Button>
                  <Button variant="ghost" size="icon" aria-label={`${hw.title} 숙제 삭제`} onClick={() => setDeleteTarget(hw)} className="p-1.5 text-gray-300 hover:text-red-400"><Trash2 size={14} /></Button>
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
