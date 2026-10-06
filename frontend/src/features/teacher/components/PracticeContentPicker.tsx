"use client";

import { useEffect, useRef, useState } from "react";
import type { PracticeContent } from "@/features/student/types";
import { mapPracticeContent } from "@/features/student/utils/mappers";
import { difficultyLabel, ruleLabel, ruleOptions } from "@/features/student/utils/practiceLabels";
import { teacherApi } from "@/features/teacher/api/teacherApi";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import Select from "@/shared/components/select/Select";

/** 숙제로 낼 연습 세트 고르기(선택 사항). 검색어·발음 유형으로 찾고 하나를 고른다. */
export default function PracticeContentPicker({ selected, onSelect }: { selected: PracticeContent | null; onSelect: (content: PracticeContent | null) => void }) {
  const [keyword, setKeyword] = useState("");
  const [rule, setRule] = useState("");
  const [query, setQuery] = useState({ keyword: "", rule: "" });
  const [results, setResults] = useState<PracticeContent[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState("");
  const [searched, setSearched] = useState(false);
  const request = useRef(0);

  useEffect(() => {
    if (!searched) return;
    const id = ++request.current;
    teacherApi.practiceContents({ keyword: query.keyword.trim() || undefined, rule: query.rule || undefined, size: 8 })
      .then((page) => { if (id === request.current) { setResults(page.content.map(mapPracticeContent)); setTotal(page.totalElements); setError(""); } })
      .catch((cause) => { if (id === request.current) setError(errorMessage(cause, "연습 콘텐츠를 불러오지 못했어요.")); });
  }, [query, searched]);

  if (selected) return (
    <div className="space-y-1">
      <p className="text-sm font-semibold text-gray-700">연습 콘텐츠</p>
      <div className="flex items-center gap-2 rounded-xl bg-gray-50 p-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-gray-800">{selected.label}</p>
          <p className="truncate text-xs text-gray-500">{selected.items.map((item) => item.word).join(" · ")}</p>
        </div>
        <Button size="sm" variant="line" onClick={() => onSelect(null)}>선택 해제</Button>
      </div>
    </div>
  );

  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-semibold text-gray-700">연습 콘텐츠 (선택)</legend>
      <p className="text-xs text-gray-400">고르면 학생이 숙제에서 이 연습을 바로 시작하고, 기록이 숙제 연습으로 저장돼요.</p>
      <div className="grid grid-cols-2 gap-2">
        <Input label="콘텐츠 검색" hideLabel size="sm" placeholder="낱말·문장 검색" value={keyword} maxLength={50} onChange={(event) => setKeyword(event.target.value)} />
        <Select label="발음 유형" hideLabel size="sm" value={rule} options={[{ value: "", label: "발음 유형 전체" }, ...ruleOptions]} onChange={(event) => setRule(event.target.value)} />
      </div>
      <Button size="sm" variant="secondary" fullWidth onClick={() => { setSearched(true); setQuery({ keyword, rule }); }}>콘텐츠 찾기</Button>
      {error && <Notice tone="error">{error}</Notice>}
      {searched && !error && results.length === 0 && <p className="text-xs text-gray-400">조건에 맞는 연습 콘텐츠가 없어요.</p>}
      {results.length > 0 && (
        <ul className="max-h-56 space-y-1.5 overflow-y-auto" aria-label="연습 콘텐츠 검색 결과">
          {results.map((content) => (
            <li key={content.id} className="flex items-center gap-2 rounded-xl border border-gray-100 p-2">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm text-gray-800">{content.label}</p>
                <p className="flex gap-1 pt-0.5">
                  {content.pronunciationRule && <Badge tone="info">{ruleLabel[content.pronunciationRule]}</Badge>}
                  {content.difficulty && <Badge tone="neutral">{difficultyLabel[content.difficulty]}</Badge>}
                </p>
              </div>
              <Button size="sm" onClick={() => onSelect(content)} aria-label={`${content.label} 선택`}>선택</Button>
            </li>
          ))}
        </ul>
      )}
      {total > results.length && <p className="text-xs text-gray-400">{total}개 중 {results.length}개를 보여 줘요. 검색어로 좁혀 보세요.</p>}
    </fieldset>
  );
}
