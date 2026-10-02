import TeacherPage from '@/features/teacher/pages/TeacherPage'

export default async function TeacherReport({ params }: { params: Promise<{ studentId: string }> }) {
  const { studentId } = await params
  return <TeacherPage initialView="analytics" initialStudentId={Number(studentId)} initialAnalyticsTab="상세 리포트" />
}
