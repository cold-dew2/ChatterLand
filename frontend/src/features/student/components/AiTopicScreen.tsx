import { ChevronRight } from "lucide-react";
import { aiConversationTopics } from "@/features/student/utils/aiTopics";
import { cardClassName } from "@/shared/components/card/Card";
import Mascot from "@/shared/components/mascot/Mascot";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

export default function AiTopicScreen({ onBack, onSelect }: { onBack: () => void; onSelect: (topic: string) => void }) {
  return (
    <div className="min-h-screen">
      <PageHeader title="대화 주제 고르기" onBack={onBack} />
      <div className="px-5 pt-2 pb-8">
        <div className="mb-5 flex items-center gap-3">
          <Mascot size={56} />
          <p className="rounded-[var(--radius-xl)] rounded-tl-sm bg-white px-4 py-3 text-[15px] font-medium text-[var(--ink-800)] shadow-[var(--shadow-card)]">어떤 이야기를 나눠볼까요?</p>
        </div>
        <div className="space-y-3">
          {aiConversationTopics.map((item, index) => (
            <button type="button" key={item.label} onClick={() => onSelect(item.label)} className={cardClassName({ tone: "raised", interactive: true, className: "flex min-h-[80px] items-center gap-4" })}>
              <span className={`grid h-14 w-14 shrink-0 place-items-center rounded-[var(--radius-card)] text-2xl ${["bg-[var(--sky-100)]", "bg-[var(--coral-100)]", "bg-[var(--meadow-100)]", "bg-[var(--butter-100)]"][index]}`} aria-hidden="true">{["🏫", "🍓", "🐰", "🌈"][index]}</span>
              <span className="min-w-0"><b className="block text-base text-[var(--ink-900)]">{item.label}</b><small className="mt-0.5 block text-[13px] text-[var(--ink-600)]">{item.starter}</small></span>
              <ChevronRight className="ml-auto shrink-0 text-[var(--ink-300)]" size={18} aria-hidden="true" />
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
