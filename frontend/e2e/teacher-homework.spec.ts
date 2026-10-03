import { expect, test } from '@playwright/test'
import { api, createStudent, createTeacher, loginUi } from './helpers'

test('E2E-12 교사 숙제 배정 모달: 취소 → 검증 → 저장 실패 안내 → 배정 → 학생 화면 반영', async ({ page, browser }) => {
  const student = await createStudent('hw-modal', '모달학생')
  const teacher = await createTeacher('hw-modal-t', '모달선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  await loginUi(page, teacher.email, 'teacher')
  await page.goto(`/teacher/students/${student.studentId}`)

  await page.getByRole('button', { name: '숙제 배정' }).click()
  const dialog = page.getByRole('dialog', { name: '숙제 등록' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByLabel(/제자 선택/)).toHaveValue(String(student.studentId))
  await dialog.getByRole('button', { name: '취소' }).click()
  await expect(dialog).toHaveCount(0)

  await page.getByRole('button', { name: '숙제 배정' }).click()
  await dialog.getByLabel(/목표 시간/).fill('0')
  await dialog.getByRole('button', { name: '숙제 등록' }).click()
  await expect(dialog.getByText('목표 시간은 1~120분 사이로 입력해 주세요.')).toBeVisible()
  await dialog.getByLabel(/목표 시간/).fill('15')
  await dialog.getByLabel(/숙제 내용/).fill('ㄹ 말 5번 연습')
  await dialog.getByRole('radio', { name: '조음 말하기' }).check()

  // 서버가 거부하면(예: 담당 해제) 모달을 닫지 않고 이유를 보여준다.
  await page.route('**/api/v1/teachers/me/homeworks', (route) => route.request().method() === 'POST'
    ? route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ success: false, code: '403 FORBIDDEN', message: '담당 학생 정보에 접근할 수 없습니다.' }) })
    : route.continue(), { times: 1 })
  await dialog.getByRole('button', { name: '숙제 등록' }).click()
  await expect(dialog.getByText('담당 학생 정보에 접근할 수 없습니다.')).toBeVisible()
  await expect(dialog).toBeVisible()

  await dialog.getByRole('button', { name: '숙제 등록' }).click()
  await expect(dialog).toHaveCount(0)
  const homeworks = await api<{ content?: { title: string; type: string; targetMinutes: number }[] } | { title: string; type: string; targetMinutes: number }[]>(
    'GET', '/api/v1/students/me/homeworks', undefined, student.token)
  const list = Array.isArray(homeworks) ? homeworks : homeworks.content ?? []
  expect(list).toHaveLength(1)
  expect(list[0]).toMatchObject({ title: 'ㄹ 말 5번 연습', type: '조음 말하기', targetMinutes: 15 })

  const studentPage = await (await browser.newContext()).newPage()
  await loginUi(studentPage, student.email)
  await expect(studentPage.getByRole('region', { name: '오늘의 숙제' }).getByRole('button', { name: /ㄹ 말 5번 연습/ })).toBeVisible()
})
