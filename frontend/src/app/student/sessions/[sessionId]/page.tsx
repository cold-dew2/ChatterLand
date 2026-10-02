import StudentPage from '@/features/student/pages/StudentPage'

export default async function SessionDetail({ params }: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = await params
  return <StudentPage initialRoute="session-detail" sessionId={sessionId} />
}
