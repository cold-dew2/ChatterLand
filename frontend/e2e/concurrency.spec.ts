import { expect, test } from '@playwright/test'
import { api, createStudent, createTeacher, loginUi, mailCode, PASSWORD } from './helpers'

type HomeworkRow = { homeworkId: number; title: string; version: number }
const teacherHomeworks = async (token: string) =>
  (await api<{ content: HomeworkRow[] }>('GET', '/api/v1/teachers/me/homeworks?page=0&size=100', undefined, token)).content

test('E2E-17 숙제 등록 응답이 유실된 뒤 다시 눌러도 숙제는 하나만 생긴다(같은 Idempotency-Key)', async ({ page }) => {
  const student = await createStudent('hw-idem', '중복학생')
  const teacher = await createTeacher('hw-idem-t', '중복선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  await loginUi(page, teacher.email, 'teacher')
  await page.goto(`/teacher/students/${student.studentId}`)

  const keys: string[] = []
  const statuses: number[] = []
  page.on('response', (response) => {
    if (response.request().method() === 'POST' && response.url().endsWith('/api/v1/teachers/me/homeworks')) statuses.push(response.status())
  })
  // 첫 등록 요청은 서버까지 전달·저장되지만, 응답이 화면에 도착하기 전에 연결이 끊긴 것처럼 만든다.
  await page.route('**/api/v1/teachers/me/homeworks', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    keys.push(route.request().headers()['idempotency-key'] ?? '')
    if (keys.length === 1) { await route.fetch(); return route.abort('failed') }
    return route.continue()
  })
  await page.getByRole('button', { name: '숙제 배정' }).click()
  const dialog = page.getByRole('dialog', { name: '숙제 등록' })
  await dialog.getByLabel(/숙제 내용/).fill('응답 유실 숙제')
  await dialog.getByRole('button', { name: '숙제 등록' }).click()
  await expect(dialog.getByText(/서버에 연결할 수 없어요/)).toBeVisible()
  expect(await teacherHomeworks(teacher.token)).toHaveLength(1) // 서버에는 이미 저장되어 있다

  await dialog.getByRole('button', { name: '숙제 등록' }).click()
  await expect(dialog).toHaveCount(0)
  expect(keys).toHaveLength(2)
  expect(keys[0]).toMatch(/^[A-Za-z0-9_-]{8,64}$/)
  expect(keys[1]).toBe(keys[0])
  expect(statuses).toEqual([200]) // 첫 응답은 유실, 재전송은 새로 만들지 않았으므로 200(처음 숙제)
  const saved = await teacherHomeworks(teacher.token)
  expect(saved).toHaveLength(1)
  expect(saved[0].title).toBe('응답 유실 숙제')
})

