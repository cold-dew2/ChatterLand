import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, type Page } from '@playwright/test'

const here = path.dirname(fileURLToPath(import.meta.url))

export const API = 'http://localhost:8080'
export const PASSWORD = 'Chatterland!234'
export const POLICY = '2026-10-01'
export const mic = (file: string) => ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream',
  `--use-file-for-fake-audio-capture=${path.resolve(here, 'fixtures', file)}`]

/** E2E 계정은 e2e-<시나리오>-<시각>@example.test 형식으로 만든다(테스트 DB에서만). */
export const uniqueEmail = (scenario: string) => `e2e-${scenario}-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.test`

export async function api<T = Record<string, unknown>>(method: string, url: string, body?: unknown, token?: string): Promise<T> {
  const response = await fetch(API + url, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  if (!response.ok) throw new Error(`${method} ${url} → ${response.status} ${text}`)
  return (text ? JSON.parse(text) : null) as T
}

export async function createStudent(scenario: string, name: string, extra: Record<string, unknown> = {}) {
  const email = uniqueEmail(scenario)
  const created = await api<{ studentId: number }>('POST', '/api/v1/auth/signup', {
    role: 'STUDENT', name, email, password: PASSWORD, centerId: 1, termsAgreed: true, age: 8,
    consents: { privacy: true, voice: true, aiChat: false, policyVersion: POLICY, guardianConfirmed: true, guardianName: '보호자', guardianRelation: '부모' }, ...extra,
  })
  const token = (await api<{ accessToken: string }>('POST', '/api/v1/auth/login', { email, password: PASSWORD })).accessToken
  return { email, name, studentId: created.studentId, token }
}

export async function createTeacher(scenario: string, name: string) {
  const email = uniqueEmail(scenario)
  await api('POST', '/api/v1/auth/signup', { role: 'TEACHER', name, email, password: PASSWORD, centerId: 1, termsAgreed: true, consents: { privacy: true, policyVersion: POLICY } })
  const token = (await api<{ accessToken: string }>('POST', '/api/v1/auth/login', { email, password: PASSWORD })).accessToken
  return { email, name, token }
}

/** 테스트용 메일 서버(mailpit)에 도착한 비밀번호 재설정 메일에서 6자리 인증 코드를 꺼낸다. */
export async function mailCode(email: string): Promise<string> {
  let code: string | null = null
  await expect.poll(async () => {
    const list = await (await fetch('http://127.0.0.1:8025/api/v1/messages')).json() as { messages: { ID: string; To: { Address: string }[] }[] }
    const message = list.messages.find((item) => item.To.some((to) => to.Address === email))
    if (!message) return null
    const detail = await (await fetch(`http://127.0.0.1:8025/api/v1/message/${message.ID}`)).json() as { Text: string }
    code = detail.Text.match(/\b(\d{6})\b/)?.[1] ?? null
    return code
  }, { timeout: 20_000 }).not.toBeNull()
  return code!
}

export async function loginUi(page: Page, email: string, role: 'student' | 'teacher' = 'student') {
  await page.goto('/login')
  if (role === 'teacher') await page.getByRole('tab', { name: /선생님/ }).click()
  await page.locator('#login-email').fill(email)
  await page.locator('#login-password').fill(PASSWORD)
  await page.locator('form').getByRole('button', { name: '로그인' }).click()
  await page.waitForURL(role === 'teacher' ? /\/teacher/ : /\/student/)
}

/** 연습 화면에서 녹음 → 분석. 녹음 길이는 "말하는 시간"이라 고정 시간으로 둔다. 분석 결과는 화면 상태로 기다린다. */
export async function recordAndAnalyze(page: Page, speakMs: number) {
  await page.getByRole('button', { name: '녹음 시작' }).click()
  await expect(page.getByText(/녹음 중…/).first()).toBeVisible()
  await page.waitForTimeout(speakMs)
  await page.getByRole('button', { name: '녹음 중지' }).click()
  await expect(page.getByLabel('내 녹음 재생')).toBeVisible()
  await page.getByRole('button', { name: '분석하기' }).click()
}

export async function openPractice(page: Page, category: RegExp, exercise: RegExp) {
  await page.goto('/student/practice')
  await page.getByRole('button', { name: /단어 말하기/ }).click()
  await page.getByRole('button', { name: category }).first().click()
  await page.getByRole('button', { name: exercise }).first().click()
}

/** 가로 스크롤과 화면 밖으로 나간 요소를 찾는다(장식용 fixed 배경 제외). */
export async function layoutProblems(page: Page) {
  return page.evaluate(() => {
    const problems: string[] = []
    const width = window.innerWidth
    if (document.documentElement.scrollWidth > width + 1) problems.push(`가로 스크롤 ${document.documentElement.scrollWidth}px > ${width}px`)
    for (const element of Array.from(document.querySelectorAll<HTMLElement>('body *'))) {
      const rect = element.getBoundingClientRect()
      if (rect.width === 0 || rect.height === 0) continue
      const style = getComputedStyle(element)
      if (style.visibility === 'hidden' || style.display === 'none') continue
      if (rect.right > width + 1 || rect.left < -1) problems.push(`${element.tagName.toLowerCase()}.${String(element.className).slice(0, 40)} [${Math.round(rect.left)}~${Math.round(rect.right)}]`)
    }
    return problems.slice(0, 10)
  })
}

