import { expect, test } from '@playwright/test'
import { createStudent, loginUi, POLICY } from '../e2e/helpers'
import { caption, installCaption } from './demoCaption'

/**
 * 공모전 시연 보조 클립: AI 대화(실제 Gemini 호출). 60초 본 영상에는 넣지 않고 따로 녹화한다.
 * - 외부 호출은 메시지 1건(= Gemini generateContent 1회)만 한다. 대화 끝내기(요약 = 추가 호출)는 누르지 않는다.
 * - 응답은 받은 그대로 보여 주며 품질 검증 결과가 아니다(자막에 표시). 실패하면 오류를 그대로 남기고 대체 응답을 만들지 않는다.
 * - 학생 계정은 테스트 DB의 시연용 계정이며 AI 대화 외부 전송 동의(AI_CHAT)를 받은 상태로 만든다.
 */
test.use({ launchOptions: { slowMo: 70 } })

test('AI 대화 보조 클립: 실제 Gemini 응답 1회', async ({ page }) => {
  await installCaption(page.context())
  const messageCalls: string[] = []
  page.on('request', (request) => { if (request.method() === 'POST' && /\/api\/v1\/ai\/conversations\/[^/]+\/(messages|end)$/.test(request.url())) messageCalls.push(request.url()) })
  const student = await createStudent('contest-chat', '하늘', {
    consents: { privacy: true, voice: true, aiChat: true, policyVersion: POLICY, guardianConfirmed: true, guardianName: '보호자', guardianRelation: '부모' },
  })

  await loginUi(page, student.email)
  await page.goto('/student/practice')
  await caption(page, 'AI 대화', '주제를 고르고 이야기해요')
  await page.waitForTimeout(1500)
  await page.getByRole('button', { name: /AI 대화하기/ }).click()
  await page.waitForTimeout(1200)
  await page.getByRole('button', { name: /좋아하는 음식/ }).click()
  await page.getByLabel('대화 메시지').fill('나는 사과를 좋아해요')
  await page.waitForTimeout(1200)
  const sent = Date.now()
  const reply = page.waitForResponse((response) => /\/api\/v1\/ai\/conversations\/[^/]+\/messages$/.test(response.url()) && response.request().method() === 'POST', { timeout: 60_000 })
  await page.getByRole('button', { name: '보내기' }).click()
  const response = await reply
  const elapsedMs = Date.now() - sent
  const body = await response.json() as { response?: string; aiText?: string; code?: string; message?: string }
  console.log(`[ai-chat] status=${response.status()} elapsedMs=${elapsedMs} code=${body.code ?? '-'}`)
  if (!response.ok()) {
    console.log(`[ai-chat] 오류 메시지: ${body.message ?? '-'}`)
    expect(messageCalls).toHaveLength(1)
    throw new Error(`AI 대화 응답 실패(${response.status()} ${body.code ?? ''}) — 대체 응답을 만들지 않음`)
  }
  const text = (body.response ?? body.aiText ?? '').trim()
  expect(text.length).toBeGreaterThan(0)
  await expect(page.getByText(text.slice(0, 12)).first()).toBeVisible()
  console.log(`[ai-chat] 응답: ${text}`)
  await caption(page, 'AI 대화 · 실제 Gemini 응답', '자동 생성 응답 · 품질 검증 전')
  await page.waitForTimeout(5000)
  expect(messageCalls, '외부 AI 호출은 메시지 1건만').toHaveLength(1)
})
