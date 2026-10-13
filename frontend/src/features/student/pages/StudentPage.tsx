"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Clock, Home, User } from "lucide-react";
import { useLogout } from "@/features/auth/hooks/useLogout";
import { studentApi } from "@/features/student/api/studentApi";
import AiChatScreen from "@/features/student/components/AiChatScreen";
import AiTopicScreen from "@/features/student/components/AiTopicScreen";
import { practiceScreenFor } from "@/features/student/components/RecommendationSection";
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
import LearningStatusScreen from "@/features/student/components/LearningStatusScreen";
import StudentConsentScreen from "@/features/student/components/StudentConsentScreen";
import StudentGuardianScreen from "@/features/student/components/StudentGuardianScreen";
import StudentProfileEditScreen from "@/features/student/components/StudentProfileEditScreen";
import { useStudentHome } from "@/features/student/hooks/useStudentHome";
import type { AppScreen, Exercise, ExerciseResult, PracticeCategory, Session } from "@/features/student/types";
import { mapSession } from "@/features/student/utils/mappers";
import PageLayout from "@/layouts/components/PageLayout";
import { paths } from "@/routes/path/paths";
import type { SelectedContent } from "@/features/practiceContent/utils/selection";

function initialStudentScreen(route: string, sessionId?: string): AppScreen {
  if (route === "practice") return { kind: "practice-type" };
  if (route === "ai-chat") return { kind: "ai-chat", topic: "오늘의 기분" };
  if (route === "sessions") return { kind: "session-list" };
  if (route === "session-detail") return { kind: "session-exercises", session: { id: sessionId ?? "", title: "세션", date: "", done: false, exercises: [] } };
  if (route === "history") return { kind: "tabs", tab: "history" };
  if (route === "mypage") return { kind: "tabs", tab: "mypage" };
  if (route === "mypage-profile") return { kind: "my-profile" };
  if (route === "mypage-consents") return { kind: "my-consents" };
  if (route === "mypage-guardians") return { kind: "my-guardians" };
  return { kind: "tabs", tab: "home" };
}

// ── Main ──────────────────────────────────────────────────────────────────────

