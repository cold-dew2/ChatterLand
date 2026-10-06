"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Clock, Home, User } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
import { studentApi } from "@/features/student/api/studentApi";
import AiChatScreen from "@/features/student/components/AiChatScreen";
import AiTopicScreen from "@/features/student/components/AiTopicScreen";
import PracticeBrowseScreen, { asPracticeCategory } from "@/features/student/components/PracticeBrowseScreen";
import PracticeCategoryScreen from "@/features/student/components/PracticeCategoryScreen";
import PracticeListScreen from "@/features/student/components/PracticeListScreen";
import PracticeResultScreen from "@/features/student/components/PracticeResultScreen";
import PracticeRunScreen from "@/features/student/components/PracticeRunScreen";
import PracticeTypeScreen from "@/features/student/components/PracticeTypeScreen";
import SessionExercisesScreen from "@/features/student/components/SessionExercisesScreen";
import SessionListScreen from "@/features/student/components/SessionListScreen";
import SpeechActivityScreen from "@/features/student/components/SpeechActivityScreen";
import StudentHistoryScreen from "@/features/student/components/StudentHistoryScreen";
import StudentHomeScreen from "@/features/student/components/StudentHomeScreen";
import StudentHomeworkScreen from "@/features/student/components/StudentHomeworkScreen";
import StudentMyPageScreen from "@/features/student/components/StudentMyPageScreen";
import { useStudentHome } from "@/features/student/hooks/useStudentHome";
import type { AppScreen, Exercise, ExerciseResult, PracticeCategory, Session } from "@/features/student/types";
import { mapSession } from "@/features/student/utils/mappers";
import { clearAuth } from "@/shared/api/client";
import PageLayout from "@/layouts/components/PageLayout";

function initialStudentScreen(route: string, sessionId?: string): AppScreen {
  if (route === "practice") return { kind: "practice-type" };
  if (route === "ai-chat") return { kind: "ai-chat", topic: "오늘의 기분" };
  if (route === "sessions") return { kind: "session-list" };
  if (route === "session-detail") return { kind: "session-exercises", session: { id: sessionId ?? "", title: "세션", date: "", done: false, exercises: [] } };
  if (route === "history") return { kind: "tabs", tab: "history" };
  if (route === "mypage") return { kind: "tabs", tab: "mypage" };
  return { kind: "tabs", tab: "home" };
}

// ── Main ──────────────────────────────────────────────────────────────────────

