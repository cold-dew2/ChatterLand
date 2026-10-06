import { Bot, ChevronLeft, Search } from "lucide-react";
import Button from "@/shared/components/button/Button";
import { cardClassName } from "@/shared/components/card/Card";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";

export default function PracticeTypeScreen({ onSelect, onBack }: {
  onSelect: (type: "ai" | "word" | "browse") => void;
  onBack: () => void;
}) {
  return (
    <div className="min-h-screen bg-white flex flex-col">
      <div className="px-4 pt-12 pb-2">
        <p className="text-xs text-gray-400 mb-4">연습하기 &gt; 연습 유형 선택</p>
        <Button variant="ghost" size="icon" onClick={onBack} aria-label="홈으로 돌아가기" className="-ml-2">
          <ChevronLeft size={22} />
        </Button>
      </div>

      <PageTitle title="연습 유형 선택" description="어떤 연습을 할까요?" className="px-5 mb-6" />

      <div className="px-5 space-y-3">
        <button type="button" onClick={() => onSelect("browse")} className={cardClassName({ interactive: true, className: "flex items-center gap-4" })}>
          <div className="w-16 h-16 rounded-2xl bg-green-50 flex items-center justify-center shrink-0" aria-hidden="true">
            <Search size={30} className="text-[var(--brand-primary)]" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-base font-bold text-gray-900 mb-0.5">전체 연습 찾기</p>
            <p className="text-sm text-gray-400 leading-snug">발음 유형·난이도로 골라서 스스로 연습해요</p>
          </div>
        </button>

        <button type="button" onClick={() => onSelect("ai")} className={cardClassName({ interactive: true, className: "flex items-center gap-4" })}>
          <div className="w-16 h-16 rounded-2xl bg-blue-50 flex items-center justify-center shrink-0" aria-hidden="true">
            <Bot size={32} className="text-[var(--brand-primary)]" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-base font-bold text-gray-900 mb-0.5">AI 대화하기</p>
            <p className="text-sm text-gray-400 leading-snug">AI 로봇과 대화를 연습해요</p>
          </div>
        </button>

        <button type="button" onClick={() => onSelect("word")} className={cardClassName({ interactive: true, className: "flex items-center gap-4" })}>
          <div className="w-16 h-16 rounded-2xl bg-red-50 flex items-center justify-center shrink-0" aria-hidden="true">
            <svg viewBox="0 0 48 48" width="40" height="40" xmlns="http://www.w3.org/2000/svg">
              <path d="M24 8 Q26 4 30 5" stroke="#4ade80" strokeWidth="2" fill="none" strokeLinecap="round" />
              <ellipse cx="24" cy="28" rx="14" ry="16" fill="#ef4444" />
              <ellipse cx="24" cy="26" rx="14" ry="14" fill="#f87171" />
              <ellipse cx="18" cy="20" rx="5" ry="4" fill="#fca5a5" opacity="0.5" />
              <path d="M24 16 Q18 20 16 28 Q16 36 22 40 Q14 38 10 28 Q10 18 20 16Z" fill="#dc2626" opacity="0.5" />
              <ellipse cx="24" cy="28" rx="14" ry="16" fill="none" stroke="#dc2626" strokeWidth="0.5" />
              <path d="M22 10 Q24 6 27 7" stroke="#16a34a" strokeWidth="2.5" fill="none" strokeLinecap="round" />
            </svg>
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-base font-bold text-gray-900 mb-0.5">단어 말하기</p>
            <p className="text-sm text-gray-400 leading-snug">그림을 보고 단어를 말해요</p>
          </div>
        </button>
      </div>
    </div>
  );
}