test('E2E-18 두 탭에서 같은 숙제를 고치면 늦게 저장한 탭은 덮어쓰지 않고 최신 내용을 다시 보여준다', async ({ browser }) => {
  const student = await createStudent('hw-ver', '버전학생')
  const teacher = await createTeacher('hw-ver-t', '버전선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  const due = new Date(Date.now() + 9 * 3600_000 + 3 * 86400_000).toISOString().slice(0, 10)
  await api('POST', '/api/v1/teachers/me/homeworks', { studentId: student.studentId, title: '처음 제목', type: '말하기 연습', dueDate: due, targetMinutes: 10 }, teacher.token)

  const context = await browser.newContext()
  const tabA = await context.newPage()
  const tabB = await context.newPage()
  for (const tab of [tabA, tabB]) {
    await loginUi(tab, teacher.email, 'teacher')
    await tab.goto('/teacher/homeworks')
    await expect(tab.getByRole('button', { name: '처음 제목 숙제 수정' })).toBeVisible()
  }

  await tabA.getByRole('button', { name: '처음 제목 숙제 수정' }).click()
  const dialogA = tabA.getByRole('dialog', { name: '숙제 수정' })
  await dialogA.getByLabel(/숙제 내용/).fill('A 탭 제목')
  await dialogA.getByRole('button', { name: '수정 저장' }).click()
  await expect(dialogA).toHaveCount(0)

  // B 탭은 A가 저장하기 전의 목록(version 0)을 보고 있다.
  await tabB.getByRole('button', { name: '처음 제목 숙제 수정' }).click()
  const dialogB = tabB.getByRole('dialog', { name: '숙제 수정' })
  await dialogB.getByLabel(/숙제 내용/).fill('B 탭 제목')
  await dialogB.getByRole('button', { name: '수정 저장' }).click()
  await expect(dialogB.getByText(/다른 곳에서 먼저 바뀐 숙제예요/)).toBeVisible()
  await expect(dialogB.getByLabel(/숙제 내용/)).toHaveValue('A 탭 제목')
  expect((await teacherHomeworks(teacher.token))[0].title).toBe('A 탭 제목')

  // 최신 내용을 확인한 뒤 다시 저장하면 반영된다.
  await dialogB.getByLabel(/숙제 내용/).fill('B 탭 제목')
  await dialogB.getByRole('button', { name: '수정 저장' }).click()
  await expect(dialogB).toHaveCount(0)
  const [final] = await teacherHomeworks(teacher.token)
  expect(final).toMatchObject({ title: 'B 탭 제목', version: 2 })
  await context.close()
})

test('E2E-19 비밀번호를 재설정하면 다른 기기의 로그인도 바로 끊기고 로그인 화면으로 안내된다', async ({ page }) => {
  const teacher = await createTeacher('pw-revoke', '세션선생님')
  await loginUi(page, teacher.email, 'teacher')
  await page.goto('/teacher/students')
  await expect(page.getByText('등록된 제자가 없어요')).toBeVisible()

  // 다른 기기에서 비밀번호 재설정(실제 메일 수신)
  await api('POST', '/api/v1/auth/password-reset/request', { email: teacher.email })
  const { resetToken } = await api<{ resetToken: string }>('POST', '/api/v1/auth/password-reset/verify', { email: teacher.email, code: await mailCode(teacher.email) })
  await api('POST', '/api/v1/auth/password-reset/confirm', { resetToken, newPassword: 'NewPassword!9', newPasswordConfirm: 'NewPassword!9' })

  // 이 기기의 access token은 아직 만료 전이지만 서버가 거절하고, refresh token도 폐기되어 다시 로그인해야 한다.
  const stale = await page.evaluate(() => window.sessionStorage.getItem('chatterland.accessToken'))
  expect((await fetch('http://localhost:8080/api/v1/auth/me', { headers: { Authorization: `Bearer ${stale}` } })).status).toBe(401)
  await page.reload()
  await page.waitForURL(/\/login\?expired=1&next=%2Fteacher%2Fstudents/)
  await expect(page.getByText('로그인 시간이 만료되었어요. 다시 로그인해 주세요.')).toBeVisible()
  await page.getByRole('tab', { name: /선생님/ }).click()
  await page.locator('#login-email').fill(teacher.email)
  await page.locator('#login-password').fill(PASSWORD)
  await page.locator('form').getByRole('button', { name: '로그인' }).click()
  await expect(page.getByText('이메일 또는 비밀번호를 확인해 주세요.')).toBeVisible()
  await page.locator('#login-password').fill('NewPassword!9')
  await page.locator('form').getByRole('button', { name: '로그인' }).click()
  await page.waitForURL((url) => url.pathname === '/teacher/students')
})

test('E2E-20 로그아웃하면 이 기기의 access 토큰은 만료 전이어도 서버가 거절한다', async ({ page }) => {
  const student = await createStudent('logout-access', '로그아웃학생')
  await loginUi(page, student.email)
  const access = await page.evaluate(() => window.sessionStorage.getItem('chatterland.accessToken'))
  expect((await fetch('http://localhost:8080/api/v1/auth/me', { headers: { Authorization: `Bearer ${access}` } })).status).toBe(200)
  await page.goto('/student/mypage')
  await page.getByRole('button', { name: '로그아웃' }).click()
  await page.waitForURL((url) => url.pathname === '/')
  expect(await page.evaluate(() => window.sessionStorage.getItem('chatterland.accessToken'))).toBeNull()
  // 화면에서 지운 것만이 아니라 서버도 이 토큰을 더 받지 않는다.
  expect((await fetch('http://localhost:8080/api/v1/auth/me', { headers: { Authorization: `Bearer ${access}` } })).status).toBe(401)
  // 다른 기기(별도 로그인)는 영향이 없다.
  expect((await fetch('http://localhost:8080/api/v1/auth/me', { headers: { Authorization: `Bearer ${student.token}` } })).status).toBe(200)
})

test('E2E-21 마이페이지에서 비밀번호를 바꾸면 이 기기는 로그인을 유지하고 다른 기기는 끊긴다', async ({ page }) => {
  const student = await createStudent('pw-change', '비번변경학생')
  await loginUi(page, student.email)
  await page.goto('/student/mypage')
  const form = page.locator('form').filter({ has: page.getByRole('button', { name: '비밀번호 변경' }) })
  await form.getByLabel(/현재 비밀번호/).fill('Wrong-Pass!000')
  await form.getByLabel(/^새 비밀번호(\s*\*)?$/).fill('Changed-Pass!789')
  await form.getByLabel(/새 비밀번호 확인/).fill('Changed-Pass!789')
  await form.getByRole('button', { name: '비밀번호 변경' }).click()
  await expect(form.getByText('현재 비밀번호가 올바르지 않아요. (남은 시도 4회)')).toBeVisible()
  await expect(page).toHaveURL(/\/student\/mypage/) // 틀려도 로그아웃되지 않는다

  await form.getByLabel(/현재 비밀번호/).fill(PASSWORD)
  await form.getByRole('button', { name: '비밀번호 변경' }).click()
  await expect(form.getByText(/비밀번호를 변경했어요/)).toBeVisible()
  // 이 기기: 새로고침해도 로그인 유지
  await page.reload()
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()
  // 다른 기기(테스트 시작 때 받은 토큰): 거절
  expect((await fetch('http://localhost:8080/api/v1/auth/me', { headers: { Authorization: `Bearer ${student.token}` } })).status).toBe(401)
  expect((await fetch('http://localhost:8080/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: student.email, password: PASSWORD }) })).status).toBe(401)
  expect((await fetch('http://localhost:8080/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: student.email, password: 'Changed-Pass!789' }) })).status).toBe(200)
})

test('E2E-22 다른 탭에서 고친 숙제는 오래된 화면에서 삭제되지 않는다', async ({ browser }) => {
  const student = await createStudent('hw-del', '삭제학생')
  const teacher = await createTeacher('hw-del-t', '삭제선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  const due = new Date(Date.now() + 9 * 3600_000 + 3 * 86400_000).toISOString().slice(0, 10)
  const created = await api<{ homeworkId: number }>('POST', '/api/v1/teachers/me/homeworks', { studentId: student.studentId, title: '지울 숙제', type: '말하기 연습', dueDate: due, targetMinutes: 10 }, teacher.token)

  const context = await browser.newContext()
  const page = await context.newPage()
  await loginUi(page, teacher.email, 'teacher')
  await page.goto('/teacher/homeworks')
  await expect(page.getByRole('button', { name: '지울 숙제 숙제 삭제' })).toBeVisible()
  // 화면을 연 뒤 다른 곳(다른 탭)에서 숙제를 고친다.
  await api('PATCH', `/api/v1/teachers/me/homeworks/${created.homeworkId}`, { targetMinutes: 30, version: 0 }, teacher.token)

  await page.getByRole('button', { name: '지울 숙제 숙제 삭제' }).click()
  await page.getByRole('button', { name: '삭제', exact: true }).click()
  await expect(page.getByText(/다른 곳에서 먼저 바뀐 숙제라 삭제하지 않았어요/)).toBeVisible()
  expect(await teacherHomeworks(teacher.token)).toHaveLength(1)
  // 최신 목록을 본 뒤 다시 삭제하면 지워진다.
  await page.getByRole('button', { name: '지울 숙제 숙제 삭제' }).click()
  await page.getByRole('button', { name: '삭제', exact: true }).click()
  await expect(page.getByText('숙제를 삭제했어요.')).toBeVisible()
  expect(await teacherHomeworks(teacher.token)).toHaveLength(0)
  await context.close()
})
