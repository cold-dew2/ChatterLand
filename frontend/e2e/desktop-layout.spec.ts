import { expect, test, type Page } from '@playwright/test'
import { api, createStudent, createTeacher, layoutProblems, loginUi } from './helpers'

test.use({ viewport: { width: 1280, height: 720 } })

/** 열린 모달이 화면 안에 다 들어오고, 마지막 버튼까지 보이거나 스크롤로 닿을 수 있는지 확인한다. */
async function modalFits(page: Page, name: string | RegExp) {
  const dialog = page.getByRole('dialog', { name })
  await expect(dialog).toBeVisible()
  const box = await dialog.boundingBox()
  const viewport = page.viewportSize()!
  expect(box!.y).toBeGreaterThanOrEqual(-1)
  expect(box!.y + box!.height).toBeLessThanOrEqual(viewport.height + 1)
  const lastButton = dialog.getByRole('button').last()
  await lastButton.scrollIntoViewIfNeeded()
  await expect(lastButton).toBeInViewport()
}

async function check(page: Page, label: string) {
  await page.waitForLoadState('networkidle')
  const problems = await layoutProblems(page)
  await page.screenshot({ path: `test-results/desktop/${label}.png`, fullPage: true })
  expect(problems, `${label}: ${problems.join(' | ')}`).toEqual([])
}

test('DESK-01 학생 주요 화면(1280px): 가로 넘침 없음, 하단 메뉴가 콘텐츠를 가리지 않음', async ({ page }) => {
  const student = await createStudent('desk-s', '데스크학생')
  await page.goto('/login'); await check(page, 'login')
  await page.goto('/signup'); await check(page, 'signup')
  await loginUi(page, student.email)
  await check(page, 'student-home')
  // 하단 메뉴는 콘텐츠 열과 같은 폭이고, 스크롤 끝에서 마지막 콘텐츠를 가리지 않는다.
  const nav = await page.getByRole('navigation', { name: '학생 메뉴' }).boundingBox()
  const main = await page.locator('main').boundingBox()
  expect(Math.round(nav!.width)).toBe(Math.round(main!.width))
  expect(Math.abs(nav!.x - main!.x)).toBeLessThanOrEqual(1)
  await page.mouse.wheel(0, 5000)
  const lastContent = page.locator('main section').last()
  const lastBox = await lastContent.boundingBox()
  const navAfter = await page.getByRole('navigation', { name: '학생 메뉴' }).boundingBox()
  expect(lastBox!.y + lastBox!.height).toBeLessThanOrEqual(navAfter!.y + 1)
  for (const [path, label] of [['/student/practice', 'student-practice'], ['/student/history', 'student-history'], ['/student/mypage', 'student-mypage']] as const) {
    await page.goto(path); await check(page, label)
  }
})

test('DESK-02 선생님 주요 화면·모달(1280×720): 가로 넘침 없음, 모달이 화면 안에 들어오고 저장 버튼에 닿을 수 있음', async ({ page }) => {
  const student = await createStudent('desk-ts', '데스크제자')
  const teacher = await createTeacher('desk-t', '데스크선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  await loginUi(page, teacher.email, 'teacher')
  await check(page, 'teacher-home')
  for (const [path, label] of [['/teacher/students', 'teacher-students'], [`/teacher/students/${student.studentId}`, 'teacher-student-detail'],
    ['/teacher/homeworks', 'teacher-homeworks'], ['/teacher/analytics', 'teacher-analytics'], [`/teacher/reports/${student.studentId}`, 'teacher-report']] as const) {
    await page.goto(path); await check(page, label)
  }
  await page.goto(`/teacher/students/${student.studentId}`)
  await page.getByRole('button', { name: '숙제 배정' }).click()
  await modalFits(page, '숙제 등록')
  await check(page, 'modal-homework')
  await page.getByRole('dialog', { name: '숙제 등록' }).getByRole('button', { name: '취소' }).click()
  await page.getByRole('button', { name: '정보 수정' }).click()
  await modalFits(page, /학생 정보|제자 정보|정보 수정/)
  await check(page, 'modal-student-form')
})
