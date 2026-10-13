import { expect, test, type Page } from '@playwright/test'
import { api, createStudent, createTeacher, layoutProblems, loginUi, mic, openPractice, PASSWORD, POLICY, recordAndAnalyze, uniqueEmail } from './helpers'

test.use({ launchOptions: { args: mic('mic-radio.wav') }, permissions: ['microphone'] })

async function createGuardian(scenario: string, name: string) {
  const email = uniqueEmail(scenario)
  await api('POST', '/api/v1/auth/signup', { role: 'GUARDIAN', name, email, password: PASSWORD, centerId: 1, termsAgreed: true, consents: { privacy: true, policyVersion: POLICY } })
  const login = await api<{ accessToken: string }>('POST', '/api/v1/auth/login', { email, password: PASSWORD })
  return { email, token: login.accessToken }
}

async function loginGuardian(page: Page, email: string) {
  await page.goto('/login')
  await page.getByRole('tab', { name: /보호자/ }).click()
  await page.locator('#login-email').fill(email)
  await page.locator('#login-password').fill(PASSWORD)
  await page.locator('form').getByRole('button', { name: '로그인' }).click()
  await page.waitForURL(/\/guardian/)
}

test('E2E-26 추천 콘텐츠로 연습 이동 → 실제 녹음 기록 → 기간별 학습 현황, 선생님 누적 학습과 같은 숫자', async ({ page, browser }) => {
  const student = await createStudent('report', '리포트학생')
  const teacher = await createTeacher('report-t', '리포트선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  await loginUi(page, student.email)
  // 기록이 없으면 기본 추천(맞춤 추천이라고 하지 않음)
  const recommend = page.getByRole('region', { name: /이렇게 시작해 보세요|추천 연습/ })
  await expect(recommend.getByText('기본 추천')).toBeVisible()
  await expect(recommend.getByText('내 기록 기반')).toHaveCount(0)
  // 추천 콘텐츠를 누르면 기존 연습 흐름(선택 연습)으로 바로 간다
  const first = recommend.getByRole('button', { name: /연습하기,/ }).first()
  const title = (await first.getAttribute('aria-label'))!.split(' 연습하기,')[0]
  await first.click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await expect(page.getByText(/^선택한 .+ 연습 1 \/ 1$/)).toBeVisible()

  // 실제 녹음·분석·저장 1건
  await openPractice(page, /발음/, /ㄹ 발음/)
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible({ timeout: 90_000 })
  const report = await api<{ totalAttempts: number; practiceDays: number }>('GET', '/api/v1/students/me/learning-status?period=7d', undefined, student.token)
  expect(report.totalAttempts).toBe(1)

  await page.goto('/student/mypage')
  await page.getByRole('button', { name: /학습 상태/ }).click()
  await expect(page.getByRole('tab', { name: '최근 7일' })).toHaveAttribute('aria-selected', 'true')
  const coverage = page.getByText(/전체 연습 콘텐츠 [\d,]+개 중 1개를 연습했어요/)
  await expect(coverage).toBeVisible()
  await expect(page.getByText('이전 7일에 저장된 기록이 없어 비교하지 않아요.')).toBeVisible()
  await page.getByRole('tab', { name: '전체 기간' }).click()
  await expect(page.getByText(/처음 연습한 날\(\d{4}-\d{2}-\d{2}\)부터/)).toBeVisible()
  const studentCoverage = await page.getByText(/전체 연습 콘텐츠 [\d,]+개 중/).innerText()

  // 기록이 생기면 추천이 기록 기반으로 바뀐다
  await page.goto('/student')
  await expect(page.getByRole('region', { name: '추천 연습' }).getByText('내 기록 기반')).toBeVisible()

  // 선생님: 같은 학생의 누적 학습 = 학생 화면과 같은 숫자
  const teacherContext = await browser.newContext()
  const teacherPage = await teacherContext.newPage()
  await loginUi(teacherPage, teacher.email, 'teacher')
  await teacherPage.goto('/teacher/analytics')
  await teacherPage.getByRole('tab', { name: '누적 학습' }).click()
  await teacherPage.getByRole('tab', { name: '전체 기간' }).click()
  await expect(teacherPage.getByText(studentCoverage)).toBeVisible()
  const teacherReport = await api<{ totalAttempts: number }>('GET', `/api/v1/teachers/me/students/${student.studentId}/learning-report?period=7d`, undefined, teacher.token)
  expect(teacherReport.totalAttempts).toBe(report.totalAttempts)
  await teacherContext.close()
})

test('E2E-27 보호자: 자녀 코드로 연결 → 리포트 보기 → 다른 학생 차단 → 자녀가 해제하면 바로 차단', async ({ page, browser }) => {
  const child = await createStudent('g-child', '연결자녀')
  const other = await createStudent('g-other', '다른학생')
  const guardian = await createGuardian('guardian', '김보호')

  // 자녀 계정: 마이페이지 › 보호자 연결 › 연결 코드 만들기
  await loginUi(page, child.email)
  await page.goto('/student/mypage')
  await page.getByRole('button', { name: /보호자 연결/ }).click()
  await expect(page).toHaveURL(/\/student\/mypage\/guardians$/)
  await page.getByRole('button', { name: '연결 코드 만들기' }).click()
  const code = (await page.getByText(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/).innerText()).trim()

  // 보호자 계정: 코드로 연결
  const guardianContext = await browser.newContext()
  const g = await guardianContext.newPage()
  await loginGuardian(g, guardian.email)
  await expect(g.getByText('아직 연결된 자녀가 없어요')).toBeVisible()
  await g.getByLabel('연결 코드').fill(code.toLowerCase())
  await g.getByRole('button', { name: '자녀 연결' }).click()
  await expect(g.getByText('연결자녀 학생과 연결했어요.')).toBeVisible()
  await g.getByRole('button', { name: '연결자녀 학습 리포트 보기' }).click()
  await expect(g).toHaveURL(new RegExp(`/guardian/children/${child.studentId}$`))
  await expect(g.getByRole('region', { name: '자녀 계정의 동의 상태' })).toBeVisible()
  await expect(g.getByRole('tab', { name: '최근 30일' })).toBeVisible()
  // 같은 코드는 다시 쓸 수 없다
  const reuse = await fetch('http://localhost:8080/api/v1/guardians/me/children', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${guardian.token}` }, body: JSON.stringify({ code }) })
  expect(reuse.status).toBe(400)

  // 주소의 학생 ID를 바꿔도 연결되지 않은 학생은 보이지 않는다(서버가 막음)
  await g.goto(`/guardian/children/${other.studentId}`)
  await expect(g.getByText('연결된 자녀 정보를 찾을 수 없습니다.')).toBeVisible()
  expect((await fetch(`http://localhost:8080/api/v1/guardians/me/children/${other.studentId}/learning-report`, { headers: { Authorization: `Bearer ${guardian.token}` } })).status).toBe(404)
  // 역할 분리: 보호자는 학생·선생님 화면/API를 쓸 수 없다
  await g.goto('/student')
  await g.waitForURL(/\/guardian$/)
  expect((await fetch('http://localhost:8080/api/v1/students/me', { headers: { Authorization: `Bearer ${guardian.token}` } })).status).toBe(403)

  // 자녀가 연결을 해제하면 보호자는 바로 볼 수 없다
  await page.reload()
  await page.getByRole('button', { name: '김보호 연결 해제' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '연결 해제' }).click()
  await expect(page.getByText('아직 연결된 보호자가 없어요.')).toBeVisible()
  await g.goto(`/guardian/children/${child.studentId}`)
  await expect(g.getByText('연결된 자녀 정보를 찾을 수 없습니다.')).toBeVisible()
  await g.goto('/guardian')
  await expect(g.getByText('아직 연결된 자녀가 없어요')).toBeVisible()
  await guardianContext.close()
})

test('E2E-28 AI 대화: 후속 질문 → 대화 낱말과 연결된 실제 콘텐츠로 연습 이동 → 끝내기 요약, 실패·동의 안내', async ({ page }) => {
  // 외부 AI는 부르지 않는다: 대화 API만 가로채고, 추천 콘텐츠는 테스트 DB의 실제 활성 콘텐츠를 쓴다(서버 쪽 규칙은 백엔드 통합 테스트에서 검증).
  const student = await createStudent('chat', '대화학생', { consents: { privacy: true, voice: true, aiChat: true, policyVersion: POLICY, guardianConfirmed: true, guardianName: '보호자', guardianRelation: '부모' } })
  const found = await api<{ content: { exerciseId: number; title: string; categoryId: string }[] }>('GET', '/api/v1/practice-contents?keyword=%EC%82%AC%EA%B3%BC&size=1', undefined, student.token)
  const real = found.content[0]
  let ended = 0
  await page.route('**/api/v1/ai/conversations', (route) => route.request().method() === 'POST'
    ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ conversationId: 'e2e-conv', topic: '좋아하는 음식' }) }) : route.continue())
  await page.route('**/api/v1/ai/conversations/e2e-conv/messages', (route) => route.fulfill({ status: 200, contentType: 'application/json',
    body: JSON.stringify({ text: '사과를 좋아해요', response: '사과 맛있지! 사과는 어떤 맛이 나서 좋아?' }) }))
  await page.route('**/api/v1/ai/conversations/e2e-conv/contents', (route) => route.fulfill({ status: 200, contentType: 'application/json',
    body: JSON.stringify({ items: [{ ...real, categoryName: '어휘력', color: '#5B8BC8', keyword: '사과', reason: "대화에서 나온 '사과' 낱말이 들어 있는 연습이에요" }] }) }))
  await page.route('**/api/v1/ai/conversations/e2e-conv/end', (route) => {
    ended += 1
    return ended === 1
      ? route.fulfill({ status: 504, contentType: 'application/json', body: JSON.stringify({ success: false, code: 'AI_TIMEOUT', message: 'AI 서비스 응답 시간이 초과되었어요.' }) })
      : route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 'CLOSED', summary: '오늘은 좋아하는 사과 이야기를 나눴어요.', studentTurns: 1,
          relatedContents: [{ ...real, categoryName: '어휘력', color: '#5B8BC8', keyword: '사과', reason: "대화에서 나온 '사과' 낱말이 들어 있는 연습이에요" }],
          nextActivities: [{ type: 'PRACTICE', label: '대화와 관련된 연습 해 보기' }] }) })
  })

  await loginUi(page, student.email)
  await page.goto('/student/practice')
  await page.getByRole('button', { name: /AI 대화하기/ }).click()
  await page.getByRole('button', { name: /좋아하는 음식/ }).click()
  await page.getByLabel('대화 메시지').fill('사과를 좋아해요')
  await page.getByRole('button', { name: '보내기' }).click()
  await expect(page.getByText('사과 맛있지! 사과는 어떤 맛이 나서 좋아?')).toBeVisible()
  await expect(page.getByRole('region', { name: '대화와 관련된 연습' }).getByText(real.title)).toBeVisible()

  // 요약 실패: 대화는 그대로, 다시 시도
  await page.getByRole('button', { name: '대화 끝내고 요약 보기' }).click()
  await expect(page.getByText('AI 서비스 응답 시간이 초과되었어요.')).toBeVisible()
  await expect(page.getByText('사과를 좋아해요')).toBeVisible()
  await page.getByRole('button', { name: '다시 시도: 대화 끝내고 요약 보기' }).click()
  await expect(page.getByText('오늘은 좋아하는 사과 이야기를 나눴어요.')).toBeVisible()
  await expect(page.getByText(/발음은 평가하지 않아요/)).toBeVisible()
  // 관련 연습 → 기존 연습 흐름
  await page.getByRole('button', { name: new RegExp(`${real.title} 연습하기`) }).click()
  await expect(page.getByRole('heading', { name: real.title })).toBeVisible()
  await expect(page.getByText(/^선택한 .+ 연습 1 \/ 1$/)).toBeVisible()

  // 동의가 없으면(철회) 동의 관리 화면으로 안내
  await page.unroute('**/api/v1/ai/conversations')
  await api('PUT', '/api/v1/consents/me/AI_CHAT', { agreed: false, policyVersion: POLICY }, student.token)
  await page.goto('/student/practice')
  await page.getByRole('button', { name: /AI 대화하기/ }).click()
  await page.getByRole('button', { name: /좋아하는 음식/ }).click()
  await page.getByRole('button', { name: '동의 관리로 가기' }).click()
  await expect(page).toHaveURL(/\/student\/mypage\/consents$/)
})

