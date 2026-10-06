"use client";

import type { ReactNode } from "react";
import { ChevronRight, ClipboardList, Clock, Bell, Mic, User } from "lucide-react";
import type { AppScreen, HistoryItem, Homework, LoadState, Session, StudentSummary } from "@/features/student/types";
import { isOverdue } from "@/features/student/utils/mappers";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Mascot from "@/shared/components/mascot/Mascot";
import ProgressBar from "@/shared/components/progressBar/ProgressBar";
import Stat from "@/shared/components/stat/Stat";

const historyTypeLabel: Record<HistoryItem["type"], string> = { ai: "AI 대화", word: "말하기 연습", hw: "숙제" };

/** 저장된 값만 보여준다: 외부 분석 점수 > 텍스트 일치율 > 없음 */
function recentResult(item: HistoryItem) {
  if (item.score !== null) return `${item.score}점`;
  if (item.matchRate !== null) return `텍스트 일치율 ${Math.round(item.matchRate)}%`;
  return item.type === "word" ? "미평가" : "";
}

const quickMenus: { label: string; icon: typeof Mic; tint: string; target: AppScreen }[] = [
  { label: "연습하기", icon: Mic, tint: "bg-[var(--meadow-100)] text-[var(--meadow-800)]", target: { kind: "practice-type" } },
  { label: "숙제하기", icon: ClipboardList, tint: "bg-[var(--coral-100)] text-[var(--coral-700)]", target: { kind: "homework-list" } },
  { label: "히스토리", icon: Clock, tint: "bg-[var(--sky-100)] text-[var(--sky-800)]", target: { kind: "tabs", tab: "history" } },
  { label: "마이페이지", icon: User, tint: "bg-[var(--butter-100)] text-[var(--butter-800)]", target: { kind: "tabs", tab: "mypage" } },
];

function SectionHeader({ id, title, onMore }: { id: string; title: ReactNode; onMore: () => void }) {
  return (
    <div className="mb-2.5 flex items-center justify-between">
      <h2 id={id} className="text-base font-bold text-[var(--ink-900)]">{title}</h2>
      <button type="button" onClick={onMore} className="min-h-9 rounded-lg px-1 text-[13px] font-semibold text-[var(--brand-primary)] hover:underline">전체 보기</button>
    </div>
  );
}

