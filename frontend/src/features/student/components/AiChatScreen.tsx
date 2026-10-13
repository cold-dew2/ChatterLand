"use client";

import { useEffect, useRef, useState } from "react";
import { Clock, RotateCcw, Send } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import { useAudioRecorder } from "@/features/student/hooks/useAudioRecorder";
import { isConsentRequired } from "@/features/student/utils/speechErrors";
import { toWav16k } from "@/features/student/utils/wavEncoder";
import { aiConversationTopics } from "@/features/student/utils/aiTopics";
import RecordButton from "@/features/student/components/RecordButton";
import type { PracticeTarget } from "@/features/student/components/RecommendationSection";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import Mascot from "@/shared/components/mascot/Mascot";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

type ChatMsg = { role: "ai" | "user"; text: string };
/** 대화 낱말·주제와 연결된 실제 활성 연습 콘텐츠(서버가 DB에서 찾음) */
type RelatedContent = PracticeTarget & { keyword: string; reason: string };
type Summary = { summary: string; studentTurns: number; relatedContents: RelatedContent[]; nextActivities: { type: string; label: string }[] };

function mapRelated(value: unknown): RelatedContent[] {
  return (Array.isArray(value) ? value as Record<string, unknown>[] : []).map((row) => ({
    exerciseId: Number(row.exerciseId), title: String(row.title ?? ""), categoryId: String(row.categoryId ?? ""), categoryName: String(row.categoryName ?? ""),
    color: String(row.color ?? "var(--brand-primary)"), keyword: String(row.keyword ?? ""), reason: String(row.reason ?? ""),
  }));
}

function RelatedList({ items, onPractice }: { items: RelatedContent[]; onPractice: (item: RelatedContent) => void }) {
  return (
    <ul className="space-y-2" aria-label="대화와 관련된 연습">
      {items.map((item) => (
        <li key={item.exerciseId}>
          <button type="button" onClick={() => onPractice(item)} aria-label={`${item.title} 연습하기, ${item.reason}`}
            className="flex min-h-12 w-full items-center gap-2 rounded-[var(--radius-lg)] border border-[var(--line-soft)] bg-white px-3 py-2 text-left hover:border-[var(--meadow-200)]">
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-bold text-[var(--ink-900)]">{item.title}</span>
              <span className="block truncate text-xs text-[var(--ink-600)]">{item.categoryName} · {item.reason}</span>
            </span>
            <span className="shrink-0 text-xs font-semibold text-[var(--brand-primary)]">연습하기</span>
          </button>
        </li>
      ))}
    </ul>
  );
}
function useTimer() {
  const [seconds, setSeconds] = useState(0);
  const ref = useRef<ReturnType<typeof setInterval> | null>(null);
  useEffect(() => {
    ref.current = setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => { if (ref.current) clearInterval(ref.current); };
  }, []);
  const mm = String(Math.floor(seconds / 60)).padStart(2, "0");
  const ss = String(seconds % 60).padStart(2, "0");
  return `${mm}:${ss}`;
}

/**
 * AI 대화 학습. 주제를 고르면 AI가 학생 말에 이어 후속 질문을 하고(어려워하면 더 쉽게 다시 설명),
 * 대화에 나온 낱말로 실제 연습 콘텐츠를 이어 주며, 끝낼 때 실제 대화로 만든 요약과 다음 활동을 보여 준다.
 * AI는 발음을 평가하지 않는다. AI가 실패하면 요약·메시지를 저장하지 않고 다시 시도할 수 있다.
 */
