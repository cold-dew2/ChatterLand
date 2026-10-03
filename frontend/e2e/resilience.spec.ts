import { expect, test } from '@playwright/test'
import { api, createStudent, createTeacher, layoutProblems, loginUi, PASSWORD } from './helpers'

test('E2E-13 인증 만료 → 로그인(만료 안내) → 원래 보던 화면으로 복귀', async ({ page }) => {
  const student = await createStudent('expire', '만료학생')
  await loginUi(page, student.email)
  await page.goto('/student/history')
  await expect(page.getByRole('navigation', { name: '학생 메뉴' })).toBeVisible()
  // 저장된 토큰을 서버가 거부하는 값으로 바꿔 만료를 재현한다(refresh도 실패).
  await page.evaluate(() => {
    window.sessionStorage.setItem('chatterland.accessToken', 'expired-token')
    window.sessionStorage.setItem('chatterland.refreshToken', 'revoked-refresh-token')
  })
  await page.reload()
  await page.waitForURL(/\/login\?expired=1&next=%2Fstudent%2Fhistory/)
  await expect(page.getByText('로그인 시간이 만료되었어요. 다시 로그인해 주세요.')).toBeVisible()
  await page.locator('#login-email').fill(student.email)
  await page.locator('#login-password').fill(PASSWORD)
  await page.locator('form').getByRole('button', { name: '로그인' }).click()
  await page.waitForURL((url) => url.pathname === '/student/history')
})

test('E2E-14 외부 주소 복귀 경로는 무시하고, 로그인한 사용자가 로그인 화면에 오면 홈으로 보낸다', async ({ page }) => {
  const student = await createStudent('nextsafe', '안전학생')
  await page.goto('/login?expired=1&next=' + encodeURIComponent('https://evil.example/steal'))
  await page.locator('#login-email').fill(student.email)
  await page.locator('#login-password').fill(PASSWORD)
  await page.locator('form').getByRole('button', { name: '로그인' }).click()
  await page.waitForURL((url) => url.origin === 'http://localhost:3000' && url.pathname === '/student')
  await page.goto('/login')
  await page.waitForURL((url) => url.pathname === '/student')
})

test('E2E-15 로그아웃 후 뒤로 가기·새로고침·직접 주소로 보호 화면에 들어갈 수 없다', async ({ page }) => {
  const student = await createStudent('back', '뒤로학생')
  await loginUi(page, student.email)
  await page.goto('/student/history')
  await page.goto('/student/mypage')
  await page.getByRole('button', { name: '로그아웃' }).click()
  await page.waitForURL((url) => url.pathname === '/')
  await page.goBack()
  await page.waitForURL((url) => !url.pathname.startsWith('/student'))
  await expect(page.getByRole('navigation', { name: '학생 메뉴' })).toHaveCount(0)
  await page.goto('/student/history')
  await page.reload()
  await page.waitForURL((url) => !url.pathname.startsWith('/student'))
  const refresh = await fetch('http://localhost:8080/api/v1/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: 'any' }) })
  expect(refresh.status).toBe(401)
})

test.describe('작은 화면(320px)과 긴 텍스트', () => {
  test.use({ viewport: { width: 320, height: 640 } })

  test('E2E-16 긴 이름·이메일·숙제 제목·설명이 화면 밖으로 넘치지 않는다', async ({ page }) => {
    const longName = '가나다라마바사아자차카타파하'.repeat(5).slice(0, 80)
    const student = await createStudent('long-' + 'x'.repeat(28), longName) // 이메일 로컬 부분 64자 한도 안에서 최대한 길게
    const teacher = await createTeacher('long-t', '긴숙제선생님')
    await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: longName }, teacher.token)
    const due = new Date(Date.now() + 9 * 3600_000 + 3 * 86400_000).toISOString().slice(0, 10)
    await api('POST', '/api/v1/teachers/me/homeworks', { studentId: student.studentId, title: 'ㄹ받침연습'.repeat(32), type: '조음말하기연습유형이름이매우김',
      dueDate: due, targetMinutes: 120, description: '설명이아주길어요'.repeat(250) }, teacher.token)
    await loginUi(page, student.email)
    for (const [path, label] of [['/student', 'student-home'], ['/student/mypage', 'student-mypage']] as const) {
      await page.goto(path); await page.waitForLoadState('networkidle')
      expect(await layoutProblems(page), label).toEqual([])
    }
    await page.goto('/student')
    await page.getByRole('region', { name: '오늘의 숙제' }).getByRole('button', { name: '전체 보기' }).click()
    await expect(page.getByText('숙제하기')).toBeVisible()
    expect(await layoutProblems(page), 'student-homework').toEqual([])
    await expect(page.getByRole('button', { name: '완료', exact: true })).toBeInViewport({ ratio: 0.5 }).catch(async () => {
      await page.getByRole('button', { name: '완료', exact: true }).scrollIntoViewIfNeeded()
      await expect(page.getByRole('button', { name: '완료', exact: true })).toBeInViewport()
    })

    await page.evaluate(() => window.sessionStorage.clear())
    await loginUi(page, teacher.email, 'teacher')
    for (const [path, label] of [['/teacher/students', 'teacher-students'], [`/teacher/students/${student.studentId}`, 'teacher-detail'], ['/teacher/homeworks', 'teacher-homeworks']] as const) {
      await page.goto(path); await page.waitForLoadState('networkidle')
      expect(await layoutProblems(page), label).toEqual([])
    }
  })
})
