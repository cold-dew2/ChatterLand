"use client";

import { useState } from "react";
import { Check, ClipboardList, Pencil, Plus, Trash2 } from "lucide-react";
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
      <PageHeader tone="bar" title="숙제 관리" onBack={onBack} action={<Button size="sm" onClick={onAssignHw}><Plus size={14} aria-hidden="true" /> 등록</Button>} />

      <Tabs ariaLabel="학생별 숙제 필터" variant="chip" value={filter} onChange={setFilter} className="px-4 pt-4 pb-2 sm:px-6"
        items={[{ value: "all" as const, label: "전체" }, ...students.map((s) => ({ value: s.id, label: s.name }))]} />

      <div className="space-y-2.5 px-4 py-3 sm:px-6">
        {loadState === "loading" && <LoadingState label="숙제를 불러오고 있어요…" />}
        {loadState === "error" && <ErrorState message="숙제 목록을 불러오지 못했어요." onRetry={onRetry} />}
        {loadState === "ready" && filtered.length === 0 && (
          <EmptyState title={filter === "all" ? "숙제가 없어요" : "이 학생에게 배정한 숙제가 없어요"} variant="plain"
            icon={<span className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--coral-100)] text-[var(--coral-700)]"><ClipboardList size={22} aria-hidden="true" /></span>}
            action={<Button onClick={onAssignHw}>{filter === "all" ? "첫 숙제 등록" : "숙제 등록"}</Button>} />
        )}
        {loadState === "ready" && filtered.map((hw) => {
          const student = students.find((s) => s.id === hw.studentId);
          const overdue = !hw.done && Boolean(hw.dueDate) && hw.dueDate < today;
          return (
            <Card as="article" key={hw.id} className={hw.done ? "bg-[var(--ink-50)]" : ""}>
              <div className="flex items-start gap-3">
                <button type="button" onClick={async () => { setTogglingId(hw.id); await onToggle(hw.id); setTogglingId(null); }} disabled={togglingId !== null}
                  role="checkbox" aria-checked={hw.done} aria-label={`${hw.title} 완료 상태 변경`}
                  className={`-m-2.5 flex h-11 w-11 shrink-0 items-center justify-center rounded-full transition-colors disabled:opacity-50`}>
                  <span className={`flex h-6 w-6 items-center justify-center rounded-full border-2 ${hw.done ? "border-[var(--meadow-600)] bg-[var(--meadow-600)]" : "border-[var(--ink-300)] bg-white hover:border-[var(--meadow-600)]"}`}>
                    {hw.done && <Check size={14} strokeWidth={3} className="text-white" aria-hidden="true" />}
                  </span>
                </button>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <p className={`text-[15px] font-bold ${hw.done ? "text-[var(--ink-500)] line-through" : "text-[var(--ink-900)]"}`}>{hw.title}</p>
                    <Badge tone="neutral">{hw.type}</Badge>
                    {hw.done && <Badge tone="success">완료</Badge>}
                  </div>
                  {student && <p className="mb-1 text-xs font-semibold text-[var(--ink-600)]">{student.name}</p>}
                  {hw.description && <p className="mb-1.5 text-xs leading-relaxed text-[var(--ink-600)]">{hw.description}</p>}
                  <p className={`text-xs font-medium ${overdue ? "text-[var(--coral-600)]" : "text-[var(--ink-500)]"}`}>마감: {hw.dueDate}{overdue ? " (기한 초과)" : ""}</p>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Button variant="ghost" size="icon" aria-label={`${hw.title} 숙제 수정`} onClick={() => onEdit(hw)} className="hover:enabled:text-[var(--meadow-800)]"><Pencil size={16} /></Button>
                  <Button variant="ghost" size="icon" aria-label={`${hw.title} 숙제 삭제`} onClick={() => setDeleteTarget(hw)} className="hover:enabled:bg-[var(--coral-50)] hover:enabled:text-[var(--coral-600)]"><Trash2 size={16} /></Button>
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
