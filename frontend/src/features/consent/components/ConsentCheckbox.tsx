"use client";

import { useId } from "react";

type ConsentCheckboxProps = {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  onDetail?: () => void;
  error?: string;
  bold?: boolean;
};

/** 동의 체크박스 + '자세히' 버튼 + 오류 메시지 */
export default function ConsentCheckbox({ label, checked, onChange, onDetail, error, bold = false }: ConsentCheckboxProps) {
  const id = useId();
  const errorId = `${id}-error`;
  return (
    <div>
      <div className="flex items-start gap-2.5 py-1">
        <input type="checkbox" id={id} checked={checked} onChange={(event) => onChange(event.target.checked)}
          aria-invalid={Boolean(error) || undefined} aria-describedby={error ? errorId : undefined}
          className="mt-0.5 h-4 w-4 shrink-0 cursor-pointer rounded accent-[var(--brand-primary)]" />
        <label htmlFor={id} className={`flex-1 cursor-pointer text-xs leading-relaxed ${bold ? "font-bold text-gray-800" : "text-gray-600"}`}>{label}</label>
        {onDetail && <button type="button" onClick={onDetail} className="shrink-0 text-xs font-semibold text-[var(--brand-primary)] underline-offset-2 hover:underline">자세히</button>}
      </div>
      {error && <p id={errorId} role="alert" className="ml-6 text-xs text-red-600">{error}</p>}
    </div>
  );
}
