"use client";

import { ChevronRight, ClipboardList, Clock, Bell, Mic, User } from "lucide-react";
import type { AppScreen, HistoryItem, Homework, LoadState, Session, StudentSummary } from "@/features/student/types";
import { isOverdue } from "@/features/student/utils/mappers";
import Button from "@/shared/components/button/Button";
import Card, { cardClassName } from "@/shared/components/card/Card";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import MenuCard from "@/shared/components/menuCard/MenuCard";

const historyTypeLabel: Record<HistoryItem["type"], string> = { ai: "AI 대화", word: "말하기 연습", hw: "숙제" };

/** 저장된 값만 보여준다: 외부 분석 점수 > 문장 일치도 > 없음 */
function recentResult(item: HistoryItem) {
  if (item.score !== null) return `${item.score}점`;
  if (item.matchRate !== null) return `일치도 ${Math.round(item.matchRate)}%`;
  return item.type === "word" ? "미평가" : "";
}

export default function StudentHomeScreen({ onNavigate, student, nextSession, homeworks, homeworkState, profileState, recent, recentState, onRetry }: {
  onNavigate: (target: AppScreen) => void; student: StudentSummary; nextSession: Session | null;
  homeworks: Homework[]; homeworkState: LoadState; profileState: LoadState; recent: HistoryItem[]; recentState: LoadState; onRetry: () => void;
}) {
  const doneCount = homeworks.filter((homework) => homework.done).length;
  const dueHomeworks = homeworks.filter((homework) => !homework.done).slice(0, 2);
  return (
    <div>
      {/* Greeting */}
      <div className="flex items-start justify-between px-5 pt-10 pb-6">
        <div>
          <p className="text-sm text-gray-400">안녕하세요,</p>
          <h1 className="text-xl font-bold text-gray-900">{student.name} 학생!</h1>
        </div>
        <span className="relative p-2 mt-1" aria-hidden="true">
          <Bell size={22} className="text-gray-400" />
        </span>
      </div>

      <section className="px-5 pb-5" aria-labelledby="today-homework-title">
        <div className="mb-3 flex items-center justify-between">
          <h2 id="today-homework-title" className="text-base font-bold text-gray-900">
            오늘의 숙제{homeworkState === "ready" && homeworks.length > 0 && <span className="ml-2 text-xs font-medium text-gray-400">완료 {doneCount} / {homeworks.length}</span>}
          </h2>
          <button type="button" onClick={() => onNavigate({ kind: "homework-list" })} className="text-xs font-semibold text-[var(--brand-primary)]">전체 보기</button>
        </div>
        {homeworkState === "loading" && <LoadingState label="숙제를 불러오고 있어요…" />}
        {homeworkState === "error" && <ErrorState message="숙제를 불러오지 못했어요." onRetry={onRetry} />}
        {homeworkState === "ready" && dueHomeworks.length === 0 && <EmptyState title="새 숙제가 없어요" description="선생님이 숙제를 내주면 여기에 표시돼요." />}
        {homeworkState === "ready" && dueHomeworks.length > 0 && <div className="space-y-2">
          {dueHomeworks.map((homework) => {
            const overdue = isOverdue(homework.dueDate, homework.done);
            return (
              <button type="button" key={homework.id} onClick={() => onNavigate({ kind: "homework-list" })}
                className={cardClassName({ padding: "sm", className: "flex w-full items-center gap-3 text-left" })}>
                <ClipboardList size={18} className="shrink-0 text-[var(--brand-primary)]" aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-gray-800">{homework.title}</span>
                  <span className={`block text-xs ${overdue ? "text-red-500" : "text-gray-400"}`}>마감 {homework.dueDate || "미정"}{overdue ? " · 기한 지남" : ""} · {homework.type}</span>
                </span>
                <ChevronRight size={16} className="text-gray-300" aria-hidden="true" />
              </button>
            );
          })}
        </div>}
      </section>

      {profileState === "ready" && <div className="grid grid-cols-3 gap-2 px-5 pb-5">
        {[
          { label: "평균 문장 일치도", value: student.averageMatchRate === null ? "기록 없음" : `${student.averageMatchRate}%`, sub: `연습 ${student.totalAttempts}회`, icon: "✦", tint: "bg-blue-50 text-blue-700" },
          { label: "완료 세션", value: `${student.sessionsDone}/${student.sessionsTotal}`, icon: "✓", tint: "bg-green-50 text-green-700" },
          { label: "연속 학습", value: `${student.streak}일`, icon: "🌱", tint: "bg-amber-50 text-amber-700" },
        ].map((stat) => (
          <Card key={stat.label} padding="sm">
            <div className={`mb-2 grid h-7 w-7 place-items-center rounded-lg text-xs ${stat.tint}`} aria-hidden="true">{stat.icon}</div>
            <p className="text-sm font-bold text-gray-800">{stat.value}</p>
            <p className="mt-1 text-[10px] text-gray-400">{stat.label}{stat.sub ? ` · ${stat.sub}` : ""}</p>
          </Card>
        ))}
      </div>}
      {profileState === "loading" && <LoadingState label="학습 정보를 불러오고 있어요…" />}
      {profileState === "error" && <div className="px-5 pb-5"><ErrorState message="학습 정보를 불러오지 못했어요." onRetry={onRetry} /></div>}

      <section className="px-5 pb-5" aria-labelledby="recent-history-title">
        <div className="mb-3 flex items-center justify-between">
          <h2 id="recent-history-title" className="text-base font-bold text-gray-900">최근 학습 기록</h2>
          <button type="button" onClick={() => onNavigate({ kind: "tabs", tab: "history" })} className="text-xs font-semibold text-[var(--brand-primary)]">전체 보기</button>
        </div>
        {recentState === "loading" && <LoadingState label="최근 기록을 불러오고 있어요…" />}
        {recentState === "error" && <ErrorState message="최근 학습 기록을 불러오지 못했어요." onRetry={onRetry} />}
        {recentState === "ready" && recent.length === 0 && <EmptyState title="아직 학습 기록이 없어요" description="말하기 연습을 하면 여기에 기록이 쌓여요."
          action={<Button size="sm" onClick={() => onNavigate({ kind: "practice-type" })}>연습 시작하기</Button>} />}
        {recentState === "ready" && recent.length > 0 && (
          <ul className="space-y-2">
            {recent.map((item) => (
              <li key={`${item.type}-${item.id}`} className={cardClassName({ padding: "sm", className: "flex items-center gap-3" })}>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-gray-800">{item.title}</span>
                  <span className="block text-xs text-gray-400">{historyTypeLabel[item.type]} · {item.date}{item.time ? ` ${item.time}` : ""}</span>
                </span>
                <span className="shrink-0 text-xs font-bold text-gray-600">{recentResult(item)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Next session banner */}
      {nextSession && (
        <Card tone="muted" className="mx-5 mb-5 flex items-center gap-3">
          <div className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 bg-[var(--brand-primary)]" aria-hidden="true">
            <Clock size={17} color="white" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs text-gray-400">다음 세션</p>
            <p className="text-sm font-semibold text-gray-800 truncate">{nextSession.title}</p>
            <p className="text-xs text-gray-400">{nextSession.date}</p>
          </div>
          <Button size="sm" onClick={() => onNavigate({ kind: "session-exercises", session: nextSession })}>시작</Button>
        </Card>
      )}

      {/* Menu cards */}
      <div className="px-5 space-y-3">
        <MenuCard icon={Mic} color="var(--brand-teal)" title="연습하기" description="말하기 연습을 시작해요"
          onClick={() => onNavigate({ kind: "practice-type" })} />
        <MenuCard icon={ClipboardList} color="var(--brand-coral)" title="숙제하기" description="선생님이 내준 숙제를 해요"
          onClick={() => onNavigate({ kind: "homework-list" })} />
        <MenuCard icon={Clock} color="var(--brand-slate)" title="히스토리" description="내 기록과 진도를 확인해요"
          onClick={() => onNavigate({ kind: "tabs", tab: "history" })} />
        <MenuCard icon={User} color="var(--brand-purple)" title="마이페이지" description="내 정보와 설정을 관리해요"
          onClick={() => onNavigate({ kind: "tabs", tab: "mypage" })} />
      </div>
    </div>
  );
}
