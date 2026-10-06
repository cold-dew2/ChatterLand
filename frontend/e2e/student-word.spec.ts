import { expect, test } from '@playwright/test'
import { api, createStudent, createTeacher, loginUi, mic, openPractice, recordAndAnalyze } from './helpers'

test.use({ launchOptions: { args: mic('mic-radio.wav') }, permissions: ['microphone'] })

test('E2E-02/03 낱말 녹음 → 분석 결과 → 학습 기록 저장 → 새로고침 후 유지', async ({ page }) => {
  const student = await createStudent('word', '낱말학생')
  await loginUi(page, student.email)
  await expect(page.getByText('아직 학습 기록이 없어요')).toBeVisible()
  await openPractice(page, /발음/, /ㄹ 발음/)
  await expect(page.getByText('라디오').first()).toBeVisible()
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText('낱말', { exact: true })).toBeVisible()
  await expect(page.getByText('미평가')).toHaveCount(3)
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible()
  const shown = Number((await page.locator('text=텍스트 일치율').first().locator('xpath=../..').innerText()).match(/(\d+)\s*%/)?.[1])

  const history = await api<{ content: { matchRate: number | null }[] }>('GET', '/api/v1/students/me/history?type=word', undefined, student.token)
  expect(history.content).toHaveLength(1)
  expect(Math.round(history.content[0].matchRate ?? -1)).toBe(shown)

  await page.goto('/student')
  await expect(page.getByText('최근 학습 기록')).toBeVisible()
  await expect(page.getByText(`텍스트 일치율 ${shown}%`)).toBeVisible()
})

test('E2E-08 분석 서버 오류 → 안내 → 다시 분석하기로 복구', async ({ page }) => {
  const student = await createStudent('error', '오류학생')
  await loginUi(page, student.email)
  let failed = false
  await page.route('**/api/v1/speech/analyze', async (route) => {
    if (!failed) { failed = true; await route.fulfill({ status: 502, contentType: 'application/json', body: JSON.stringify({ success: false, code: '502 BAD_GATEWAY', message: '로컬 음성 인식 실행에 실패했습니다.' }) }); return }
    await route.continue()
  })
  await openPractice(page, /발음/, /ㄹ 발음/)
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText('로컬 음성 인식 실행에 실패했습니다.')).toBeVisible()
  await expect(page.getByText('텍스트 일치율')).toHaveCount(0)
  await page.getByRole('button', { name: '다시 분석하기' }).click()
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  // 실패한 시도는 학습 기록이 되지 않는다(성공한 1건만).
  const history = await api<{ content: unknown[] }>('GET', '/api/v1/students/me/history?type=word', undefined, student.token)
  expect(history.content).toHaveLength(1)
})

test('E2E-09 학생 학습 기록 → 선생님 분석 화면 수치가 저장 데이터와 일치', async ({ page }) => {
  const student = await createStudent('report', '리포트학생')
  const teacher = await createTeacher('report-t', '리포트선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  await loginUi(page, student.email)
  await openPractice(page, /발음/, /ㄹ 발음/)
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible({ timeout: 90_000 })
  const today = new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 10)
  const analytics = await api<{ averageMatchRate: number | null; matchedAttempts: number }>('GET',
    `/api/v1/teachers/me/students/${student.studentId}/analytics?startDate=${today}&endDate=${today}`, undefined, teacher.token)
  const history = await api<{ content: { matchRate: number }[] }>('GET', '/api/v1/students/me/history?type=word', undefined, student.token)
  expect(analytics.matchedAttempts).toBe(history.content.length)
  expect(analytics.averageMatchRate).toBe(Math.round(history.content.reduce((sum, item) => sum + item.matchRate, 0) / history.content.length))

  await page.context().clearCookies()
  await page.evaluate(() => window.sessionStorage.clear())
  await loginUi(page, teacher.email, 'teacher')
  await page.goto(`/teacher/reports/${student.studentId}`)
  await page.getByRole('tab', { name: '종합 분석' }).click()
  await expect(page.getByText('평균 텍스트 일치율').first()).toBeVisible()
  await expect(page.locator('p', { hasText: '평균 텍스트 일치율' }).first().locator('xpath=..')).toContainText(`${analytics.averageMatchRate}`)
})

