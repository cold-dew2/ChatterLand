import { expect, test } from '@playwright/test'
import { api, createStudent, createTeacher, loginUi, mic, recordAndAnalyze } from './helpers'

test.use({ launchOptions: { args: mic('mic-radio.wav') }, permissions: ['microphone'] })

test('E2E-25 숙제 없이 연습 찾기 → 녹음·분석·저장(자율 연습) → 선생님이 숙제 콘텐츠 지정 → 숙제 연습 → 선생님이 두 기록을 구분해 확인', async ({ page, browser }) => {
  const student = await createStudent('learn', '자율학생')
  const teacher = await createTeacher('learn-t', '학습선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)

  // 1. 학생: 숙제 없이 전체 연습 찾기에서 발음 유형으로 골라 연습
  await loginUi(page, student.email)
  await page.goto('/student/practice')
  await page.getByRole('button', { name: /전체 연습 찾기/ }).click()
  await page.getByLabel('발음 유형').selectOption('BASIC_CONSONANT')
  const start = page.getByRole('button', { name: /연습 시작$/ }).first()
  await expect(start).toBeVisible()
  const setName = (await start.getAttribute('aria-label'))!.replace(/ 연습 시작$/, '')
  await start.click()
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible()

  // 2. 선생님: 연습 콘텐츠를 골라 숙제 만들기
  const teacherPage = await (await browser.newContext()).newPage()
  await loginUi(teacherPage, teacher.email, 'teacher')
  await teacherPage.goto(`/teacher/students/${student.studentId}`)
  await teacherPage.getByRole('button', { name: '숙제 배정' }).click()
  const dialog = teacherPage.getByRole('dialog', { name: '숙제 등록' })
  await dialog.getByLabel('콘텐츠 검색').fill(setName)
  await dialog.getByRole('button', { name: '콘텐츠 찾기' }).click()
  await dialog.getByRole('button', { name: `${setName} 선택` }).click()
  await dialog.getByRole('button', { name: '숙제 등록' }).click()
  await expect(dialog).toHaveCount(0)

  // 3. 학생: 숙제에서 지정된 연습을 바로 시작 → 숙제 연습으로 저장
  await page.goto('/student')
  await page.getByRole('region', { name: '오늘의 숙제' }).getByRole('button').first().click()
  await expect(page.getByText(new RegExp(`연습: ${setName}`))).toBeVisible()
  await page.getByRole('button', { name: /연습하러 가기/ }).first().click()
  await recordAndAnalyze(page, 2500)
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible()

  // 4. 선생님: 학습 현황에서 자율 연습·숙제 연습을 구분해 본다(학생이 따로 제출하지 않음)
  await teacherPage.reload()
  await teacherPage.getByRole('tab', { name: '학습 현황' }).click()
  const panel = teacherPage.getByRole('region', { name: '연습 기록' })
  await expect(panel.getByText('2회')).toBeVisible()
  await expect(panel.locator('article').filter({ hasText: '숙제 연습' })).toHaveCount(1)
  await expect(panel.locator('article').filter({ hasText: '자율 연습' })).toHaveCount(1)
  await expect(panel.getByText('(발음 점수 아님)').first()).toBeVisible()
  await panel.getByRole('tab', { name: '숙제 연습' }).click()
  await expect(panel.locator('article')).toHaveCount(1)

  // 다른 학생 ID로 직접 요청하면 서버가 막는다
  const other = await createStudent('learn-other', '다른학생')
  const blocked = await fetch(`http://localhost:8080/api/v1/teachers/me/students/${other.studentId}/practice-attempts`, { headers: { Authorization: `Bearer ${teacher.token}` } })
  expect([403, 404]).toContain(blocked.status)
})
