import type { ReactNode } from 'react'

/**
 * 학생·선생님 화면의 공통 모바일 폭 레이아웃.
 * 데스크톱에서도 화면이 과도하게 넓어지지 않도록 가운데 정렬된 앱 폭(max-w-md)을 유지한다.
 */
export default function PageLayout({ children, bottomNav, className = '' }: { children: ReactNode; bottomNav?: ReactNode; className?: string }) {
  return (
    <div className={`mx-auto flex min-h-screen w-full max-w-md flex-col bg-white shadow-[0_0_0_1px_rgba(0,0,0,0.03)] ${className}`.trim()}>
      <div className={`flex flex-1 flex-col ${bottomNav ? 'pb-20' : ''}`}>{children}</div>
      {bottomNav && <div className="fixed bottom-0 left-1/2 z-10 w-full max-w-md -translate-x-1/2">{bottomNav}</div>}
    </div>
  )
}
