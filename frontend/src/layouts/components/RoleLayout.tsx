"use client"

import { useEffect, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { authApi } from '@/features/auth/api/authApi'
import { ApiError, clearAuth, SESSION_EXPIRED_EVENT } from '@/shared/api/client'
import ErrorState from '@/shared/components/feedback/ErrorState'
import LoadingState from '@/shared/components/feedback/LoadingState'
import { paths } from '@/routes/path/paths'

export default function RoleLayout({ role, children }: { role: 'STUDENT' | 'TEACHER'; children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const [ready, setReady] = useState(false)
  const [networkError, setNetworkError] = useState(false)
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let active = true
    authApi.me().then((user) => {
      if (!active) return
      if (user.role !== role) {
        router.replace(user.role === 'TEACHER' ? paths.teacher.home : paths.student.home)
        return
      }
      setReady(true)
    }).catch((cause: unknown) => {
      if (!active) return
      // 서버에 연결하지 못한 경우는 로그아웃시키지 않고 재시도할 수 있게 한다.
      if (cause instanceof ApiError && cause.code === 'NETWORK_ERROR') { setNetworkError(true); return }
      clearAuth()
      router.replace(paths.root)
    })
    return () => { active = false }
  }, [role, pathname, router, retryKey])

  useEffect(() => {
    const handleExpired = () => { clearAuth(); router.replace(`${paths.login}?expired=1`) }
    window.addEventListener(SESSION_EXPIRED_EVENT, handleExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleExpired)
  }, [router])

  if (networkError) return <main className="flex min-h-screen items-center justify-center px-5"><div className="w-full max-w-sm"><ErrorState message="서버에 연결할 수 없어요. 네트워크 연결을 확인해 주세요." onRetry={() => { setNetworkError(false); setRetryKey((value) => value + 1) }} /></div></main>
  if (!ready) return <main className="flex min-h-screen items-center justify-center"><LoadingState label="계정을 확인하고 있어요…" /></main>
  return children
}
