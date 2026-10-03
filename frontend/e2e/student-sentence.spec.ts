import { expect, test } from '@playwright/test'
import { createStudent, loginUi, mic, openPractice, recordAndAnalyze } from './helpers'

test.use({ launchOptions: { args: mic('mic-weather.wav') }, permissions: ['microphone'] })

test('E2E-04 문장 녹음 → 문장 유형 결과', async ({ page }) => {
  const student = await createStudent('sentence', '문장학생')
  await loginUi(page, student.email)
  await openPractice(page, /유창성/, /천천히 말하기/)
  await expect(page.getByText('오늘은 날씨가 좋아요.').first()).toBeVisible()
  // 파일(약 3.4초)이 반복 재생되므로 문장 하나가 끝나고 무음이 남도록 4.2초 녹음한다.
  await recordAndAnalyze(page, 4200)
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText('문장', { exact: true })).toBeVisible()
  await expect(page.getByText('단어 비교')).toBeVisible()
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible()
})

test('E2E-05 말하는 도중 녹음 종료 → 판정 보류 → 다시 녹음', async ({ page }) => {
  const student = await createStudent('hold', '보류학생')
  await loginUi(page, student.email)
  await openPractice(page, /유창성/, /천천히 말하기/)
  await recordAndAnalyze(page, 1500)
  const holdBadge = page.locator('span').filter({ hasText: /^판정 보류$/ })
  await expect(holdBadge).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText(/말이 끝나기 전에 녹음이 멈췄어요/)).toBeVisible()
  await page.getByRole('button', { name: '다시 녹음' }).first().click()
  await expect(page.getByRole('button', { name: '녹음 시작' })).toBeVisible()
  await expect(holdBadge).toHaveCount(0)
})