export default function AiChatScreen({ onBack, topic, onOpenConsent, onPractice, onNewTopic }: {
  onBack: () => void; topic: string; onOpenConsent: () => void;
  /** 관련 연습 콘텐츠를 기존 연습 흐름으로 연다 */
  onPractice?: (item: PracticeTarget) => void;
  /** 다른 주제 고르기 */
  onNewTopic?: () => void;
}) {
  const timer = useTimer();
  const starter = aiConversationTopics.find((item) => item.label === topic)?.starter ?? "안녕! 오늘은 어떤 이야기를 나눠볼까요?";
  const [messages, setMessages] = useState<ChatMsg[]>([{ role: "ai", text: starter }]);
  const [phase, setPhase] = useState<"idle" | "thinking">("idle");
  const [conversationId, setConversationId] = useState("");
  const [messageText, setMessageText] = useState("");
  const [chatError, setChatError] = useState("");
  const [consentNeeded, setConsentNeeded] = useState(false);
  const [related, setRelated] = useState<RelatedContent[]>([]);
  const [relatedError, setRelatedError] = useState(false);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [ending, setEnding] = useState(false);
  const [endError, setEndError] = useState("");
  const recorder = useAudioRecorder();
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    studentApi.createConversation(topic).then((conversation) => setConversationId(conversation.conversationId))
      .catch((error: unknown) => {
        setConsentNeeded(isConsentRequired(error));
        setChatError(errorMessage(error, "대화방을 만들지 못했어요."));
      });
  }, [topic]);

  useEffect(() => {
    setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }), 80);
  }, [messages]);

  const addMessage = async (message: { text?: string; audio?: Blob }, bubbleText: string) => {
    if (!conversationId || phase === "thinking") return false;
    setMessages((prev) => [...prev, { role: "user", text: bubbleText }]);
    setPhase("thinking");
    setChatError("");
    try {
      const response = await studentApi.sendMessage(conversationId, message) as { response?: string; aiText?: string; text?: string; feedback?: string };
      const aiText = response.response ?? response.aiText;
      if (!aiText) throw new Error("AI 응답을 받지 못했어요. 잠시 뒤 다시 시도해 주세요.");
      setMessages((prev) => [...prev, { role: "ai", text: aiText }]);
      setPhase("idle");
      // 대화가 이어질 때마다 관련 연습을 다시 찾는다(실패해도 대화는 계속, 짧게 안내만)
      studentApi.conversationContents(conversationId)
        .then((row) => { setRelated(mapRelated(row.items)); setRelatedError(false); })
        .catch(() => { setRelated([]); setRelatedError(true); });
      return true;
    } catch (error) {
      setConsentNeeded(isConsentRequired(error));
      setChatError(errorMessage(error, "메시지를 보내지 못했어요."));
      setMessages((prev) => prev.slice(0, -1));
      setPhase("idle");
      return false;
    }
  };

  const sendText = (event: React.FormEvent) => {
    event.preventDefault();
    const value = messageText.trim();
    if (!value) return;
    setMessageText("");
    void addMessage({ text: value }, value);
  };

  /** 녹음을 16kHz WAV로 바꿔 보낸다. 서버가 로컬에서 글자로 바꾸고 외부 AI에는 글자만 전달한다. */
  const sendRecording = async () => {
    if (!recorder.recording || phase !== "idle") return;
    setChatError("");
    try {
      const wav = await toWav16k(recorder.recording);
      if (await addMessage({ audio: wav }, "🎤 음성 메시지")) recorder.reset();
    } catch (error) {
      setChatError(errorMessage(error, "녹음을 보내지 못했어요. 다시 시도해 주세요."));
    }
  };

  const spoke = messages.some((message) => message.role === "user");
  /** 끝내기: 나눈 이야기가 있으면 실제 대화로 요약을 만든다(실패하면 대화는 그대로, 다시 시도 가능) */
  const endConversation = async () => {
    if (!conversationId || !spoke) { onBack(); return; }
    setEnding(true); setEndError("");
    try {
      const row = await studentApi.endConversation(conversationId);
      setSummary({
        summary: String(row.summary ?? ""), studentTurns: Number(row.studentTurns ?? 0) || 0, relatedContents: mapRelated(row.relatedContents),
        nextActivities: Array.isArray(row.nextActivities) ? row.nextActivities as Summary["nextActivities"] : [],
      });
    } catch (error) {
      setConsentNeeded(isConsentRequired(error));
      setEndError(errorMessage(error, "대화 요약을 만들지 못했어요. 잠시 뒤 다시 시도해 주세요."));
    } finally {
      setEnding(false);
    }
  };

  if (summary) {
    const canPractice = Boolean(onPractice) && summary.relatedContents.length > 0;
    return (
      <div className="flex min-h-screen flex-col pb-8">
        <PageHeader title={`AI 대화 · ${topic}`} subtitle="대화를 마쳤어요" onBack={onBack} backLabel="연습하기로 돌아가기" />
        <div className="space-y-4 px-5 pt-2">
          <section aria-labelledby="chat-summary-title" className="space-y-2 rounded-[var(--radius-card)] border border-[var(--line-soft)] bg-white p-5">
            <div className="flex items-center gap-2">
              <Mascot size={40} />
              <h3 id="chat-summary-title" className="text-base font-bold text-[var(--ink-900)]">오늘 나눈 이야기</h3>
            </div>
            <p className="text-[15px] leading-relaxed text-[var(--ink-800)]">{summary.summary}</p>
            <p className="text-xs text-[var(--ink-500)]">내가 한 말 {summary.studentTurns}번 · 대화 내용으로 AI가 만든 요약이에요. 발음은 평가하지 않아요.</p>
          </section>
          {canPractice && (
            <section aria-labelledby="chat-practice-title" className="space-y-2">
              <h3 id="chat-practice-title" className="text-[15px] font-bold text-[var(--ink-900)]">다음 활동: 대화와 관련된 연습</h3>
              <RelatedList items={summary.relatedContents} onPractice={(item) => onPractice?.(item)} />
            </section>
          )}
          <div className="grid gap-2 sm:grid-cols-2">
            {onNewTopic && <Button variant="secondary" onClick={onNewTopic}>다른 주제로 이야기하기</Button>}
            <Button variant="line" onClick={onBack}>나가기</Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen flex-col">
      <PageHeader title={`AI 대화 · ${topic}`} onBack={onBack}
        action={<Badge tone="primary" className="py-1"><Clock size={12} aria-hidden="true" />대화 시간 {timer}</Badge>} />

      {/* Chat area */}
      <div className="flex-1 space-y-4 overflow-y-auto px-4 py-4 [scrollbar-width:none]" aria-live="polite">
        {messages.map((msg, i) => (
          <div key={i} className={`flex items-end gap-2 ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
            {msg.role === "ai" && <Mascot size={36} className="mb-0.5" />}
            <div className={`max-w-[75%] px-4 py-2.5 text-[15px] leading-relaxed font-medium
                ${msg.role === "ai" ? "rounded-[20px] rounded-bl-md border border-[var(--line-soft)] bg-white text-[var(--ink-900)]" : "rounded-[20px] rounded-br-md bg-[var(--meadow-700)] text-white"}`}>
              {msg.text}
            </div>
          </div>
        ))}

        {phase === "thinking" && (
          <div className="flex items-end justify-start gap-2" role="status" aria-label="AI가 답을 생각하고 있어요">
            <Mascot size={36} />
            <div className="rounded-[20px] rounded-bl-md border border-[var(--line-soft)] bg-white px-4 py-3.5">
              <div className="flex items-center gap-1">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="h-2 w-2 animate-bounce rounded-full bg-[var(--meadow-400)]" style={{ animationDelay: `${i * 0.18}s` }} />
                ))}
              </div>
            </div>
          </div>
        )}

        {related.length > 0 && onPractice && phase === "idle" && (
          <section aria-labelledby="chat-related-title" className="space-y-2 rounded-[var(--radius-card)] bg-[var(--surface-sunken)] p-3">
            <h3 id="chat-related-title" className="text-[13px] font-bold text-[var(--ink-800)]">대화와 관련된 연습</h3>
            <RelatedList items={related} onPractice={onPractice} />
          </section>
        )}
        {relatedError && phase === "idle" && <p className="text-center text-xs text-[var(--ink-500)]">관련 연습을 불러오지 못했어요. 대화는 계속할 수 있어요.</p>}
        <div ref={bottomRef} />
      </div>

      {/* Bottom bar */}
      <div className="shrink-0 rounded-t-[var(--radius-card-lg)] border-t border-[var(--line-soft)] bg-white px-5 pt-4 pb-6">
        {chatError && <Notice tone="error" className="mb-3 text-center">{chatError}</Notice>}
        {consentNeeded && <Button size="sm" variant="secondary" fullWidth onClick={onOpenConsent} className="mb-3">동의 관리로 가기</Button>}
        <form onSubmit={sendText} className="mb-4 flex items-end gap-2">
          <Input label="대화 메시지" hideLabel size="sm" fieldClassName="min-w-0 flex-1" className="min-h-11" value={messageText}
            onChange={(event) => setMessageText(event.target.value)} disabled={!conversationId || phase !== "idle"} placeholder="하고 싶은 말을 적어보세요" />
          <Button type="submit" disabled={!conversationId || phase !== "idle" || !messageText.trim()} className="min-h-11 shrink-0 rounded-[var(--radius-control-sm)] py-2">보내기</Button>
        </form>
        <div className="mb-4 flex flex-col items-center gap-2.5">
          {recorder.status === "recorded" ? (
            <div className="w-full space-y-2.5">
              {recorder.previewUrl && <audio controls src={recorder.previewUrl} className="h-11 w-full" aria-label="내 음성 메시지 듣기" />}
              <div className="grid grid-cols-[1fr_1.4fr] gap-2.5">
                <Button variant="line" onClick={recorder.reset} disabled={phase === "thinking"}><RotateCcw size={16} aria-hidden="true" />다시 녹음</Button>
                <Button onClick={() => void sendRecording()} loading={phase === "thinking"} loadingLabel="AI가 생각 중…"><Send size={16} aria-hidden="true" />보내기</Button>
              </div>
            </div>
          ) : (
            <>
              <RecordButton size="md" state={recorder.status === "recording" ? "recording" : recorder.status === "requesting" ? "requesting" : "idle"}
                onClick={() => void (recorder.status === "recording" ? recorder.stop() : recorder.start())}
                disabled={phase === "thinking" || !conversationId || recorder.status === "requesting"} startLabel="음성 녹음 시작" />
              <p className={`text-center text-xs font-medium ${recorder.status === "recording" ? "text-[var(--coral-700)]" : "text-[var(--ink-600)]"}`} aria-live="polite">
                {recorder.status === "recording" ? `말하고 있어요… ${recorder.elapsed}초 · 다 말했으면 눌러서 멈춰요`
                  : recorder.status === "requesting" ? "마이크 권한을 확인하고 있어요…"
                    : phase === "thinking" ? "AI가 생각 중..." : "버튼을 눌러 말하고, 다시 눌러 멈춰요"}
              </p>
              {recorder.status === "recording" && <Button size="sm" variant="ghost" onClick={recorder.cancel}>녹음 취소</Button>}
            </>
          )}
          {recorder.error && <Notice tone="error" className="w-full text-center">{recorder.error}</Notice>}
        </div>

        {endError && <Notice tone="error" className="mb-3 text-center">{endError}</Notice>}
        <Button variant="neutral" fullWidth loading={ending} loadingLabel="요약을 만들고 있어요…" disabled={phase === "thinking"} onClick={() => void endConversation()}>
          {spoke ? (endError ? "다시 시도: 대화 끝내고 요약 보기" : "대화 끝내고 요약 보기") : "대화 종료"}
        </Button>
      </div>
    </div>
  );
}