test('E2E-11 분석은 끝났지만 응답이 유실됨 → 다시 분석하기 → 같은 분석 재사용(중복 분석 없음)', async ({ page }) => {
  const student = await createStudent('lost', '유실학생')
  const teacher = await createTeacher('lost-t', '유실선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  await loginUi(page, student.email)
  const keys: string[] = []
  const responses: { analysisId: string; reused?: boolean }[] = []
  await page.route('**/api/v1/speech/analyze', async (route) => {
    keys.push(route.request().headers()['idempotency-key'] ?? '')
    const response = await route.fetch()
    responses.push(await response.json())
    if (responses.length === 1) { await route.abort('connectionreset'); return } // 서버는 처리했지만 화면은 응답을 못 받음
    await route.fulfill({ response })
  })
  await openPractice(page, /발음/, /ㄹ 발음/)
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText(/서버에 연결할 수 없어요/)).toBeVisible({ timeout: 90_000 })
  await page.getByRole('button', { name: '다시 분석하기' }).click()
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible({ timeout: 90_000 })

  expect(keys).toHaveLength(2)
  expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
  expect(keys[1]).toBe(keys[0])
  expect(responses[1].analysisId).toBe(responses[0].analysisId)
  expect(responses[1].reused).toBe(true)
  const analyses = await api<{ content: unknown[] }>('GET', `/api/v1/teachers/me/students/${student.studentId}/speech-analyses`, undefined, teacher.token)
  expect(analyses.content).toHaveLength(1)
  const history = await api<{ content: unknown[] }>('GET', '/api/v1/students/me/history?type=word', undefined, student.token)
  expect(history.content).toHaveLength(1)
})

test('E2E-23 실제 서버: 검수된 교육 자료가 없는 결과는 AI를 부르지 않고 근거 자료 부족으로 안내', async ({ page }) => {
  const student = await createStudent('ai-feedback-real', 'AI근거학생')
  await loginUi(page, student.email)
  let posts = 0
  page.on('request', (request) => { if (request.method() === 'POST' && request.url().includes('/feedback')) posts++ })
  await openPractice(page, /발음/, /ㄹ 발음/)
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  // '라디오'에는 표준 발음법 조항이 적용될 받침이 없고, 기본 자료는 검수 전(DRAFT)이다.
  await expect(page.getByText(/근거 자료 부족/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'AI 설명 보기' })).toHaveCount(0)
  expect(posts).toBe(0)
})

test('E2E-24 AI 설명 화면 흐름: 요청할 때만 생성, AI 오류 후 다시 시도, 출처·점수 아님 표시, 측정값 분리', async ({ page }) => {
  const student = await createStudent('ai-feedback', 'AI설명학생')
  await loginUi(page, student.email)
  // 검수된 자료가 있는 상황의 화면 흐름: 피드백 API 응답만 대신한다(검색·근거 검증은 백엔드 통합 테스트, 실제 Gemini는 LiveAiIntegrationTest).
  let posts = 0
  await page.route('**/api/v1/speech/analyses/*/feedback', async (route) => {
    const analysisId = route.request().url().split('/analyses/')[1].split('/')[0]
    if (route.request().method() === 'GET')
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ analysisId, status: 'NOT_GENERATED', source: 'AI', available: true, consentRequired: false, basedOn: ['AUTO_ANALYSIS'] }) })
    posts++
    if (posts === 1) return route.fulfill({ status: 504, contentType: 'application/json', body: JSON.stringify({ success: false, code: 'AI_TIMEOUT', message: 'AI 서비스 응답 시간이 초과되었어요. 잠시 뒤 다시 시도해 주세요.' }) })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ analysisId, status: 'READY', source: 'AI', text: '끝까지 또박또박 말했어요! 받침 소리를 천천히 말해 봐요 [S1].', basedOn: ['AUTO_ANALYSIS'],
      sources: [{ marker: 'S1', cited: true, chunkId: 'std-pron-08', title: '표준 발음법(표준어 규정 제2부)', location: '제4장 제8항', url: 'https://korean.go.kr/kornorms/' }] }) })
  })
  await openPractice(page, /발음/, /ㄹ 발음/)
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  const panel = page.getByLabel('AI 설명')
  await expect(panel.getByRole('button', { name: 'AI 설명 보기' })).toBeVisible()
  expect(posts).toBe(0) // 결과를 보여 줄 때 AI를 자동으로 부르지 않는다

  await panel.getByRole('button', { name: 'AI 설명 보기' }).click()
  await expect(panel.getByText(/응답 시간이 초과/)).toBeVisible()
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible() // AI 오류여도 분석 결과는 그대로
  await panel.getByRole('button', { name: '다시 시도' }).click()
  await expect(panel.getByText('끝까지 또박또박 말했어요! 받침 소리를 천천히 말해 봐요 [S1].')).toBeVisible()
  await expect(panel.getByText('점수 아님')).toBeVisible()
  await expect(panel.getByText(/제4장 제8항/)).toBeVisible()
  await expect(panel.getByText(/발음 정확도는 평가하지 않았어요/)).toBeVisible()
  await expect(page.getByText('미평가')).toHaveCount(3) // 측정값(발음·속도·유창성)은 미평가 그대로
})