export default function StudentPage({ initialRoute = "home", sessionId }: { initialRoute?: string; sessionId?: string }) {
  const router = useRouter();
  const [screen, setScreen] = useState<AppScreen>(() => initialStudentScreen(initialRoute, sessionId));
  const home = useStudentHome();
  const { logout, loggingOut } = useLogout();
  // 영역별로 고른 콘텐츠(연습하고 돌아와도 유지). ID와 이름만 둔다.
  const [selections, setSelections] = useState<Record<string, SelectedContent[]>>({});

  useEffect(() => {
    if (initialRoute !== "session-detail" || !sessionId) return;
    studentApi.session(sessionId).then((result) => {
      const payload = result as Record<string, unknown>;
      const detail = payload.session && typeof payload.session === "object" ? payload.session as Record<string, unknown> : payload;
      setScreen({ kind: "session-exercises", session: mapSession({ ...detail, sessionId: detail.sessionId ?? sessionId }) });
    }).catch(() => setScreen({ kind: "session-list" }));
  }, [initialRoute, sessionId]);

  const goHome = () => { setScreen({ kind: "tabs", tab: "home" }); home.reload(); };
  // 마이페이지 하위 화면은 주소가 있는 별도 페이지다(새로고침·뒤로 가기가 그 화면 기준으로 동작)
  const openConsents = () => router.push(paths.student.myConsents);
  const backToMyPage = () => router.push(paths.student.myPage);

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

  // 목록·탭 화면은 넓은 열, 연습·결과·설정처럼 한 가지 일에 집중하는 화면은 PC에서도 읽기 좋은 좁은 열
  const wideScreens: AppScreen["kind"][] = ["tabs", "practice-type", "practice-list", "practice-cat", "practice-browse", "session-list", "homework-list", "session-exercises", "learning-status"];
  return <PageLayout bottomNav={screen.kind === "tabs" ? renderNav(screen.tab) : undefined} width={wideScreens.includes(screen.kind) ? "wide" : "focused"}>{renderScreen()}</PageLayout>;

  function renderNav(tab: "home" | "history" | "mypage") {
    const navTabs = [
      { id: "home" as const, label: "홈", icon: Home },
      { id: "history" as const, label: "히스토리", icon: Clock },
      { id: "mypage" as const, label: "마이페이지", icon: User },
    ];
    return (
      <nav aria-label="학생 메뉴" className="grid grid-cols-3 bg-white px-2 pt-2 pb-[max(1rem,env(safe-area-inset-bottom))]">
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
    return <AiChatScreen key={screen.topic} topic={screen.topic} onBack={() => setScreen({ kind: "ai-topic" })} onOpenConsent={openConsents}
      onPractice={(item) => setScreen(practiceScreenFor(item))} onNewTopic={() => setScreen({ kind: "ai-topic" })} />;
  }
  if (screen.kind === "my-profile") {
    return <StudentProfileEditScreen age={home.student.age} onBack={backToMyPage} onProfileChanged={home.reload} />;
  }
  if (screen.kind === "my-consents") {
    return <StudentConsentScreen onBack={backToMyPage} />;
  }
  if (screen.kind === "my-guardians") {
    return <StudentGuardianScreen onBack={backToMyPage} />;
  }
  if (screen.kind === "learning-status") {
    const from = screen.from;
    return <LearningStatusScreen onBack={() => setScreen({ kind: "tabs", tab: from })} backLabel={from === "home" ? "홈으로 돌아가기" : "마이페이지로 돌아가기"} />;
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
    const categoryId = screen.category.id;
    return <PracticeCategoryScreen key={categoryId} category={screen.category} focusIndex={screen.focusIndex}
      selection={selections[categoryId] ?? []} onSelectionChange={(next) => setSelections((current) => ({ ...current, [categoryId]: next }))}
      onBack={() => setScreen({ kind: "practice-list" })}
      onStart={(mode, index, order, extra) => setScreen({ kind: "practice-run", category: screen.category, mode, index, order, results: [],
        keyword: extra?.keyword, selection: extra?.selection, origin: "category" })} />;
  }
  if (screen.kind === "practice-run") {
    const run = screen;
    // 뒤로 가기: 흐름을 끝내고 직전 목록으로(숙제면 숙제 목록, 전체 순서 연습이면 영역 목록의 보던 위치). 이전 세트로 가지 않는다.
    const exitRun = () => run.origin === "homework"
      ? setScreen({ kind: "homework-list" })
      : setScreen({ kind: "practice-cat", category: run.category, focusIndex: run.mode === "sequential" ? run.index : undefined });
    return <PracticeRunScreen category={run.category} mode={run.mode} index={run.index} order={run.order}
      selection={run.selection} keyword={run.keyword} homeworkId={run.homeworkId}
      onIndexChange={(index) => setScreen({ ...run, index })}
      onExit={exitRun}
      onOpenConsent={openConsents}
      onComplete={(result, last) => {
        const results = [...run.results, result];
        setScreen(last ? { kind: "practice-result", category: run.category, results } : { ...run, index: run.index + 1, results });
      }} />;
  }
  if (screen.kind === "practice-activity") {
    const { category, exIdx, results, origin, homeworkId } = screen;
    const backToStart = () => origin === "browse" ? setScreen({ kind: "practice-browse" }) : origin === "homework" ? setScreen({ kind: "homework-list" }) : setScreen({ kind: "practice-cat", category });
    return <SpeechActivityScreen key={`${category.id}-${exIdx}`} onOpenConsent={openConsents} exercises={category.exercises} exIdx={exIdx}
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
      onStartPractice={(homework) => homework.exercises && homework.exercises.length > 0
        // 숙제 콘텐츠(한 개 이상)를 선생님이 정한 순서로 연습하고, 기록은 숙제 연습으로 저장한다.
        ? setScreen({ kind: "practice-run", category: { id: "homework", label: homework.title, color: "var(--brand-primary)", desc: "", exercises: [] },
            mode: "selected", index: 0, selection: homework.exercises, homeworkId: homework.id, origin: "homework", results: [] })
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
    return <SpeechActivityScreen key={`${session.id}-${exIdx}`} onOpenConsent={openConsents} exercises={session.exercises} exIdx={exIdx}
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
          profileState={home.profileState} recent={home.recent} recentState={home.recentState} onRetry={home.reload} onNavigate={(target) => setScreen(target)}
          notificationSettings={home.notificationSettings} />
      )}
      {tab === "history" && <StudentHistoryScreen />}
      {tab === "mypage" && <StudentMyPageScreen student={home.student} loggingOut={loggingOut} onLogout={() => void logout()}
        onOpenLearningStatus={() => setScreen({ kind: "learning-status", from: "mypage" })}
        onOpenProfile={() => router.push(paths.student.myProfile)} onOpenConsents={openConsents}
        onOpenGuardians={() => router.push(paths.student.myGuardians)}
        onNotificationSettingsSaved={home.setNotificationSettings} />}
    </main>
  );
  }
}
