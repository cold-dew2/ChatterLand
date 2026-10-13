"use client";

import { useCallback, useState } from "react";
import { BookOpen, X } from "lucide-react";
import ContentBrowser from "@/features/practiceContent/components/ContentBrowser";
import { MAX_SELECTION, removeSelection, type SelectedContent } from "@/features/practiceContent/utils/selection";
import { preview } from "@/features/student/components/PracticeCategoryScreen";
import { mapPracticeContent } from "@/features/student/utils/mappers";
import { practiceCategoryOptions, ruleOptions } from "@/features/student/utils/practiceLabels";
import { teacherApi } from "@/features/teacher/api/teacherApi";
import Button from "@/shared/components/button/Button";
import Select from "@/shared/components/select/Select";
import Tabs from "@/shared/components/tabs/Tabs";

const PICKER_PAGE_SIZE = 10;

/** 고른 콘텐츠 목록(순서대로, 하나씩 뺄 수 있음) */
export function SelectedContentList({ selected, onRemove }: { selected: SelectedContent[]; onRemove?: (id: string) => void }) {
  return (
    <ol aria-label="선택한 연습" className="space-y-1.5">
      {selected.map((item, index) => (
        <li key={item.id} className="flex min-h-11 items-center gap-2 rounded-[var(--radius-lg)] border border-[var(--line-soft)] bg-white px-3 py-1.5">
          <span className="font-number text-sm font-extrabold text-[var(--ink-400)]">{index + 1}</span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-semibold text-[var(--ink-900)]">{item.label}</span>
            {item.categoryName && <span className="block text-xs text-[var(--ink-500)]">{item.categoryName}</span>}
          </span>
          {onRemove && <Button variant="ghost" size="icon" aria-label={`${item.label} 빼기`} onClick={() => onRemove(item.id)}><X size={16} /></Button>}
        </li>
      ))}
    </ol>
  );
}

/**
 * 숙제로 낼 연습 콘텐츠 고르기(여러 개, 선택 사항). 학생 개인 연습과 같은 공통 콘텐츠에서
 * 영역·검색어·발음 유형으로 찾고, 쪽을 넘겨도 고른 것이 유지된다. 콘텐츠를 복제하지 않고 ID만 숙제에 연결한다.
 */
export default function PracticeContentPicker({ selected, onChange }: { selected: SelectedContent[]; onChange: (next: SelectedContent[]) => void }) {
  const [categoryId, setCategoryId] = useState("");
  const [rule, setRule] = useState("");

  const fetchPage = useCallback(async (page: number, keyword: string) => {
    const response = await teacherApi.practiceContents({ keyword: keyword || undefined, categoryId: categoryId || undefined, rule: rule || undefined, page, size: PICKER_PAGE_SIZE });
    return { items: response.content.map(mapPracticeContent), total: response.totalElements };
  }, [categoryId, rule]);

  return (
    <fieldset className="min-w-0 space-y-3">
      <legend className="text-[13px] font-semibold text-[var(--ink-900)]">연습 콘텐츠 (선택)</legend>
      <p className="text-xs leading-relaxed text-[var(--ink-500)]">고르면 학생이 숙제에서 이 연습들을 고른 순서대로 바로 시작하고, 기록이 숙제 연습으로 저장돼요. 최대 {MAX_SELECTION}개.</p>
      <Tabs ariaLabel="연습 영역" variant="chip" value={categoryId}
        items={[{ value: "", label: "전체" }, ...practiceCategoryOptions]}
        onChange={(value) => { setCategoryId(value); if (value !== "articulation") setRule(""); }} />
      {categoryId === "articulation" && (
        <Select label="발음 유형" hideLabel size="sm" value={rule} options={[{ value: "", label: "발음 유형 전체" }, ...ruleOptions]} onChange={(event) => setRule(event.target.value)} />
      )}
      <ContentBrowser sourceKey={`picker-${categoryId}-${rule}`} fetchPage={fetchPage} pageSize={PICKER_PAGE_SIZE}
        searchLabel="콘텐츠 검색" searchButtonLabel="콘텐츠 찾기" searchPlaceholder="낱말·문장·세트 이름 검색"
        selectMode alwaysSelect selection={selected} onSelectionChange={onChange} dense
        describe={preview} icon={BookOpen} color={(content) => content.color}
        emptyTitle="조건에 맞는 연습 콘텐츠가 없어요" listLabel="고를 수 있는 연습 콘텐츠" />
      <section aria-label="선택한 연습 목록" className="space-y-2 rounded-[var(--radius-xl)] bg-[var(--surface-sunken)] p-3">
        <p className="text-[13px] font-bold text-[var(--ink-900)]">선택한 연습 {selected.length}개</p>
        {selected.length === 0
          ? <p className="text-xs text-[var(--ink-500)]">아직 고른 콘텐츠가 없어요. 콘텐츠 없이 자유 숙제로 낼 수도 있어요.</p>
          : <SelectedContentList selected={selected} onRemove={(id) => onChange(removeSelection(selected, id))} />}
      </section>
    </fieldset>
  );
}
