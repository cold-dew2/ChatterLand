import TeacherPage from '@/features/teacher/pages/TeacherPage'

export default async function TeacherStudentDetail({ params }: { params: Promise<{ studentId: string }> }) {
  const { studentId } = await params
  return <TeacherPage initialView="students" initialStudentId={Number(studentId)} />
}
