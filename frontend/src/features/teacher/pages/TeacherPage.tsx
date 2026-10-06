"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { KeyRound, LogOut } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
import ChangePasswordForm from "@/features/auth/components/ChangePasswordForm";
import AnalyticsView, { type AnalyticsTab } from "@/features/teacher/components/AnalyticsView";
import HomeworkModal from "@/features/teacher/components/HomeworkModal";
import HomeworkView from "@/features/teacher/components/HomeworkView";
import StudentFormModal from "@/features/teacher/components/StudentFormModal";
import StudentsView from "@/features/teacher/components/StudentsView";
import TeacherHomeView from "@/features/teacher/components/TeacherHomeView";
import { useTeacherData } from "@/features/teacher/hooks/useTeacherData";
import type { Homework, Student, TeacherView } from "@/features/teacher/types";
import PageLayout from "@/layouts/components/PageLayout";
import { clearAuth } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Modal from "@/shared/components/modal/Modal";

/** 선생님 화면: 화면 전환과 모달 열림 상태만 관리하고, 데이터·요청은 useTeacherData에 둔다. */
export default function TeacherPage({ initialView = "home", initialStudentId = null, initialAnalyticsTab = "종합 분석" }: { initialView?: TeacherView; initialStudentId?: number | null; initialAnalyticsTab?: AnalyticsTab }) {
  const router = useRouter();
  const data = useTeacherData();
  const [view, setView] = useState<TeacherView>(initialView);
  const [showStudentForm, setShowStudentForm] = useState(false);
  const [editingStudent, setEditingStudent] = useState<Student | null>(null);
  const [showHomework, setShowHomework] = useState(false);
  const [editingHomework, setEditingHomework] = useState<Homework | null>(null);
  const [hwStudentId, setHwStudentId] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const openHw = (id?: number) => {
    if (data.students.length === 0) { data.setMutationError("먼저 학생을 등록한 뒤 숙제를 배정해 주세요."); return; }
    data.setMutationError(""); setEditingHomework(null); setHwStudentId(id ?? data.students[0].id); setShowHomework(true);
  };
  const openEditHomework = (homework: Homework) => {
    data.setMutationError(""); setEditingHomework(homework); setHwStudentId(homework.studentId); setShowHomework(true);
  };
  // 수정 충돌(409) 뒤 목록을 다시 불러오면 최신 버전으로 모달 내용을 다시 채운다(key가 바뀌어 새로 그려진다).
  const latestEditing = editingHomework ? data.homeworks.find((item) => item.id === editingHomework.id) ?? editingHomework : null;
  const logout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try { await authApi.logout(); } catch { /* always clear the local session */ } finally { clearAuth(); router.push("/"); }
  };

  return (
    <PageLayout variant="teacher">
      {showStudentForm && <StudentFormModal initialStudent={editingStudent} onClose={() => { setShowStudentForm(false); setEditingStudent(null); }}
        onSave={(values, existingStudentId) => data.saveStudent(values, editingStudent, existingStudentId)} />}
      {showHomework && <HomeworkModal key={latestEditing ? `${latestEditing.id}:${latestEditing.version ?? ""}` : "new"}
        students={data.students} preStudentId={hwStudentId} initialHomework={latestEditing} error={data.mutationError}
        onClose={() => { setShowHomework(false); setEditingHomework(null); data.setMutationError(""); }} onAssign={data.addHomework} onUpdate={data.updateHomework} />}

      {showPassword && <Modal title="비밀번호 변경" onClose={() => setShowPassword(false)}><ChangePasswordForm /></Modal>}

      <header className="flex items-center justify-between border-b border-[var(--line-soft)] bg-white px-4 py-2.5 sm:px-6 print:hidden">
        <div className="flex items-center gap-2">
          <span className="h-6 w-6 rounded-full bg-[var(--brand-primary)]" aria-hidden="true" />
          <p className="text-base font-extrabold text-[var(--ink-900)]">체터랜드</p>
          <Badge tone="primary" className="rounded-md">선생님</Badge>
        </div>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="icon" onClick={() => setShowPassword(true)} aria-label="비밀번호 변경">
            <KeyRound size={18} />
          </Button>
          <Button variant="ghost" size="icon" onClick={() => void logout()} disabled={loggingOut} aria-label="로그아웃">
            <LogOut size={18} />
          </Button>
        </div>
      </header>

      <main className="pb-8">
        <div aria-live="polite" className="print:hidden">
          {data.mutationError && <Notice tone="error" className="mx-4 mt-4 sm:mx-6">{data.mutationError}</Notice>}
          {data.successMessage && <Notice tone="success" className="mx-4 mt-4 sm:mx-6">{data.successMessage}</Notice>}
        </div>
        {view === "home" && <TeacherHomeView students={data.students} homeworks={data.homeworks} centerName={data.centerName} loadState={data.loadState}
          onRetry={data.reload} onNavigate={setView} />}
        {view === "students" && (
          <StudentsView revision={data.studentRevision} initialStudentId={initialStudentId}
            onBack={() => setView("home")}
            onAddStudent={() => { setEditingStudent(null); setShowStudentForm(true); }}
            onAssignHw={(id) => openHw(id)}
            onEditStudent={(student) => { setEditingStudent(student); setShowStudentForm(true); }}
            onDeleteStudent={data.removeStudent} />
        )}
        {view === "homework" && (
          <HomeworkView students={data.students} homeworks={data.homeworks} loadState={data.loadState} onRetry={data.reload}
            onBack={() => setView("home")} onAssignHw={() => openHw()} onEdit={openEditHomework} onToggle={data.toggleHomework} onDelete={data.deleteHomework} />
        )}
        {view === "analytics" && (
          data.loadState === "loading" ? <LoadingState label="담당 학생을 불러오고 있어요…" />
            : <AnalyticsView initialStudentId={initialStudentId} initialTab={initialAnalyticsTab} students={data.students} onBack={() => setView("home")} />
        )}
      </main>
    </PageLayout>
  );
}
