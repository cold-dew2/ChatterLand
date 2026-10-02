import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: '채터랜드 | 말하기 성장 연구소',
  description: '우리 아이의 말하기 성장을 함께 응원하는 언어재활 서비스',
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body>{children}</body></html>
}
