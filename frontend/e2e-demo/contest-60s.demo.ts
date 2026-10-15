import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import { api, createStudent, createTeacher, loginUi } from '../e2e/helpers'
import { caption, installCaption } from './demoCaption'

/**
 * 공모전 발표용 60초 무내레이션 시연(한 번의 실제 실행을 자르지 않고 녹화).
 * 첫 화면 → 선생님 숙제 배정 → 학생 녹음 → 음성 인식·측정 결과 → 선생님 학습 기록 → 서비스 로고(첫 화면 브랜드).
 * - 실제 서버·테스트 DB·로컬 음성 인식(whisper.cpp)·VAD. 마이크 입력은 합성음 파일(mic-weather-demo.wav, 실제 아동 음성 아님).
 * - 외부 AI(Gemini)를 부르지 않는다: AI 설명(RAG)은 승인된 자료가 없어 생성되지 않으므로 시연에서 뺐다. 요청이 나가면 실패한다.
 * - 발음 정확도는 '준비 중'(모델 없음), 말하기 속도·멈춤은 측정값으로만 보여 준다.
 */
const micFile = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'mic-weather-demo.wav')
test.use({
  launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', `--use-file-for-fake-audio-capture=${micFile}`], slowMo: 70 },
  permissions: ['microphone'],
})

const TARGET = '오늘은 날씨가 좋아요.'
const CONTENT = '천천히 말하기'
const pause = (page: Page, ms: number) => page.waitForTimeout(ms)

test('공모전 60초 시연: 첫 화면 → 선생님 숙제 → 학생 녹음 → 음성 인식·측정 → 선생님 기록 → 로고', async ({ page }) => {
  await installCaption(page.context())
  const externalAi: string[] = []
  page.on('request', (request) => {
    if (request.method() === 'POST' && (/\/feedback$/.test(request.url()) || /\/api\/v1\/ai\//.test(request.url()))) externalAi.push(request.url())
  })
  // 사전 준비(테스트 DB): 시연용 계정과 담당 연결
  const student = await createStudent('contest', '하늘')
  const teacher = await createTeacher('contest-t', '김선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)
  const started = Date.now()
  const mark = (label: string) => console.log(`[timeline] ${((Date.now() - started) / 1000).toFixed(1)}s ${label}`)

  // 0~5초: 첫 화면
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '채터랜드' })).toBeVisible()
  mark('첫 화면')
  await caption(page, '채터랜드', '아이의 말하기 연습을 돕는 서비스')
  await pause(page, 4300)

  // 5~20초: 선생님 — 학생 관리·숙제 등록
  await loginUi(page, teacher.email, 'teacher')
  mark('선생님 홈')
  await caption(page, '선생님 · 학생 관리', '담당 학생을 고르고')
  await pause(page, 1500)
  await page.getByRole('button', { name: /제자 관리/ }).click()
  await pause(page, 1800)
  await page.getByRole('button', { name: /하늘/ }).first().click()
  await pause(page, 2000)
  await page.getByRole('button', { name: '숙제 배정' }).click()
  const dialog = page.getByRole('dialog', { name: '숙제 등록' })
  await caption(page, '선생님 · 숙제 등록', `연습 콘텐츠 "${CONTENT}"`)
  await dialog.getByLabel('콘텐츠 검색').fill(CONTENT)
  await dialog.getByRole('button', { name: '콘텐츠 찾기' }).click()
  await pause(page, 1200)
  await dialog.getByRole('button', { name: `${CONTENT} 선택` }).click()
  await pause(page, 2000)
  await dialog.getByRole('button', { name: '숙제 등록' }).click()
  await expect(dialog).toHaveCount(0)
  await caption(page, '숙제 저장 완료', '학생 화면에 바로 보여요')
  await pause(page, 2000)
  mark('숙제 저장')
  await page.getByRole('button', { name: '로그아웃' }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/teacher'))

  // 20~35초: 학생 — 숙제 확인·녹음
  await caption(page, '학생 · 오늘의 숙제', '선생님이 낸 숙제를 열고')
  await loginUi(page, student.email)
  mark('학생 홈')
  const homework = page.getByRole('region', { name: '오늘의 숙제' }).getByRole('button').first()
  await expect(homework).toBeVisible()
  await pause(page, 2000)
  await homework.click()
  await expect(page.getByText(new RegExp(`연습: ${CONTENT}`))).toBeVisible()
  await pause(page, 1800)
  await page.getByRole('button', { name: /연습하러 가기/ }).first().click()
  await expect(page.getByText(TARGET).first()).toBeVisible()
  await pause(page, 1800)
  await caption(page, '학생 · 문장 읽고 녹음', '마이크 입력: 합성음(실제 아동 음성 아님)')
  await page.getByRole('button', { name: '녹음 시작' }).click()
  await expect(page.getByText(/녹음 중…/).first()).toBeVisible()
  await page.waitForTimeout(4200)
  await page.getByRole('button', { name: '녹음 중지' }).click()
  await expect(page.getByLabel('내 녹음 재생')).toBeVisible()
  await pause(page, 1200)
  mark('녹음 완료')

  // 35~50초: 음성 인식·측정 결과
  await caption(page, 'AI 음성 인식', '서버에서 음성을 글자로 바꾸고 목표 문장과 비교')
  await page.getByRole('button', { name: '분석하기' }).click()
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible()
  mark('분석 결과')
  const transcript = (await page.locator('dt', { hasText: '인식 결과' }).first().locator('xpath=following-sibling::dd[1]').innerText()).trim()
  const rate = Number((await page.getByRole('progressbar', { name: /텍스트 일치율/ }).first().getAttribute('aria-label'))?.match(/(\d+)%/)?.[1])
  await caption(page, '인식 결과와 텍스트 일치율', '글자 비교 결과 · 발음 점수 아님')
  await pause(page, 4800)
  const speed = page.getByText(/초당 \d+\.\d글자/).first()
  await page.getByText('말하기 속도').first().scrollIntoViewIfNeeded()
  const speedText = await speed.isVisible() ? (await speed.innerText()).trim() : '측정 불가'
  await caption(page, '말하기 속도 · 멈춤 측정', '녹음에서 잰 값 · 발음 분석은 준비 중')
  await pause(page, 5000)
  mark('측정값')

  // 50~60초: 선생님 학습 기록 → 로고
  await page.goto('/student/mypage')
  await page.getByRole('button', { name: '로그아웃' }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/student'))
  await loginUi(page, teacher.email, 'teacher')
  await page.getByRole('button', { name: /제자 관리/ }).click()
  await page.getByRole('button', { name: /하늘/ }).first().click()
  await page.getByRole('tab', { name: '학습 현황' }).click()
  const record = page.getByRole('region', { name: '연습 기록' }).locator('article').filter({ hasText: '숙제 연습' })
  await expect(record).toHaveCount(1)
  await expect(record).toContainText(transcript)
  await expect(record).toContainText(`${rate}%`)
  await record.scrollIntoViewIfNeeded()
  mark('선생님 기록')
  await caption(page, '선생님 · 학습 기록 확인', '학생이 본 결과와 같은 기록')
  await pause(page, 4200)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '채터랜드' })).toBeVisible()
  await caption(page, '', '')
  mark('로고')
  await pause(page, 3200)
  mark('끝')

  expect(externalAi, '시연 중 외부 AI 요청이 없어야 한다').toEqual([])
  console.log(`[result] 인식 "${transcript}" · 텍스트 일치율 ${rate}% · ${speedText}`)
})
