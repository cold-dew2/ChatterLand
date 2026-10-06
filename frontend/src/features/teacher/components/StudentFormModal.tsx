"use client";

import { useState } from "react";
import { teacherApi } from "@/features/teacher/api/teacherApi";
import { learnerTypeLabel, type LearnerType, type Student, type StudentFormValues } from "@/features/teacher/types";
import { errorMessage } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import { cardClassName } from "@/shared/components/card/Card";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import Modal from "@/shared/components/modal/Modal";
import Select from "@/shared/components/select/Select";
import Tabs from "@/shared/components/tabs/Tabs";
import Textarea from "@/shared/components/textarea/Textarea";

const TAG_OPTIONS = ["발음", "유창성", "이해력", "표현력", "어휘"];
const SESSION_OPTIONS = [10, 16, 20, 24, 30].map((count) => ({ value: String(count), label: `${count}회` }));
const LEARNER_OPTIONS = (Object.keys(learnerTypeLabel) as LearnerType[]).map((value) => ({ value, label: learnerTypeLabel[value] }));
const PHONE_PATTERN = /^[0-9+\-\s()]{7,30}$/;

type FormState = { name: string; age: string; phone: string; tags: string[]; memo: string; sessionsTotal: string; learnerType: LearnerType };
type FormErrors = Partial<Record<"name" | "age" | "phone" | "memo" | "existing", string>>;

