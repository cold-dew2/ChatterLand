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
      <div className="flex min-h-9 items-start gap-3 py-1.5">
        <input type="checkbox" id={id} checked={checked} onChange={(event) => onChange(event.target.checked)}
          aria-invalid={Boolean(error) || undefined} aria-describedby={error ? errorId : undefined}
          className="mt-px h-5 w-5 shrink-0 cursor-pointer rounded-md accent-[var(--brand-primary)]" />
        <label htmlFor={id} className={`flex-1 cursor-pointer leading-relaxed ${bold ? "text-[15px] font-bold text-[var(--ink-900)]" : "text-sm text-[var(--ink-700)]"}`}>{label}</label>
        {onDetail && <button type="button" onClick={onDetail} className="shrink-0 rounded-md px-1 text-[13px] font-semibold text-[var(--ink-500)] underline underline-offset-2 hover:text-[var(--brand-primary)]">자세히</button>}
      </div>
      {error && <p id={errorId} role="alert" className="ml-8 text-xs font-medium text-[var(--coral-600)]">{error}</p>}
    </div>
  );
}
