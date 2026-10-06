"use client";

import { useEffect, useState } from "react";
import { BookOpen, CheckCircle, MessageSquare, Mic, Volume2 } from "lucide-react";
import { teacherApi } from "@/features/teacher/api/teacherApi";
import SpeechReviewPanel from "@/features/teacher/components/SpeechReviewPanel";
import { learnerTypeLabel, type Student } from "@/features/teacher/types";
import { statusTone } from "@/features/teacher/utils/display";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import ConfirmDialog from "@/shared/components/feedback/ConfirmDialog";
import EmptyState from "@/shared/components/feedback/EmptyState";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import PageHeader from "@/shared/components/pageHeader/PageHeader";
import ProgressBar from "@/shared/components/progressBar/ProgressBar";
import Tabs from "@/shared/components/tabs/Tabs";
import StudentPracticePanel from "@/features/teacher/components/StudentPracticePanel";

function ActivityIcon({ type }: { type: string }) {
  const map: Record<string, React.ReactNode> = {
    mic: <Mic size={13} className="text-gray-500" />,
    volume: <Volume2 size={13} className="text-gray-500" />,
    book: <BookOpen size={13} className="text-gray-500" />,
    chat: <MessageSquare size={13} className="text-gray-500" />,
  };
  return <>{map[type] ?? <Mic size={13} className="text-gray-500" />}</>;
}

type SessionRecord = { date: string; type: string; duration: string; activities: { title: string; icon: string; done: boolean; score: number | null }[]; notes: string };

