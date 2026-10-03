"use client"

import { useEffect, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { authApi } from '@/features/auth/api/authApi'
import { ApiError, clearAuth, SESSION_EXPIRED_EVENT } from '@/shared/api/client'
import ErrorState from '@/shared/components/feedback/ErrorState'
import LoadingState from '@/shared/components/feedback/LoadingState'
import { paths } from '@/routes/path/paths'

/** 세션 만료 시 로그인 화면 주소. 돌아올 경로는 로그인 화면에서 다시 검증한다(safeReturnPath). */
export function loginAfterExpiry(currentPath: string) {
  return `${paths.login}?expired=1&next=${encodeURIComponent(currentPath)}`
}

export default function RoleLayout({ role, children }: { role: 'STUDENT' | 'TEACHER'; children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const [ready, setReady] = useState(false)
  const [networkError, setNetworkError] = useState(false)
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let active = true
    // 로그인했던 사용자(토큰 보유)의 인증이 끝났으면 만료 안내와 함께 지금 화면으로 돌아올 수 있게 로그인으로 보낸다.
    const hadSession = Boolean(window.sessionStorage.getItem('chatterland.accessToken') || window.sessionStorage.getItem('chatterland.refreshToken'))
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
      router.replace(hadSession ? loginAfterExpiry(pathname) : paths.root)
    })
    return () => { active = false }
  }, [role, pathname, router, retryKey])

  useEffect(() => {
    const handleExpired = () => { clearAuth(); router.replace(loginAfterExpiry(window.location.pathname + window.location.search)) }
    window.addEventListener(SESSION_EXPIRED_EVENT, handleExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleExpired)
  }, [router])

  if (networkError) return <main className="flex min-h-screen items-center justify-center px-5"><div className="w-full max-w-sm"><ErrorState message="서버에 연결할 수 없어요. 네트워크 연결을 확인해 주세요." onRetry={() => { setNetworkError(false); setRetryKey((value) => value + 1) }} /></div></main>
  if (!ready) return <main className="flex min-h-screen items-center justify-center"><LoadingState label="계정을 확인하고 있어요…" /></main>
  return children
}