test.describe('새 화면 반응형', () => {
  test('E2E-29 학습 상태·보호자 연결·보호자 화면·선생님 누적 학습: 320/820/1280px 가로 넘침 없음', async ({ page, browser }) => {
    const child = await createStudent('resp-child', '아주아주긴이름을가진학생입니다정말로긴이름')
    const teacher = await createTeacher('resp-t', '반응형선생님')
    await api('POST', '/api/v1/teachers/me/students', { studentId: child.studentId, name: child.name }, teacher.token)
    const guardian = await createGuardian('resp-g', '반응형보호자')
    const { code } = await api<{ code: string }>('POST', '/api/v1/students/me/guardian-link-codes', undefined, child.token)
    await api('POST', '/api/v1/guardians/me/children', { code }, guardian.token)
    await loginUi(page, child.email)
    const g = await (await browser.newContext()).newPage()
    await loginGuardian(g, guardian.email)
    const t = await (await browser.newContext()).newPage()
    await loginUi(t, teacher.email, 'teacher')
    for (const width of [320, 820, 1280]) {
      for (const [p, path, label, open] of [
        [page, '/student/mypage', 'learning-status', async () => { await page.getByRole('button', { name: /학습 상태/ }).click(); await page.getByRole('tab', { name: '최근 7일' }).waitFor() }],
        [page, '/student/mypage/guardians', 'student-guardians', async () => { await page.getByRole('heading', { name: '연결된 보호자' }).waitFor() }],
        [page, '/student', 'student-home', async () => { await page.getByRole('region', { name: /이렇게 시작해 보세요|추천 연습/ }).getByRole('button').first().waitFor() }],
        [g, '/guardian', 'guardian-home', async () => { await g.getByRole('list', { name: '연결된 자녀 목록' }).waitFor() }],
        [g, `/guardian/children/${child.studentId}`, 'guardian-child', async () => { await g.getByRole('tab', { name: '최근 30일' }).waitFor() }],
        [t, '/teacher/analytics', 'teacher-report', async () => { await t.getByRole('tab', { name: '누적 학습' }).click(); await t.getByRole('tab', { name: '최근 30일' }).waitFor() }],
      ] as const) {
        await p.setViewportSize({ width, height: 900 })
        await p.goto(path)
        await open()
        await p.waitForLoadState('networkidle')
        expect(await layoutProblems(p), `${label} ${width}px`).toEqual([])
      }
    }
  })
})
