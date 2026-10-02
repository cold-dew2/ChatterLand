import ReportDownload from '@/features/teacher/pages/ReportDownload'

export default async function DownloadReport({ params, searchParams }: { params: Promise<{ studentId: string }>; searchParams: Promise<{ startDate?: string; endDate?: string }> }) {
  const { studentId } = await params
  const search = await searchParams
  return <ReportDownload studentId={Number(studentId)} startDate={search.startDate ?? '2026-09-01'} endDate={search.endDate ?? '2026-10-01'} />
}
