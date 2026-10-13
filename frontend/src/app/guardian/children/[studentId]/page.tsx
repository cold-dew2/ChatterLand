import GuardianPage from '@/features/guardian/pages/GuardianPage'

export default async function GuardianChild({ params }: { params: Promise<{ studentId: string }> }) {
  const { studentId } = await params
  return <GuardianPage studentId={Number(studentId)} />
}