export default function StudentPage({ initialRoute = "home", sessionId }: { initialRoute?: string; sessionId?: string }) {
  const router = useRouter();
  const [screen, setScreen] = useState<AppScreen>(() => initialStudentScreen(initialRoute, sessionId));
  const home = useStudentHome();
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    if (initialRoute !== "session-detail" || !sessionId) return;
    studentApi.session(sessionId).then((result) => {
      const payload = result as Record<string, unknown>;
      const detail = payload.session && typeof payload.session === "object" ? payload.session as Record<string, unknown> : payload;
      setScreen({ kind: "session-exercises", session: mapSession({ ...detail, sessionId: detail.sessionId ?? sessionId }) });
    }).catch(() => setScreen({ kind: "session-list" }));
  }, [initialRoute, sessionId]);

  const goHome = () => { setScreen({ kind: "tabs", tab: "home" }); home.reload(); };
  const logout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try { await authApi.logout(); } catch { /* always clear the local session */ } finally { clearAuth(); router.push("/"); }
  };

  const handleActivityComplete = (exercises: Exercise[], session: Session | null, category: PracticeCategory | null,
    origin?: "browse" | "homework", homeworkId?: number) =>
    (exIdx: number, previous: ExerciseResult[], result: ExerciseResult) => {
      const results = [...previous, result];
      if (exIdx + 1 >= exercises.length) {
        if (session) setScreen({ kind: "session-result", session, results });
        else if (category) setScreen({ kind: "practice-result", category, results });
      } else {
        if (session) setScreen({ kind: "activity", session, exIdx: exIdx + 1, results });
        else if (category) setScreen({ kind: "practice-activity", category, exIdx: exIdx + 1, results, origin, homeworkId });
      }
    };

  return <PageLayout bottomNav={screen.kind === "tabs" ? renderNav(screen.tab) : undefined}>{renderScreen()}</PageLayout>;

  function renderNav(tab: "home" | "history" | "mypage") {
    const navTabs = [
      { id: "home" as const, label: "홈", icon: Home },
      { id: "history" as const, label: "히스토리", icon: Clock },
      { id: "mypage" as const, label: "마이페이지", icon: User },
    ];
    return (
      <nav aria-label="학생 메뉴" className="grid grid-cols-3 border-t border-[var(--line-soft)] bg-white px-2 pt-2 pb-[max(1rem,env(safe-area-inset-bottom))]">
        {navTabs.map((t) => {
          const active = tab === t.id;
          return (
            <button type="button" key={t.id} onClick={() => setScreen({ kind: "tabs", tab: t.id })} aria-current={active ? "page" : undefined}
              className={`flex min-h-[52px] flex-col items-center justify-center gap-0.5 rounded-[var(--radius-lg)] text-xs transition-colors
                ${active ? "font-bold text-[var(--meadow-700)]" : "font-medium text-[var(--ink-500)] hover:text-[var(--ink-800)]"}`}>
              <span className={`flex h-7 w-12 items-center justify-center rounded-full ${active ? "bg-[var(--meadow-100)]" : ""}`}>
                <t.icon size={21} aria-hidden="true" strokeWidth={active ? 2.4 : 2} />
              </span>
              {t.label}
            </button>
          );
        })}
      </nav>
    );
  }

  function renderScreen() {
  // Full-screen flows
  if (screen.kind === "ai-chat") {
    return <AiChatScreen topic={screen.topic} onBack={() => setScreen({ kind: "ai-topic" })} onOpenConsent={() => setScreen({ kind: "tabs", tab: "mypage" })} />;
  }
  if (screen.kind === "ai-topic") {
    return <AiTopicScreen onBack={() => setScreen({ kind: "practice-type" })} onSelect={(topic) => setScreen({ kind: "ai-chat", topic })} />;
  }
  if (screen.kind === "practice-type") {
    return (
      <PracticeTypeScreen
        onBack={() => setScreen({ kind: "tabs", tab: "home" })}
        onSelect={(type) => {
          if (type === "ai") setScreen({ kind: "ai-topic" });
          else if (type === "browse") setScreen({ kind: "practice-browse" });
          else setScreen({ kind: "practice-list" });
        }}
      />
    );
  }
  if (screen.kind === "practice-browse") {
    return <PracticeBrowseScreen onBack={() => setScreen({ kind: "practice-type" })}
      onStart={(content) => setScreen({ kind: "practice-activity", category: asPracticeCategory(content), exIdx: 0, results: [], origin: "browse" })} />;
  }
  if (screen.kind === "practice-list") {
    return <PracticeListScreen onBack={() => setScreen({ kind: "practice-type" })}
      onSelect={(cat) => setScreen({ kind: "practice-cat", category: cat })} />;
  }
  if (screen.kind === "practice-cat") {
    return <PracticeCategoryScreen key={screen.category.id} category={screen.category} focusIndex={screen.focusIndex}
      onBack={() => setScreen({ kind: "practice-list" })}
      onStart={(mode, index, order) => setScreen({ kind: "practice-run", category: screen.category, mode, index, order, results: [] })} />;
  }
  if (screen.kind === "practice-run") {
    const run = screen;
    // 뒤로 가기: 흐름을 끝내고 영역 화면으로(전체 순서 연습이면 보던 위치를 목록에서 보여 준다). 이전 세트로 가지 않는다.
    const exitRun = () => setScreen({ kind: "practice-cat", category: run.category, focusIndex: run.mode === "sequential" ? run.index : undefined });
    return <PracticeRunScreen category={run.category} mode={run.mode} index={run.index} order={run.order}
      onIndexChange={(index) => setScreen({ ...run, index })}
      onExit={exitRun}
      onOpenConsent={() => setScreen({ kind: "tabs", tab: "mypage" })}
      onComplete={(result, last) => {
        const results = [...run.results, result];
        setScreen(last ? { kind: "practice-result", category: run.category, results } : { ...run, index: run.index + 1, results });
      }} />;
  }
  if (screen.kind === "practice-activity") {
    const { category, exIdx, results, origin, homeworkId } = screen;
    const backToStart = () => origin === "browse" ? setScreen({ kind: "practice-browse" }) : origin === "homework" ? setScreen({ kind: "homework-list" }) : setScreen({ kind: "practice-cat", category });
    return <SpeechActivityScreen key={`${category.id}-${exIdx}`} onOpenConsent={() => setScreen({ kind: "tabs", tab: "mypage" })} exercises={category.exercises} exIdx={exIdx}
      homeworkId={homeworkId}
      onBack={backToStart}
      onComplete={(result) => handleActivityComplete(category.exercises, null, category, origin, homeworkId)(exIdx, results, result)} />;
  }
  if (screen.kind === "practice-result") {
    return <PracticeResultScreen title={screen.category.label} results={screen.results} onClose={goHome} />;
  }
  if (screen.kind === "session-list") {
    return <SessionListScreen onBack={() => setScreen({ kind: "tabs", tab: "home" })}
      onSelect={(s) => setScreen({ kind: "session-exercises", session: s })} />;
  }
  if (screen.kind === "homework-list") {
    return <StudentHomeworkScreen onBack={goHome}
      onStartPractice={(homework, content) => content
        ? setScreen({ kind: "practice-activity", category: asPracticeCategory(content), exIdx: 0, results: [], origin: "homework", homeworkId: homework.id })
        : setScreen({ kind: "practice-type" })}
      onCompleteHomework={home.markHomeworkDone} />;
  }
  if (screen.kind === "session-exercises") {
    return <SessionExercisesScreen session={screen.session}
      onBack={() => setScreen({ kind: "session-list" })}
      onSelect={(i) => setScreen({ kind: "activity", session: screen.session, exIdx: i, results: [] })} />;
  }
  if (screen.kind === "activity") {
    const { session, exIdx, results } = screen;
    return <SpeechActivityScreen key={`${session.id}-${exIdx}`} onOpenConsent={() => setScreen({ kind: "tabs", tab: "mypage" })} exercises={session.exercises} exIdx={exIdx}
      lessonSessionId={session.id}
      onBack={() => setScreen({ kind: "session-exercises", session })}
      onComplete={(result) => handleActivityComplete(session.exercises, session, null)(exIdx, results, result)} />;
  }
  if (screen.kind === "session-result") {
    return <PracticeResultScreen title={screen.session.title} results={screen.results} onClose={goHome} />;
  }

  const { tab } = screen;
  return (
    <main className="flex-1 overflow-y-auto pb-4 [scrollbar-width:none]">
      {tab === "home" && (
        <StudentHomeScreen student={home.student} nextSession={home.nextSession} homeworks={home.homeworks} homeworkState={home.homeworkState}
          profileState={home.profileState} recent={home.recent} recentState={home.recentState} onRetry={home.reload} onNavigate={(target) => setScreen(target)} />
      )}
      {tab === "history" && <StudentHistoryScreen />}
      {tab === "mypage" && <StudentMyPageScreen student={home.student} loggingOut={loggingOut} onLogout={() => void logout()} />}
    </main>
  );
  }
}
