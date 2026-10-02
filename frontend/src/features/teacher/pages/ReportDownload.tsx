"use client";

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { teacherApi } from '@/features/teacher/api/teacherApi'
import { errorMessage } from '@/shared/api/client'
import Button from '@/shared/components/button/Button'
import ErrorState from '@/shared/components/feedback/ErrorState'
import LoadingState from '@/shared/components/feedback/LoadingState'
import Notice from '@/shared/components/feedback/Notice'

export default function ReportDownload({ studentId, startDate, endDate }: { studentId: number; startDate: string; endDate: string }) {
  const router = useRouter()
  const [state, setState] = useState<'loading' | 'done' | 'error'>('loading')
  const [error, setError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let active = true
    teacherApi.downloadReport(studentId, startDate, endDate).then((file) => {
      if (!active) return
      const url = URL.createObjectURL(file)
      const link = document.createElement('a')
      link.href = url
      link.download = `채터랜드_학생리포트_${studentId}.pdf`
      link.click()
      URL.revokeObjectURL(url)
      setState('done')
    }).catch((cause: unknown) => {
      if (active) { setError(errorMessage(cause, '리포트를 다운로드하지 못했어요.')); setState('error') }
    })
    return () => { active = false }
  }, [studentId, startDate, endDate, retryKey])

  return <main className="mx-auto flex min-h-screen max-w-sm flex-col items-center justify-center gap-4 px-6 text-center">
    <h1 className="text-xl font-bold text-gray-800">학생 리포트 다운로드</h1>
    {state === 'loading' && <LoadingState label="학생 리포트를 준비하고 있어요…" />}
    {state === 'done' && <Notice tone="success">리포트 다운로드가 완료됐어요.</Notice>}
    {state === 'error' && <div className="w-full"><ErrorState message={error} onRetry={() => { setState('loading'); setRetryKey((value) => value + 1) }} /></div>}
    <Button fullWidth onClick={() => router.replace(`/teacher/reports/${studentId}`)}>리포트로 돌아가기</Button>
  </main>
}
