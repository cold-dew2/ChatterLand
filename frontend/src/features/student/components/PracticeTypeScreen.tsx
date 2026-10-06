import type { ReactNode } from "react";
import { Apple, Search } from "lucide-react";
import BackButton from "@/shared/components/backButton/BackButton";
import { cardClassName } from "@/shared/components/card/Card";
import Mascot from "@/shared/components/mascot/Mascot";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";

function TypeCard({ icon, title, description, onClick }: { icon: ReactNode; title: string; description: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className={cardClassName({ tone: "raised", interactive: true, className: "flex min-h-[96px] items-center gap-4 rounded-[var(--radius-card-lg)]" })}>
      {icon}
      <span className="min-w-0 flex-1">
        <span className="block text-[17px] font-bold text-[var(--ink-900)]">{title}</span>
        <span className="mt-0.5 block text-[13px] leading-snug text-[var(--ink-600)]">{description}</span>
      </span>
    </button>
  );
}

export default function PracticeTypeScreen({ onSelect, onBack }: {
  onSelect: (type: "ai" | "word" | "browse") => void;
  onBack: () => void;
}) {
  return (
    <div className="flex min-h-screen flex-col px-5 pt-8 pb-8">
      <p className="mb-3.5 text-xs text-[var(--ink-500)]">연습하기 &gt; 연습 유형 선택</p>
      <BackButton onClick={onBack} label="홈으로 돌아가기" />

      <PageTitle title="연습 유형 선택" description="어떤 연습을 할까요?" className="mt-5 mb-6" />

      <div className="space-y-3">
        <TypeCard onClick={() => onSelect("browse")} title="전체 연습 찾기" description="발음 유형·난이도로 골라서 스스로 연습해요"
          icon={<span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-[30px] bg-[var(--meadow-100)] text-[var(--meadow-800)]" aria-hidden="true"><Search size={28} /></span>} />
        <TypeCard onClick={() => onSelect("ai")} title="AI 대화하기" description="AI 로봇과 대화를 연습해요"
          icon={<Mascot size={64} />} />
        <TypeCard onClick={() => onSelect("word")} title="단어 말하기" description="그림을 보고 단어를 말해요"
          icon={<span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-[30px] bg-[var(--coral-100)] text-[var(--coral-600)]" aria-hidden="true"><Apple size={30} /></span>} />
      </div>
    </div>
  );
}
