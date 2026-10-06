"use client";

import { useState } from "react";
import type { PracticeContent } from "@/features/student/types";
import PracticeContentPicker from "@/features/teacher/components/PracticeContentPicker";
import type { Homework, Student } from "@/features/teacher/types";
import { localDateInput } from "@/features/teacher/utils/mappers";
import Button from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import Modal from "@/shared/components/modal/Modal";
import Select from "@/shared/components/select/Select";

const HW_RADIO_TYPES = ["AI 대화하기", "단어 말하기", "말하기 연습", "조음 말하기"];

type HomeworkErrors = { studentId?: string; title?: string; minutes?: string; dueDate?: string };

function defaultDueDate() {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  return localDateInput(date);
}

/** 숙제 등록·수정 모달. 저장 실패 시 서버 오류(error)를 모달 안에 보여주고 닫지 않는다. */
export default function HomeworkModal({ students, preStudentId, initialHomework, error, onClose, onAssign, onUpdate }: {
  students: Student[]; preStudentId: number; initialHomework?: Homework | null; error?: string; onClose: () => void;
  onAssign: (hw: Omit<Homework, "id" | "done">) => Promise<boolean>; onUpdate: (id: number, body: Record<string, unknown>) => Promise<boolean>;
}) {
  const initialStudentId = initialHomework?.studentId ?? preStudentId;
  // 목록에 없는 학생(예: 담당 해제됨)이 미리 선택되지 않도록 빈 값(선택 안내)으로 시작한다.
  const [studentId, setStudentId] = useState(students.some((student) => student.id === initialStudentId) ? String(initialStudentId) : "");
  const [hwType, setHwType] = useState(initialHomework?.type ?? "말하기 연습");
  const [contentText, setContent] = useState(initialHomework?.title ?? "");
  const [minutes, setMinutes] = useState(String(initialHomework?.targetMinutes ?? 10));
  const [dueDate, setDueDate] = useState(initialHomework?.dueDate ?? defaultDueDate());
  const [submitting, setSubmitting] = useState(false);
  const [content, setContentChoice] = useState<PracticeContent | null>(null);
  const [errors, setErrors] = useState<HomeworkErrors>({});
  const today = localDateInput(new Date());

  const submit = async () => {
    if (submitting) return;
    const minuteValue = Number(minutes);
    const nextErrors: HomeworkErrors = {
      studentId: students.some((student) => student.id === Number(studentId)) ? undefined : "숙제를 배정할 학생을 선택해 주세요.",
      title: contentText.trim().length > 160 ? "숙제 내용은 160자 이하로 입력해 주세요." : undefined,
      minutes: Number.isInteger(minuteValue) && minuteValue >= 1 && minuteValue <= 120 ? undefined : "목표 시간은 1~120분 사이로 입력해 주세요.",
      dueDate: !dueDate ? "마감일을 선택해 주세요." : dueDate < today ? "마감일은 오늘 이후로 설정해 주세요." : undefined,
    };
    setErrors(nextErrors);
    if (Object.values(nextErrors).some(Boolean)) return;
    setSubmitting(true);
    const title = contentText.trim() || (content ? content.label : `${hwType} 숙제`);
    const description = `${hwType} · 목표 ${minuteValue}분`;
    const saved = initialHomework
      ? await onUpdate(initialHomework.id, { title, type: hwType, dueDate, targetMinutes: minuteValue, description })
      : await onAssign({ studentId: Number(studentId), title, type: hwType, dueDate, description, targetMinutes: minuteValue, ...(content ? { exerciseId: Number(content.id) } : {}) });
    setSubmitting(false);
    if (saved) onClose();
  };

  return (
    <Modal title={initialHomework ? "숙제 수정" : "숙제 등록"} onClose={onClose} closeDisabled={submitting}
      footer={<>
        {students.length === 0 && <Notice tone="warning" className="mb-3">먼저 학생을 등록해 주세요.</Notice>}
        {error && !submitting && <Notice tone="error" className="mb-3">{error}</Notice>}
        <div className="flex gap-2">
          <Button variant="neutral" size="lg" fullWidth disabled={submitting} onClick={onClose}>취소</Button>
          <Button size="lg" fullWidth loading={submitting} loadingLabel="저장 중…" disabled={students.length === 0} onClick={() => void submit()}>
            {initialHomework ? "수정 저장" : "숙제 등록"}
          </Button>
        </div>
      </>}>
      <Select label="제자 선택" required disabled={Boolean(initialHomework)} value={studentId} placeholder="학생을 선택하세요"
        onChange={(event) => { setStudentId(event.target.value); setErrors((current) => ({ ...current, studentId: undefined })); }}
        options={students.map((student) => ({ value: String(student.id), label: student.name }))} error={errors.studentId}
        hint={initialHomework ? "배정된 학생은 수정할 수 없어요." : undefined} />

      <fieldset>
        <legend className="mb-2 block text-[13px] font-semibold text-[var(--ink-900)]">숙제 유형</legend>
        <div className="grid grid-cols-2 gap-2">
          {HW_RADIO_TYPES.map((type) => (
            <label key={type} className="flex min-h-12 cursor-pointer items-center gap-2.5 rounded-[var(--radius-lg)] border border-[var(--line-control)] bg-white px-3 transition-colors has-[:checked]:border-[1.5px] has-[:checked]:border-[var(--meadow-700)] has-[:checked]:bg-[var(--meadow-50)] has-[:focus-visible]:shadow-[var(--focus-ring)]">
              <input type="radio" name="homework-type" value={type} checked={hwType === type} onChange={() => setHwType(type)}
                className="h-5 w-5 shrink-0 accent-[var(--brand-primary)]" />
              <span className="text-sm font-medium text-[var(--ink-800)]">{type}</span>
            </label>
          ))}
        </div>
      </fieldset>

      {!initialHomework && <PracticeContentPicker selected={content} onSelect={setContentChoice} />}
      {initialHomework?.exerciseTitle && <p className="rounded-[var(--radius-lg)] bg-[var(--surface-sunken)] px-3.5 py-2.5 text-xs text-[var(--ink-600)]">연습 콘텐츠: <b>{initialHomework.exerciseTitle}</b> (수정할 수 없어요)</p>}

      <Input label="숙제 내용" value={contentText} maxLength={160} onChange={(event) => { setContent(event.target.value); setErrors((current) => ({ ...current, title: undefined })); }}
        placeholder="ㄹ 말 연습하기" hint="비워 두면 숙제 유형으로 제목을 만들어요." error={errors.title} />
      <Input label="목표 시간 (분)" type="number" inputMode="numeric" min={1} max={120} step={5} required value={minutes}
        onChange={(event) => { setMinutes(event.target.value); setErrors((current) => ({ ...current, minutes: undefined })); }} error={errors.minutes} />
      <Input label="마감일" type="date" required min={today} value={dueDate}
        onChange={(event) => { setDueDate(event.target.value); setErrors((current) => ({ ...current, dueDate: undefined })); }} error={errors.dueDate} />
    </Modal>
  );
}
