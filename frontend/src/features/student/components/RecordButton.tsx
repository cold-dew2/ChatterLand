import { Mic, Square } from "lucide-react";
import Spinner from "@/shared/components/spinner/Spinner";

type RecordButtonProps = {
  /** idle: 녹음 전 · requesting: 마이크 권한 확인 중 · recording: 녹음 중 */
  state: "idle" | "requesting" | "recording";
  onClick: () => void;
  disabled?: boolean;
  /** 녹음 시작 버튼의 이름(스크린리더). 화면마다 다르게 쓴다 */
  startLabel: string;
  stopLabel?: string;
  size?: "lg" | "md";
};

/**
 * 말하기 연습·AI 대화의 둥근 녹음 버튼. 녹음 중에는 코랄색과 퍼지는 테두리로 표시하고,
 * 상태는 aria-pressed와 버튼 이름으로도 알려 준다(상태 문구는 버튼 아래에 화면마다 따로 보여 준다).
 */
export default function RecordButton({ state, onClick, disabled = false, startLabel, stopLabel = "녹음 중지", size = "lg" }: RecordButtonProps) {
  const recording = state === "recording";
  const dimension = size === "lg" ? "h-[104px] w-[104px]" : "h-[72px] w-[72px]";
  const iconSize = size === "lg" ? 34 : 26;
  return (
    <button type="button" onClick={onClick} disabled={disabled} aria-pressed={recording} aria-label={recording ? stopLabel : startLabel}
      className={`flex shrink-0 select-none items-center justify-center rounded-full text-white transition-all duration-200 disabled:cursor-not-allowed disabled:bg-[var(--ink-300)] disabled:shadow-none
        ${dimension} ${recording
          ? "bg-[var(--coral-400)] shadow-[0_0_0_12px_rgba(240,140,114,0.2),0_0_0_26px_rgba(240,140,114,0.09)]"
          : "bg-[var(--meadow-700)] shadow-[0_10px_24px_rgba(62,122,71,0.32)] hover:enabled:bg-[var(--meadow-800)] active:enabled:scale-95"}`}>
      {recording ? <Square size={iconSize - 6} fill="currentColor" aria-hidden="true" />
        : state === "requesting" ? <Spinner size="md" />
          : <Mic size={iconSize} aria-hidden="true" />}
    </button>
  );
}