export default function StudentHomeScreen({ onNavigate, student, nextSession, homeworks, homeworkState, profileState, recent, recentState, onRetry }: {
  onNavigate: (target: AppScreen) => void; student: StudentSummary; nextSession: Session | null;
  homeworks: Homework[]; homeworkState: LoadState; profileState: LoadState; recent: HistoryItem[]; recentState: LoadState; onRetry: () => void;
}) {
  const doneCount = homeworks.filter((homework) => homework.done).length;
  const dueHomeworks = homeworks.filter((homework) => !homework.done).slice(0, 2);
  const sessionRate = student.sessionsTotal ? (student.sessionsDone / student.sessionsTotal) * 100 : 0;
  return (
    <div className="space-y-5 px-5 pt-8 pb-2">
      {/* Greeting */}
      <div className="relative min-h-[132px] overflow-hidden rounded-[var(--radius-card-lg)] bg-[var(--sky-100)] p-5">
        <span className="absolute -right-4 -bottom-12 h-36 w-36 rounded-full bg-white/40" aria-hidden="true" />
        <span className="absolute right-20 -bottom-6 h-16 w-16 rounded-full bg-[var(--meadow-400)]/35" aria-hidden="true" />
        <span className="absolute right-3.5 top-3.5 flex h-10 w-10 items-center justify-center rounded-full bg-white/80 text-[var(--ink-600)]" aria-hidden="true">
          <Bell size={18} />
        </span>
        <div className="relative flex min-h-[92px] flex-col">
          <p className="text-sm text-[var(--sky-800)]">안녕하세요,</p>
          <h1 className="mt-0.5 pr-12 font-display text-[26px] leading-tight tracking-[-0.02em] text-[var(--ink-900)]">{student.name} 학생!</h1>
          {profileState === "ready" && (
            <span className="mt-auto inline-flex items-center gap-1.5 self-start rounded-full bg-white px-3 py-1.5 text-[13px] font-bold text-[var(--ink-900)]">
              <span className="h-2 w-2 rounded-full bg-[var(--butter-400)]" aria-hidden="true" />연속 학습 {student.streak}일
            </span>
          )}
        </div>
      </div>

      <section aria-labelledby="today-homework-title">
        <SectionHeader id="today-homework-title" onMore={() => onNavigate({ kind: "homework-list" })}
          title={<>오늘의 숙제{homeworkState === "ready" && homeworks.length > 0 && <span className="ml-2 text-xs font-medium text-[var(--ink-500)]">완료 {doneCount} / {homeworks.length}</span>}</>} />
        {homeworkState === "loading" && <LoadingState label="숙제를 불러오고 있어요…" />}
        {homeworkState === "error" && <ErrorState message="숙제를 불러오지 못했어요." onRetry={onRetry} />}
        {homeworkState === "ready" && dueHomeworks.length === 0 && <EmptyState title="새 숙제가 없어요" description="선생님이 숙제를 내주면 여기에 표시돼요." />}
        {homeworkState === "ready" && dueHomeworks.length > 0 && <Card padding="none" className="divide-y divide-[var(--ink-100)] overflow-hidden">
          {dueHomeworks.map((homework) => {
            const overdue = isOverdue(homework.dueDate, homework.done);
            return (
              <button type="button" key={homework.id} onClick={() => onNavigate({ kind: "homework-list" })}
                className="flex min-h-[64px] w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-[var(--ink-25)]">
                <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${overdue ? "bg-[var(--coral-100)] text-[var(--coral-700)]" : "bg-[var(--butter-100)] text-[var(--butter-800)]"}`} aria-hidden="true">
                  <ClipboardList size={18} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[15px] font-semibold text-[var(--ink-900)]">{homework.title}</span>
                  <span className={`block text-xs ${overdue ? "font-semibold text-[var(--coral-600)]" : "text-[var(--ink-500)]"}`}>마감 {homework.dueDate || "미정"}{overdue ? " · 기한 지남" : ""} · {homework.type}</span>
                </span>
                <ChevronRight size={16} className="shrink-0 text-[var(--ink-300)]" aria-hidden="true" />
              </button>
            );
          })}
        </Card>}
      </section>

      <section aria-labelledby="recent-history-title">
        <SectionHeader id="recent-history-title" title="최근 학습 기록" onMore={() => onNavigate({ kind: "tabs", tab: "history" })} />
        {recentState === "loading" && <LoadingState label="최근 기록을 불러오고 있어요…" />}
        {recentState === "error" && <ErrorState message="최근 학습 기록을 불러오지 못했어요." onRetry={onRetry} />}
        {recentState === "ready" && recent.length === 0 && <EmptyState title="아직 학습 기록이 없어요" description="말하기 연습을 하면 여기에 기록이 쌓여요."
          icon={<Mascot size={56} />} action={<Button onClick={() => onNavigate({ kind: "practice-type" })}>연습 시작하기</Button>} />}
        {recentState === "ready" && recent.length > 0 && (
          <Card as="div" padding="none" className="overflow-hidden">
            <ul className="divide-y divide-[var(--ink-100)]">
              {recent.map((item) => (
                <li key={`${item.type}-${item.id}`} className="flex min-h-[60px] items-center gap-3 px-4 py-3">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-semibold text-[var(--ink-900)]">{item.title}</span>
                    <span className="block text-xs text-[var(--ink-500)]">{historyTypeLabel[item.type]} · {item.date}{item.time ? ` ${item.time}` : ""}</span>
                  </span>
                  <span className="shrink-0 text-xs font-semibold text-[var(--ink-700)]">{recentResult(item)}</span>
                </li>
              ))}
            </ul>
          </Card>
        )}
      </section>
      {/* Next session banner */}
      {nextSession && (
        <div className="flex items-center gap-3 rounded-[var(--radius-card)] bg-[var(--meadow-700)] py-3.5 pr-3.5 pl-4 text-white">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white/15" aria-hidden="true">
            <Clock size={18} />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-xs text-white/85">다음 세션{nextSession.date ? ` · ${nextSession.date}` : ""}</p>
            <p className="truncate text-[15px] font-bold">{nextSession.title}</p>
          </div>
          <button type="button" onClick={() => onNavigate({ kind: "session-exercises", session: nextSession })}
            className="min-h-10 shrink-0 rounded-[var(--radius-md)] bg-[var(--butter-400)] px-4 text-sm font-bold text-[var(--ink-900)] transition-colors hover:bg-[var(--butter-300)]">시작</button>
        </div>
      )}

      {/* Quick menu */}
      <nav aria-label="바로 가기" className="grid grid-cols-4 gap-2">
        {quickMenus.map((menu) => (
          <button type="button" key={menu.label} onClick={() => onNavigate(menu.target)}
            className="group flex flex-col items-center gap-1.5 rounded-[var(--radius-xl)] py-1">
            <span className={`flex h-14 w-14 items-center justify-center rounded-full transition-transform group-hover:-translate-y-0.5 group-active:translate-y-px ${menu.tint}`} aria-hidden="true">
              <menu.icon size={22} />
            </span>
            <span className="text-xs font-semibold text-[var(--ink-800)]">{menu.label}</span>
          </button>
        ))}
      </nav>

      {/* 학습 상태 */}
      {profileState === "ready" && <div className="grid grid-cols-2 gap-2">
        <Card padding="none" className="px-4 py-3.5">
          {/* 텍스트 일치율은 글자 비교 값이라 점수처럼 강조색을 쓰지 않는다 */}
          <Stat label="평균 텍스트 일치율" value={student.averageMatchRate === null ? <span className="font-body text-lg font-bold">기록 없음</span> : student.averageMatchRate}
            unit={student.averageMatchRate === null ? undefined : "%"} caption={`연습 ${student.totalAttempts}회 · 발음 점수 아님`} />
        </Card>
        <Card padding="none" className="px-4 py-3.5">
          <Stat label="완료 세션" value={student.sessionsDone} unit={`/${student.sessionsTotal}`}>
            <ProgressBar value={sessionRate} label={`완료 세션 ${student.sessionsDone}/${student.sessionsTotal}`} className="mt-2.5" />
          </Stat>
        </Card>
      </div>}
      {profileState === "loading" && <LoadingState label="학습 정보를 불러오고 있어요…" />}
      {profileState === "error" && <ErrorState message="학습 정보를 불러오지 못했어요." onRetry={onRetry} />}
    </div>
  );
}
