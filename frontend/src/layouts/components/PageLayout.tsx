import type { ReactNode } from 'react'

type PageLayoutProps = {
  children: ReactNode
  bottomNav?: ReactNode
  /** student: 모바일 앱 폭 · 크림색 점무늬 바탕 / teacher: 데스크톱에서 조금 더 넓은 업무 화면 · 장식 없는 바탕 */
  variant?: 'student' | 'teacher'
  className?: string
}

/**
 * 학생·선생님 화면의 공통 레이아웃.
 * 학생은 데스크톱에서도 과도하게 넓어지지 않도록 가운데 정렬된 앱 폭(max-w-md)을 유지하고,
 * 선생님은 표·통계를 읽기 쉽도록 큰 화면에서 더 넓은 폭(max-w-3xl)을 쓴다.
 */
export default function PageLayout({ children, bottomNav, variant = 'student', className = '' }: PageLayoutProps) {
  const width = variant === 'teacher' ? 'max-w-md md:max-w-3xl' : 'max-w-md'
  const surface = variant === 'teacher' ? 'surface-teacher' : 'surface-student'
  return (
    <div className={`mx-auto flex min-h-screen w-full ${width} flex-col ${surface} shadow-[0_0_0_1px_var(--line-soft)] ${className}`.trim()}>
      <div className={`flex flex-1 flex-col ${bottomNav ? 'pb-24' : ''}`}>{children}</div>
      {bottomNav && <div className={`fixed bottom-0 left-1/2 z-10 w-full ${width} -translate-x-1/2`}>{bottomNav}</div>}
    </div>
  )
}
