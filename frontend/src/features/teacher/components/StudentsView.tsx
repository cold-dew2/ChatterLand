"use client";

import { useEffect, useState } from "react";
import { ChevronRight, Plus, Search, Users } from "lucide-react";
import { teacherApi } from "@/features/teacher/api/teacherApi";
import StudentDetailView from "@/features/teacher/components/StudentDetailView";
import type { Student } from "@/features/teacher/types";
import { avatarColors, STATUS_FILTERS } from "@/features/teacher/utils/display";
import { mapStudent } from "@/features/teacher/utils/mappers";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import ConfirmDialog from "@/shared/components/feedback/ConfirmDialog";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import Tabs from "@/shared/components/tabs/Tabs";

export default function StudentsView({ revision, onBack, onAddStudent, onAssignHw, onEditStudent, onDeleteStudent, initialStudentId = null }: {
  revision: number; onBack: () => void; onAddStudent: () => void; onAssignHw: (studentId: number) => void; onEditStudent: (student: Student) => void;
  onDeleteStudent: (studentId: number) => Promise<boolean>; initialStudentId?: number | null;
}) {
  const [selectedId, setSelectedId] = useState<number | null>(initialStudentId);
  const [detailError, setDetailError] = useState("");
  const [selected, setSelected] = useState<Student | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [rows, setRows] = useState<Student[]>([]);
  const [listState, setListState] = useState<"loading" | "ready" | "error">("loading");
  const [listError, setListError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [checked, setChecked] = useState<number[]>([]);
  const [confirmBulk, setConfirmBulk] = useState(false);
  const [bulkDeleting, setBulkDeleting] = useState(false);

  useEffect(() => {
    let active = true;
    teacherApi.students(search, statusFilter || undefined, page, 10).then((response) => {
      const payload = response as { content?: Record<string, unknown>[]; totalPages?: number };
      if (!active) return;
      setRows((payload.content ?? []).map(mapStudent));
      setTotalPages(Math.max(1, payload.totalPages ?? 1));
      setListState("ready");
    }).catch((cause: unknown) => { if (active) { setListError(errorMessage(cause, "학생 목록을 불러오지 못했어요.")); setListState("error"); } });
    return () => { active = false; };
  }, [search, statusFilter, page, revision, retryKey]);

  useEffect(() => {
    if (selectedId === null) return;
    let active = true;
    teacherApi.student(selectedId).then((response) => {
      const item = response as Record<string, unknown>;
      if (active) { setDetailError(""); setSelected(mapStudent({ ...item, studentId: item.studentId ?? selectedId })); }
    }).catch((cause: unknown) => {
      // 담당이 아니거나 없는 학생이면 목록으로 돌아가면서 이유를 알려준다(오류를 조용히 무시하지 않는다).
      if (active) { setDetailError(errorMessage(cause, "학생 정보를 불러오지 못했어요.")); setSelectedId(null); }
    });
    return () => { active = false; };
  }, [selectedId, revision]);

  const closeDetail = () => { setSelectedId(null); setSelected(null); };
  if (selectedId !== null) {
    if (!selected || selected.id !== selectedId) return <div><PageHeader tone="bar" title="제자 상세 정보" onBack={closeDetail} /><LoadingState label="학생 정보를 불러오고 있어요…" /></div>;
    return <StudentDetailView key={selected.id} student={selected} onBack={closeDetail} onEdit={onEditStudent} onDelete={onDeleteStudent} onAssignHw={onAssignHw} />;
  }

  const toggleCheck = (id: number) => setChecked((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);
  const removeChecked = async () => {
    setBulkDeleting(true);
    const remaining: number[] = [];
    for (const id of checked) { if (!(await onDeleteStudent(id))) remaining.push(id); }
    setBulkDeleting(false);
    setChecked(remaining);
    setConfirmBulk(false);
  };

  return (
    <div className="flex flex-col min-h-screen">
      {confirmBulk && <ConfirmDialog title={`${checked.length}명을 담당 목록에서 삭제할까요?`}
        description="선택한 학생과의 담당 연결을 해제합니다. 학생 계정과 학습 기록은 삭제되지 않아요."
        pending={bulkDeleting} onCancel={() => setConfirmBulk(false)} onConfirm={removeChecked} />}
      <PageHeader tone="bar" title="제자 목록" onBack={onBack} />
      {detailError && <Notice tone="error" className="mx-4 mt-3 sm:mx-6">{detailError}</Notice>}

      <div className="flex items-end gap-2 px-4 pt-4 pb-2.5 sm:px-6">
        <Input label="이름 또는 연락처 검색" hideLabel size="sm" fieldClassName="flex-1" className="min-h-[46px]" value={search} placeholder="이름 검색"
          onChange={(e) => { setSearch(e.target.value); setPage(0); setListState("loading"); }}
          endAdornment={<Search size={16} className="text-[var(--ink-500)]" aria-hidden="true" />} />
        <Button variant="secondary" size="sm" onClick={onAddStudent} className="min-h-[46px] whitespace-nowrap"><Plus size={14} aria-hidden="true" /> 제자 추가</Button>
      </div>

      <Tabs ariaLabel="상태 필터" variant="chip" value={statusFilter} className="px-4 pb-3 sm:px-6"
        items={STATUS_FILTERS.map((option) => ({ value: option.value, label: option.value ? option.label : "전체" }))}
        onChange={(value) => { setStatusFilter(value); setPage(0); setListState("loading"); }} />

      <ul className="flex-1 space-y-2 px-4 pb-4 sm:px-6">
        {listState === "loading" && <li><LoadingState label="학생 목록을 불러오고 있어요…" /></li>}
        {listState === "error" && <li><ErrorState message={listError} onRetry={() => { setListState("loading"); setRetryKey((value) => value + 1); }} /></li>}
        {listState === "ready" && rows.length === 0 && <li><EmptyState title={search || statusFilter ? "조건에 맞는 학생이 없어요" : "등록된 제자가 없어요"}
          icon={<span className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--sky-100)] text-[var(--sky-800)]"><Users size={22} aria-hidden="true" /></span>}
          action={!search && !statusFilter ? <Button size="sm" onClick={onAddStudent}>제자 추가</Button> : undefined} /></li>}
        {listState === "ready" && rows.map((s, i) => (
          <Card as="li" key={s.id} padding="none" className={`flex items-center gap-3 rounded-[var(--radius-2xl)] px-3.5 py-3 transition-colors hover:border-[var(--meadow-200)] ${checked.includes(s.id) ? "border-[var(--meadow-300)] bg-[var(--meadow-50)]" : ""}`}>
            <input type="checkbox" checked={checked.includes(s.id)} onChange={() => toggleCheck(s.id)} aria-label={`${s.name} 선택`}
              className="h-5 w-5 shrink-0 cursor-pointer rounded accent-[var(--brand-primary)]" />
            <button type="button" onClick={() => setSelectedId(s.id)} className="flex min-h-11 min-w-0 flex-1 items-center gap-3 rounded-[var(--radius-md)] text-left">
              <span className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-full" style={{ backgroundColor: avatarColors[i % avatarColors.length] }} aria-hidden="true">
                <span className="text-[15px] font-extrabold text-[var(--ink-900)]">{s.name[0]}</span>
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-x-1.5">
                  <span className="text-[15px] font-bold text-[var(--ink-900)]">{s.name}</span>
                  <span className="text-xs text-[var(--ink-500)]">({s.age}세)</span>
                  {s.learnerType === "THERAPY" && <Badge tone="info" className="rounded-md">언어재활</Badge>}
                </span>
                <span className="mt-0.5 block truncate text-xs text-[var(--ink-500)]">최근 활동: {s.lastSession}</span>
              </span>
              <ChevronRight size={18} className="shrink-0 text-[var(--ink-300)]" aria-hidden="true" />
            </button>
          </Card>
        ))}
      </ul>

      <div className="px-4 pb-6 pt-2 sm:px-6">
        {totalPages > 1 && <nav aria-label="학생 목록 페이지" className="mb-3 flex items-center justify-center gap-3 text-[13px] font-medium text-[var(--ink-600)]">
          <Button size="sm" variant="line" disabled={page === 0} onClick={() => { setPage((current) => Math.max(0, current - 1)); setListState("loading"); }}>이전</Button>
          <span>{page + 1} / {totalPages}</span>
          <Button size="sm" variant="line" disabled={page + 1 >= totalPages} onClick={() => { setPage((current) => Math.min(totalPages - 1, current + 1)); setListState("loading"); }}>다음</Button>
        </nav>}
        <Button variant="dangerLine" size="lg" fullWidth disabled={checked.length === 0} onClick={() => setConfirmBulk(true)}>
          담당 해제{checked.length > 0 ? ` (${checked.length}명)` : ""}
        </Button>
      </div>
    </div>
  );
}
