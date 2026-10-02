import { ChevronRight } from "lucide-react";
import { aiConversationTopics } from "@/features/student/utils/aiTopics";
import { cardClassName } from "@/shared/components/card/Card";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

export default function AiTopicScreen({ onBack, onSelect }: { onBack: () => void; onSelect: (topic: string) => void }) {
  return (
    <div className="min-h-screen bg-white">
      <PageHeader title="대화 주제 고르기" onBack={onBack} />
      <div className="px-5 py-6">
        <p className="text-sm text-gray-500 mb-5">어떤 이야기를 나눠볼까요?</p>
        <div className="space-y-3">
          {aiConversationTopics.map((item, index) => (
            <button type="button" key={item.label} onClick={() => onSelect(item.label)} className={cardClassName({ interactive: true, className: "flex items-center gap-4 hover:border-blue-200" })}>
              <span className={`grid h-12 w-12 place-items-center rounded-2xl text-xl ${["bg-blue-50", "bg-orange-50", "bg-green-50", "bg-purple-50"][index]}`} aria-hidden="true">{["🏫", "🍓", "🐰", "🌈"][index]}</span>
              <span><b className="block text-sm text-gray-800">{item.label}</b><small className="mt-1 block text-xs text-gray-400">{item.starter}</small></span>
              <ChevronRight className="ml-auto text-gray-300" size={18} aria-hidden="true" />
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
