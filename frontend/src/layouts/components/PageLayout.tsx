import type { ReactNode } from 'react'

type PageLayoutProps = {
  children: ReactNode
  bottomNav?: ReactNode
  /** 화면 맨 위 막대(선생님 상단 메뉴 등). 바탕은 화면 끝까지, 내용은 콘텐츠 열과 같은 폭으로 맞춘다. */
  topBar?: ReactNode
  /** student: 크림색 점무늬 바탕 / teacher: 표·통계를 읽기 쉽게 PC에서 더 넓은 열 · 장식 없는 바탕 */
  variant?: 'student' | 'teacher'
  /** focused: 연습·결과·설정처럼 한 가지 일에 집중하는 화면. PC에서도 읽기 좋은 좁은 열을 쓴다 */
  width?: 'wide' | 'focused'
  className?: string
}

/** 콘텐츠 열 폭: 모바일은 기존 앱 폭, 태블릿은 화면에 맞춰 넓히고, PC는 읽기 좋은 최대 폭에서 멈춘다. */
export const contentColumnWidth = {
  student: 'max-w-md md:max-w-3xl lg:max-w-5xl',
  teacher: 'max-w-md md:max-w-3xl lg:max-w-6xl',
  focused: 'max-w-md md:max-w-2xl',
} as const

/**
 * 학생·선생님 화면의 공통 레이아웃.
 * 바탕(점무늬·단색)은 화면 전체를 채우고, 콘텐츠만 가운데 열에 둔다(PC에서 양옆이 다른 색 띠로 남지 않음).
 * 하단 메뉴도 바탕은 화면 끝까지, 메뉴 자체는 콘텐츠 열과 같은 폭·위치를 쓴다.
 */
export default function PageLayout({ children, bottomNav, topBar, variant = 'student', width: columnWidth = 'wide', className = '' }: PageLayoutProps) {
  const width = contentColumnWidth[columnWidth === 'focused' ? 'focused' : variant]
  const surface = variant === 'teacher' ? 'surface-teacher' : 'surface-student'
  return (
    <div className={`flex min-h-screen w-full flex-col ${surface}`}>
      {topBar && (
        <div className="border-b border-[var(--line-soft)] bg-white print:hidden">
          <div className={`mx-auto w-full ${width}`}>{topBar}</div>
        </div>
      )}
      <div className={`mx-auto flex w-full flex-1 ${width} flex-col ${bottomNav ? 'pb-24' : ''} ${className}`.trim()}>{children}</div>
      {bottomNav && (
        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-[var(--line-soft)] bg-white">
          <div className={`mx-auto w-full ${width}`}>{bottomNav}</div>
        </div>
      )}
    </div>
  )
}
