"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
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

  const openHw = (id?: number) => {
    if (data.students.length === 0) { data.setMutationError("먼저 학생을 등록한 뒤 숙제를 배정해 주세요."); return; }
    data.setMutationError(""); setEditingHomework(null); setHwStudentId(id ?? data.students[0].id); setShowHomework(true);
  };
  const openEditHomework = (homework: Homework) => {
    data.setMutationError(""); setEditingHomework(homework); setHwStudentId(homework.studentId); setShowHomework(true);
  };
  const logout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try { await authApi.logout(); } catch { /* always clear the local session */ } finally { clearAuth(); router.push("/"); }
  };

  return (
    <PageLayout>
      {showStudentForm && <StudentFormModal initialStudent={editingStudent} onClose={() => { setShowStudentForm(false); setEditingStudent(null); }}
        onSave={(values, existingStudentId) => data.saveStudent(values, editingStudent, existingStudentId)} />}
      {showHomework && <HomeworkModal students={data.students} preStudentId={hwStudentId} initialHomework={editingHomework}
        onClose={() => { setShowHomework(false); setEditingHomework(null); }} onAssign={data.addHomework} onUpdate={data.updateHomework} />}

      <header className="flex items-center justify-between px-5 py-4 border-b border-gray-100 print:hidden">
        <div className="flex items-center gap-2">
          <p className="text-sm font-bold text-gray-800">체터랜드</p>
          <Badge tone="neutral">선생님</Badge>
        </div>
        <Button variant="ghost" size="icon" onClick={() => void logout()} disabled={loggingOut} aria-label="로그아웃">
          <LogOut size={18} />
        </Button>
      </header>

      <main className="pb-8">
        <div aria-live="polite" className="print:hidden">
          {data.mutationError && <Notice tone="error" className="mx-5 mt-4">{data.mutationError}</Notice>}
          {data.successMessage && <Notice tone="success" className="mx-5 mt-4">{data.successMessage}</Notice>}
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
