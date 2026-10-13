import type { ElementType } from "react";
import { Check, ChevronRight } from "lucide-react";
import { cardClassName } from "@/shared/components/card/Card";

type ContentCardProps = {
  /** 목록 전체 순서 번호(1부터) */
  number?: number;
  title: string;
  description?: string;
  icon: ElementType;
  color: string;
  /** open: 누르면 바로 연습(또는 열기) · select: 누르면 선택/해제 */
  mode: "open" | "select";
  selected?: boolean;
  onOpen?: () => void;
  onToggle?: () => void;
  /** 선생님 숙제 모달처럼 좁은 곳에서 쓰는 촘촘한 모양 */
  dense?: boolean;
  /** 선택 모드에서 더 고를 수 없을 때(최대 개수) 아직 고르지 않은 카드를 막는다 */
  disabled?: boolean;
};

/**
 * 연습 콘텐츠 카드(학생 연습 목록·선생님 숙제 콘텐츠 고르기 공통).
 * 선택 모드에서는 카드를 눌러도 연습으로 넘어가지 않고 선택만 바뀐다. 선택 상태는 체크·테두리·바탕과 aria-pressed로 알린다.
 */
export default function ContentCard({ number, title, description, icon: Icon, color, mode, selected = false, onOpen, onToggle, dense = false, disabled = false }: ContentCardProps) {
  const select = mode === "select";
  const iconSize = dense ? "h-10 w-10" : "h-12 w-12";
  return (
    <button type="button" onClick={select ? onToggle : onOpen} disabled={disabled}
      aria-pressed={select ? selected : undefined} aria-label={select ? `${title} 선택` : undefined}
      className={cardClassName({ interactive: !disabled, padding: dense ? "sm" : "md",
        className: `flex w-full items-center gap-3 text-left ${dense ? "min-h-[64px]" : "min-h-[76px]"} ${select && selected ? "border-[1.5px] border-[var(--meadow-700)] bg-[var(--meadow-50)]" : ""} ${disabled ? "cursor-not-allowed opacity-50" : ""}` })}>
      {select ? (
        <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-[7px] border-2 ${selected ? "border-[var(--meadow-700)] bg-[var(--meadow-700)] text-white" : "border-[var(--ink-300)] bg-white"}`} aria-hidden="true">
          {selected && <Check size={15} strokeWidth={3} />}
        </span>
      ) : (
        <span className={`flex ${iconSize} shrink-0 items-center justify-center rounded-full`} aria-hidden="true"
          style={{ backgroundColor: `color-mix(in srgb, ${color} 20%, white)`, color: `color-mix(in srgb, ${color} 70%, var(--ink-950))` }}>
          <Icon size={dense ? 18 : 22} />
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className={`block ${dense ? "text-[15px]" : "text-base"} font-bold text-[var(--ink-900)]`}>
          {number !== undefined && <span className="mr-1 font-number text-[15px] font-extrabold text-[var(--ink-400)]">{number}</span>}
          {number !== undefined && " "}{title}
        </span>
        {description && <span className="mt-0.5 block truncate text-[13px] text-[var(--ink-500)]">{description}</span>}
      </span>
      {select
        ? selected && <span className="shrink-0 text-xs font-bold text-[var(--meadow-800)]">선택됨</span>
        : <ChevronRight size={18} className="shrink-0 text-[var(--ink-300)]" aria-hidden="true" />}
    </button>
  );
}