export default function StudentFormModal({ initialStudent, onClose, onSave }: {
  initialStudent?: Student | null; onClose: () => void;
  onSave: (values: StudentFormValues, existingStudentId?: number) => Promise<boolean>;
}) {
  const [form, setForm] = useState<FormState>({
    name: initialStudent?.name ?? "", age: initialStudent ? String(initialStudent.age || "") : "", phone: initialStudent?.parentPhone ?? "",
    tags: initialStudent?.tags ?? [], memo: initialStudent?.memo ?? "", sessionsTotal: String(initialStudent?.sessionsTotal || 20),
    learnerType: initialStudent?.learnerType ?? "GENERAL",
  });
  const [linkExisting, setLinkExisting] = useState(false);
  const [availableStudents, setAvailableStudents] = useState<Record<string, unknown>[]>([]);
  const [availableState, setAvailableState] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [availableError, setAvailableError] = useState("");
  const [selectedExisting, setSelectedExisting] = useState<Record<string, unknown> | null>(null);
  const [errors, setErrors] = useState<FormErrors>({});
  const sessionOptions = SESSION_OPTIONS.some((option) => option.value === form.sessionsTotal)
    ? SESSION_OPTIONS : [...SESSION_OPTIONS, { value: form.sessionsTotal, label: `${form.sessionsTotal}회` }];
  const [submitting, setSubmitting] = useState(false);

  const update = (key: keyof FormState) => (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setForm((current) => ({ ...current, [key]: event.target.value }));
    setErrors((current) => ({ ...current, [key === "phone" ? "phone" : key]: undefined }));
  };
  const toggleTag = (tag: string) => setForm((current) => ({ ...current, tags: current.tags.includes(tag) ? current.tags.filter((item) => item !== tag) : [...current.tags, tag] }));

  const loadAvailableStudents = () => {
    if (availableState === "loading" || availableState === "ready") return;
    setAvailableState("loading");
    teacherApi.availableStudents().then((rows) => { setAvailableStudents(rows); setAvailableState("ready"); })
      .catch((error: unknown) => { setAvailableError(errorMessage(error, "기존 학생 목록을 불러오지 못했어요.")); setAvailableState("error"); });
  };

  const validate = (): FormErrors => {
    if (linkExisting && !initialStudent) return { existing: selectedExisting ? undefined : "연결할 학생 계정을 선택해 주세요." };
    const age = Number(form.age);
    return {
      name: !form.name.trim() ? "이름을 입력해 주세요." : form.name.trim().length > 80 ? "이름은 80자 이하로 입력해 주세요." : undefined,
      age: !form.age ? "나이를 입력해 주세요." : !Number.isInteger(age) || age < 1 || age > 18 ? "나이는 1~18세 사이로 입력해 주세요." : undefined,
      phone: form.phone && !PHONE_PATTERN.test(form.phone) ? "연락처는 숫자와 - 기호로 입력해 주세요." : undefined,
      memo: form.memo.length > 1000 ? "메모는 1000자 이하로 입력해 주세요." : undefined,
    };
  };

  const submit = async () => {
    if (submitting) return;
    const nextErrors = validate();
    setErrors(nextErrors);
    if (Object.values(nextErrors).some(Boolean)) return;
    setSubmitting(true);
    const saved = await onSave({
      name: form.name.trim(), age: Number(form.age), status: initialStudent?.status ?? "예정", sessionsTotal: Number(form.sessionsTotal) || 20,
      tags: form.tags, parentPhone: form.phone.trim(), memo: form.memo.trim(), learnerType: form.learnerType,
    }, linkExisting && selectedExisting ? Number(selectedExisting.studentId) : undefined);
    setSubmitting(false);
    if (saved) onClose();
  };

  return (
    <Modal title={initialStudent ? "학생 정보 수정" : "제자 추가"} onClose={onClose} closeDisabled={submitting}
      footer={<Button size="md" fullWidth loading={submitting} loadingLabel="저장 중…" onClick={() => void submit()}>
        {initialStudent ? "정보 저장" : linkExisting ? "기존 계정 연결" : "제자 추가"}
      </Button>}>
      {!initialStudent && (
        <Tabs ariaLabel="등록 방식" variant="outline" value={linkExisting ? "link" : "new"}
          items={[{ value: "new", label: "새 학생 등록" }, { value: "link", label: "기존 계정 연결" }]}
          onChange={(value) => { setLinkExisting(value === "link"); setSelectedExisting(null); setErrors({}); if (value === "link") loadAvailableStudents(); }} />
      )}

      {linkExisting && !initialStudent && (
        <div className="space-y-2" role="radiogroup" aria-label="연결할 학생 계정">
          {availableState === "loading" && <LoadingState label="연결할 수 있는 학생을 찾고 있어요…" />}
          {availableState === "error" && <div className="flex items-center gap-2"><Notice tone="error" className="flex-1">{availableError}</Notice><Button size="sm" variant="line" onClick={loadAvailableStudents}>다시 시도</Button></div>}
          {availableState === "ready" && !availableStudents.length && <p className="py-4 text-center text-xs text-gray-400">연결할 수 있는 기존 학생이 없어요.</p>}
          {availableStudents.map((student) => {
            const selected = Number(selectedExisting?.studentId) === Number(student.studentId);
            return (
              <button type="button" role="radio" aria-checked={selected} key={String(student.studentId)} onClick={() => {
                setSelectedExisting(student);
                setErrors({});
                setForm((current) => ({ ...current, name: String(student.name ?? ""), age: String(student.age ?? ""), phone: String(student.parentPhone ?? ""), tags: String(student.focusAreas ?? "").split(",").filter(Boolean), memo: String(student.memo ?? ""), sessionsTotal: String(student.sessionsTotal ?? 20) }));
              }} className={cardClassName({ padding: "sm", className: `w-full text-left ${selected ? "border-[1.5px] border-[var(--meadow-700)] bg-[var(--meadow-50)]" : ""}` })}>
                <span className="block text-sm font-semibold text-gray-800">{String(student.name ?? "학생")} · {String(student.age ?? "")}세</span>
                <span className="mt-1 block text-xs text-gray-500">{String(student.centerName ?? "센터 미상")} · 기존 학생 계정</span>
              </button>
            );
          })}
          {errors.existing && <Notice tone="error">{errors.existing}</Notice>}
          {selectedExisting && <Notice tone="info">{String(selectedExisting.name)} 학생 계정을 담당 목록에 연결합니다.</Notice>}
        </div>
      )}

      {(!linkExisting || initialStudent) && <>
        <div className="grid grid-cols-2 gap-3">
          <Input label="이름" required value={form.name} maxLength={80} onChange={update("name")} placeholder="홍길동" error={errors.name} />
          <Input label="나이" required value={form.age} type="number" inputMode="numeric" min={1} max={18} onChange={update("age")} placeholder="7" error={errors.age} />
        </div>
        <Input label="보호자 연락처" type="tel" value={form.phone} maxLength={30} onChange={update("phone")} placeholder="010-0000-0000" error={errors.phone} />
        <Select label="학습자 유형" value={form.learnerType} onChange={update("learnerType")} options={LEARNER_OPTIONS}
          hint={form.learnerType === "THERAPY" ? "녹음은 발음 점수 없이 선생님 검토 대기로 저장돼요." : "음성 인식 결과와 목표 문장의 일치도를 보여줘요."} />
        <fieldset>
          <legend className="mb-2 block text-[13px] font-semibold text-[var(--ink-900)]">치료 영역</legend>
          <div className="flex flex-wrap gap-2">
            {TAG_OPTIONS.map((tag) => (
              <button type="button" key={tag} onClick={() => toggleTag(tag)} aria-pressed={form.tags.includes(tag)}
                className={`min-h-9 rounded-full border px-3.5 py-1.5 text-[13px] font-medium transition-colors ${form.tags.includes(tag) ? "border-[var(--meadow-700)] bg-[var(--meadow-700)] text-white" : "border-[var(--line-control)] bg-white text-[var(--ink-700)] hover:border-[var(--meadow-300)]"}`}>{tag}</button>
            ))}
          </div>
        </fieldset>
        <Select label="세션 횟수" value={form.sessionsTotal} onChange={update("sessionsTotal")} options={sessionOptions} />
        <Textarea label="메모" value={form.memo} maxLength={1000} onChange={update("memo")} placeholder="주의사항, 목표 등…" error={errors.memo} />
      </>}
    </Modal>
  );
}
