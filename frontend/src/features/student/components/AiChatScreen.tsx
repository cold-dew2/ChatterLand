"use client";

import { useEffect, useRef, useState } from "react";
import { Bot, Clock, Mic, RotateCcw, Send, Square } from "lucide-react";
import { studentApi } from "@/features/student/api/studentApi";
import { useAudioRecorder } from "@/features/student/hooks/useAudioRecorder";
import { isConsentRequired } from "@/features/student/utils/speechErrors";
import { toWav16k } from "@/features/student/utils/wavEncoder";
import { aiConversationTopics } from "@/features/student/utils/aiTopics";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

type ChatMsg = { role: "ai" | "user"; text: string };
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

export default function AiChatScreen({ onBack, topic, onOpenConsent }: { onBack: () => void; topic: string; onOpenConsent: () => void }) {
  const timer = useTimer();
  const starter = aiConversationTopics.find((item) => item.label === topic)?.starter ?? "안녕! 오늘은 어떤 이야기를 나눠볼까요?";
  const [messages, setMessages] = useState<ChatMsg[]>([{ role: "ai", text: starter }]);
  const [phase, setPhase] = useState<"idle" | "thinking">("idle");
  const [conversationId, setConversationId] = useState("");
  const [messageText, setMessageText] = useState("");
  const [chatError, setChatError] = useState("");
  const [consentNeeded, setConsentNeeded] = useState(false);
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

  return (
    <div className="flex flex-col h-screen bg-white">
      <PageHeader title={`AI 대화 · ${topic}`} onBack={onBack}
        action={<Badge tone="primary"><Clock size={12} aria-hidden="true" />대화 시간 {timer}</Badge>} />

      {/* Chat area */}
      <div className="flex-1 overflow-y-auto px-4 py-5 space-y-4 [scrollbar-width:none]" aria-live="polite">
        {messages.map((msg, i) => (
          <div key={i} className={`flex items-end gap-2 ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
            {msg.role === "ai" && (
              <div className="w-8 h-8 rounded-full shrink-0 flex items-center justify-center mb-0.5 bg-[var(--brand-primary)]" aria-hidden="true">
                <Bot size={16} color="white" />
              </div>
            )}
            <div className={`max-w-[72%] px-4 py-2.5 text-sm leading-relaxed font-medium
                ${msg.role === "ai" ? "bg-gray-100 text-gray-900 rounded-2xl rounded-bl-sm" : "bg-[var(--brand-primary)] text-white rounded-2xl rounded-br-sm"}`}>
              {msg.text}
            </div>
          </div>
        ))}

        {phase === "thinking" && (
          <div className="flex items-end gap-2 justify-start" role="status" aria-label="AI가 답을 생각하고 있어요">
            <div className="w-8 h-8 rounded-full shrink-0 flex items-center justify-center bg-[var(--brand-primary)]" aria-hidden="true">
              <Bot size={16} color="white" />
            </div>
            <div className="bg-gray-100 rounded-2xl rounded-bl-sm px-4 py-3">
              <div className="flex gap-1 items-center">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="w-2 h-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: `${i * 0.18}s` }} />
                ))}
              </div>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Bottom bar */}
      <div className="shrink-0 border-t border-gray-100 px-5 pt-4 pb-8">
        {chatError && <Notice tone="error" className="mb-3 text-center">{chatError}</Notice>}
        {consentNeeded && <Button size="sm" variant="secondary" fullWidth onClick={onOpenConsent} className="mb-3">마이페이지에서 동의 관리하기</Button>}
        <form onSubmit={sendText} className="mb-4 flex items-end gap-2">
          <Input label="대화 메시지" hideLabel size="sm" fieldClassName="min-w-0 flex-1" value={messageText}
            onChange={(event) => setMessageText(event.target.value)} disabled={!conversationId || phase !== "idle"} placeholder="하고 싶은 말을 적어보세요" />
          <Button type="submit" disabled={!conversationId || phase !== "idle" || !messageText.trim()} className="py-2.5">보내기</Button>
        </form>
        <div className="flex flex-col items-center gap-2 mb-4">
          {recorder.status === "recorded" ? (
            <div className="w-full space-y-2">
              {recorder.previewUrl && <audio controls src={recorder.previewUrl} className="w-full" aria-label="내 음성 메시지 듣기" />}
              <div className="flex gap-2">
                <Button variant="neutral" fullWidth onClick={recorder.reset} disabled={phase === "thinking"}><RotateCcw size={16} aria-hidden="true" />다시 녹음</Button>
                <Button fullWidth onClick={() => void sendRecording()} loading={phase === "thinking"} loadingLabel="AI가 생각 중…"><Send size={16} aria-hidden="true" />보내기</Button>
              </div>
            </div>
          ) : (
            <>
              <button type="button" onClick={() => void (recorder.status === "recording" ? recorder.stop() : recorder.start())}
                disabled={phase === "thinking" || !conversationId || recorder.status === "requesting"}
                aria-pressed={recorder.status === "recording"} aria-label={recorder.status === "recording" ? "녹음 중지" : "음성 녹음 시작"}
                className={`w-16 h-16 rounded-full flex items-center justify-center transition-all select-none shadow-lg disabled:bg-gray-200 disabled:shadow-none
                  ${recorder.status === "recording" ? "bg-red-400 scale-110 shadow-red-200" : "bg-[var(--brand-primary)] shadow-blue-200"}`}>
                {recorder.status === "recording" ? <Square size={22} color="white" fill="white" /> : <Mic size={24} color="white" />}
              </button>
              <p className="text-xs text-gray-400 font-medium" aria-live="polite">
                {recorder.status === "recording" ? `말하고 있어요… ${recorder.elapsed}초 · 다 말했으면 눌러서 멈춰요`
                  : recorder.status === "requesting" ? "마이크 권한을 확인하고 있어요…"
                    : phase === "thinking" ? "AI가 생각 중..." : "버튼을 눌러 말하고, 다시 눌러 멈춰요"}
              </p>
              {recorder.status === "recording" && <Button size="sm" variant="ghost" onClick={recorder.cancel}>녹음 취소</Button>}
            </>
          )}
          {recorder.error && <Notice tone="error" className="w-full text-center">{recorder.error}</Notice>}
        </div>

        <Button variant="neutral" fullWidth onClick={onBack} className="rounded-2xl">대화 종료</Button>
      </div>
    </div>
  );
}
