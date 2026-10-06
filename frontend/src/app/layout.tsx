import type { Metadata, Viewport } from 'next'
import { Jua, Nunito } from 'next/font/google'
import './globals.css'

// 제목(Jua)과 숫자(Nunito) 서체. 본문은 Pretendard(variables.css)
const jua = Jua({ weight: '400', subsets: ['latin'], display: 'swap', variable: '--font-jua' })
const nunito = Nunito({ weight: ['800', '900'], subsets: ['latin'], display: 'swap', variable: '--font-nunito' })

export const metadata: Metadata = {
  title: '채터랜드 | 말하기 성장 연구소',
  description: '우리 아이의 말하기 성장을 함께 응원하는 언어재활 서비스',
}

export const viewport: Viewport = {
  themeColor: '#fbf8f1',
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko" className={`${jua.variable} ${nunito.variable}`}><body>{children}</body></html>
}