export default function StudentDetailView({ student, onBack, onEdit, onDelete, onAssignHw }: {
  student: Student; onBack: () => void; onEdit: (student: Student) => void; onDelete: (studentId: number) => Promise<boolean>; onAssignHw: (studentId: number) => void;
}) {
  const [detailTab, setDetailTab] = useState<"info" | "learning" | "speech">("info");
  const [sessionTab, setSessionTab] = useState(0);
  const [sessions, setSessions] = useState<SessionRecord[]>([]);
  const [sessionState, setSessionState] = useState<"loading" | "ready" | "error">("loading");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    let active = true;
    teacherApi.sessions(student.id, 0, 10).then((response) => {
      const payload = response as { content?: Record<string, unknown>[] };
      if (!active) return;
      setSessions((payload.content ?? []).map((item) => {
        const completed = Boolean(item.done ?? String(item.status ?? "").toUpperCase() === "COMPLETED");
        const activities = Array.isArray(item.activities) ? item.activities as Record<string, unknown>[] : Array.isArray(item.exercises) ? item.exercises as Record<string, unknown>[] : [];
        return {
          date: String(item.date ?? item.sessionDate ?? ""), type: String(item.type ?? item.title ?? "수업 세션"),
          duration: String(item.duration ?? ""), notes: String(item.notes ?? item.summary ?? ""),
          activities: activities.map((activity) => ({
            title: String(activity.title ?? activity.name ?? "연습"), icon: String(activity.icon ?? (activity.inputType === "mic" ? "mic" : "book")),
            done: Boolean(activity.done ?? activity.completed ?? completed), score: typeof activity.score === "number" ? activity.score : null,
          })),
        };
      }));
      setSessionState("ready");
    }).catch(() => { if (active) setSessionState("error"); });
    return () => { active = false; };
  }, [student.id]);

  const session = sessions[sessionTab];
  return (
    <div className="flex flex-col min-h-screen">
      {confirmDelete && <ConfirmDialog
        title="학생을 담당 목록에서 삭제할까요?"
        description={`${student.name} 학생과의 담당 연결을 해제합니다. 학생 계정과 학습 기록은 삭제되지 않아요.`}
        pending={deleting}
        onCancel={() => setConfirmDelete(false)}
        onConfirm={async () => {
          setDeleting(true);
          const removed = await onDelete(student.id);
          setDeleting(false);
          if (removed) { setConfirmDelete(false); onBack(); }
        }}
      />}
      <PageHeader tone="bar" title="제자 상세 정보" onBack={onBack} backLabel="제자 목록으로" />

      <div className="flex items-center gap-3 bg-white px-4 py-4 sm:px-6">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[var(--sky-200)]" aria-hidden="true">
          <span className="text-lg font-extrabold text-[var(--ink-900)]">{student.name[0]}</span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-lg font-bold text-[var(--ink-900)]">{student.name}<span className="ml-2 text-sm font-normal text-[var(--ink-500)]">({student.age}세)</span></p>
          <p className="text-xs text-[var(--ink-500)]">{learnerTypeLabel[student.learnerType]}</p>
        </div>
        <Badge tone={statusTone[student.status] ?? "neutral"}>{student.status}</Badge>
      </div>

      <Tabs ariaLabel="학생 상세 탭" variant="underline" value={detailTab} onChange={setDetailTab}
        items={[{ value: "info", label: "분류 정보" }, { value: "learning", label: "학습 현황" }, { value: "speech", label: "음성 검토" }]} />

      <div className="flex-1 overflow-y-auto [scrollbar-width:none]">
        {detailTab === "info" && (
          <div className="px-4 py-4 sm:px-6">
            <dl className="rounded-[var(--radius-card)] border border-[var(--line-soft)] bg-white px-4">
              {[
                { label: "언어재활센터", value: student.centerName || "미등록" },
                { label: "보호자 연락처", value: student.parentPhone || "미등록" },
                { label: "나이", value: `${student.age}세` },
                { label: "학습자 유형", value: learnerTypeLabel[student.learnerType] },
                { label: "치료 영역", value: student.tags.join(", ") || "미등록" },
                { label: "최근 방문일", value: student.lastSession },
              ].map((row, i) => (
                <div key={row.label} className={`flex items-center gap-2 py-3.5 ${i > 0 ? "border-t border-[var(--ink-100)]" : ""}`}>
                  <dt className="w-28 shrink-0 text-sm text-[var(--ink-500)]">{row.label}</dt>
                  <dd className="min-w-0 break-words text-sm font-semibold text-[var(--ink-900)]">{row.value}</dd>
                </div>
              ))}
            </dl>
            {student.memo && (
              <Card tone="butter" padding="md" className="mt-3">
                <p className="mb-1 text-xs font-semibold text-[var(--butter-800)]">메모</p>
                <p className="whitespace-pre-line text-sm leading-relaxed text-[var(--ink-800)]">{student.memo}</p>
              </Card>
            )}
          </div>
        )}

        {detailTab === "learning" && (
          <div className="space-y-4 px-4 py-4 sm:px-6">
            <StudentPracticePanel studentId={student.id} />
            <Card className="flex items-center gap-4">
              <div>
                <p className="text-xs font-medium text-[var(--ink-500)]">외부 분석 점수</p>
                {student.score !== null
                  ? <p className="font-number text-3xl font-black text-[var(--ink-900)]">{student.score}<span className="ml-1 text-base text-[var(--ink-400)]">점</span></p>
                  : <p className="pt-1"><Badge tone="neutral">미평가</Badge></p>}
              </div>
              <div className="flex-1">
                <ProgressBar value={student.sessionsTotal ? (student.sessionsDone / student.sessionsTotal) * 100 : 0} label={`세션 진행 ${student.sessionsDone}/${student.sessionsTotal}회`} />
                <p className="mt-1 text-xs text-[var(--ink-500)]">세션 {student.sessionsDone}/{student.sessionsTotal}회</p>
              </div>
            </Card>

            {sessionState === "loading" && <LoadingState label="수업 기록을 불러오고 있어요…" />}
            {sessionState === "error" && <ErrorState message="수업 기록을 불러오지 못했어요." />}
            {sessionState === "ready" && sessions.length === 0 && <EmptyState title="수업 기록이 없어요" />}
            {sessions.length > 0 && (
              <Tabs ariaLabel="수업 날짜" variant="chip" value={sessionTab} onChange={setSessionTab}
                items={sessions.map((record, index) => ({ value: index, label: record.date || `세션 ${index + 1}` }))} />
            )}
            {session && (
              <>
                <Card>
                  <div className="flex items-center gap-3 mb-3 flex-wrap">
                    <p className="text-sm font-semibold text-gray-700">{session.type}</p>
                    {session.duration && <p className="text-xs text-gray-400">{session.duration}</p>}
                  </div>
                  {session.activities.length === 0 && <p className="text-xs text-gray-400">등록된 활동이 없어요.</p>}
                  <ul className="space-y-2">
                    {session.activities.map((act, i) => (
                      <li key={i} className={`flex items-center gap-3 rounded-[var(--radius-md)] p-2.5 ${act.done ? "bg-[var(--surface-sunken)]" : "opacity-60"}`}>
                        <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-[var(--line-control)] bg-white" aria-hidden="true"><ActivityIcon type={act.icon} /></div>
                        <span className="flex-1 text-sm text-gray-700">{act.title}</span>
                        {act.done
                          ? <span className="flex items-center gap-1.5">{act.score !== null && <span className="text-sm font-bold text-gray-700">{act.score}점</span>}<CheckCircle size={14} className="text-green-500" aria-label="완료" /></span>
                          : <span className="text-xs text-gray-400">미완료</span>}
                      </li>
                    ))}
                  </ul>
                </Card>
                {session.notes && (
                  <Card tone="butter" padding="md">
                    <p className="mb-1.5 text-xs font-semibold text-[var(--butter-800)]">치료사 노트</p>
                    <p className="text-sm leading-relaxed text-[var(--ink-800)]">{session.notes}</p>
                  </Card>
                )}
              </>
            )}
          </div>
        )}

        {detailTab === "speech" && <div className="px-4 py-4 sm:px-6"><SpeechReviewPanel studentId={student.id} /></div>}
      </div>

      <div className="grid grid-cols-3 gap-2 border-t border-[var(--line-soft)] bg-white px-4 pt-3 pb-6 sm:px-6">
        <Button variant="secondary" onClick={() => onAssignHw(student.id)}>숙제 배정</Button>
        <Button variant="line" onClick={() => onEdit(student)}>정보 수정</Button>
        <Button variant="dangerLine" onClick={() => setConfirmDelete(true)}>제자 삭제</Button>
      </div>
    </div>
  );
}
