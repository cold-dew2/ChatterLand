import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import { api, createStudent, createTeacher, loginUi } from '../e2e/helpers'

/**
 * 발표용 시연: 선생님(숙제 배정) → 학생(녹음·분석·저장·재조회) → 선생님(같은 기록 확인).
 * - 실제 서버·테스트 DB·로컬 음성 인식(whisper.cpp)을 쓴다. 결과를 고정값으로 꾸미지 않는다.
 * - 마이크 입력은 Chrome 가짜 마이크로 넣는 합성음 파일(mic-weather-demo.wav: e2e/fixtures/mic-weather.wav "오늘은 날씨가 좋아요." 뒤에 무음 4초)이다.
 *   실제 아동 음성이 아니며 화면 자막에 표시한다. 무음은 파일이 반복 재생되어 다음 문장 앞부분이 녹음되지 않도록 붙였다(음성 자체는 그대로).
 * - 외부 AI(Gemini)는 부르지 않는다. AI 설명 생성 요청이 나가면 실패한다.
 * - 화면 확인과 함께 학생이 본 결과와 선생님이 본 기록이 같은지(인식 결과·텍스트 일치율·숙제 연습 구분)를 검사한다.
 */
const micFile = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'mic-weather-demo.wav')
test.use({
  launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', `--use-file-for-fake-audio-capture=${micFile}`], slowMo: 120 },
  permissions: ['microphone'],
})

const TARGET = '오늘은 날씨가 좋아요.'
const CONTENT = '천천히 말하기'

async function caption(page: Page, title: string, detail = '') {
  await page.evaluate(([t, d]) => {
    sessionStorage.setItem('__demoCaption', JSON.stringify({ t, d }))
    ;(window as unknown as { __renderDemoCaption?: () => void }).__renderDemoCaption?.()
  }, [title, detail])
}

/** 시청자가 화면을 읽을 시간. 결과는 이 대기가 아니라 화면 상태로 기다린다. */
const pause = (page: Page, ms = 1500) => page.waitForTimeout(ms)

test('발표 시연: 선생님 숙제 배정 → 학생 녹음·음성 인식·저장 → 선생님 결과 확인', async ({ page }) => {
  // 자막: 페이지를 새로 불러와도 유지(sessionStorage). 클릭을 가로채지 않는다(pointer-events: none).
  await page.context().addInitScript(() => {
    const render = () => {
      const raw = sessionStorage.getItem('__demoCaption')
      let box = document.getElementById('__demo-caption')
      if (!raw) { box?.remove(); return }
      const { t, d } = JSON.parse(raw) as { t: string; d: string }
      if (!box) {
        box = document.createElement('div')
        box.id = '__demo-caption'
        box.setAttribute('aria-hidden', 'true')
        // 개발 서버 표시(Next.js dev tools 배지)는 영상에서 가린다(앱 화면과 무관)
        const hide = document.createElement('style'); hide.textContent = 'nextjs-portal{display:none!important}'; document.head.appendChild(hide)
        box.style.cssText = 'position:fixed;right:16px;bottom:84px;z-index:2147483647;width:290px;padding:12px 16px;border-radius:12px;'
          + 'background:rgba(17,24,39,.88);color:#fff;font:600 15px/1.45 system-ui,sans-serif;pointer-events:none;box-shadow:0 6px 20px rgba(0,0,0,.25)'
        document.body.appendChild(box)
      }
      box.innerHTML = ''
      const title = document.createElement('div'); title.textContent = t; box.appendChild(title)
      if (d) { const detail = document.createElement('div'); detail.textContent = d; detail.style.cssText = 'margin-top:4px;font-weight:400;font-size:13px;opacity:.85'; box.appendChild(detail) }
    }
    ;(window as unknown as { __renderDemoCaption?: () => void }).__renderDemoCaption = render
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', render)
    else render()
  })
  const aiRequests: string[] = []
  page.on('request', (request) => { if (request.method() === 'POST' && /\/feedback$/.test(request.url())) aiRequests.push(request.url()) })

  // 사전 준비(테스트 DB): 시연용 선생님·학생 계정과 담당 연결
  const student = await createStudent('demo', '시연 학생')
  const teacher = await createTeacher('demo-t', '시연 선생님')
  await api('POST', '/api/v1/teachers/me/students', { studentId: student.studentId, name: student.name }, teacher.token)

  // ── 장면 1. 선생님: 학습 준비 ──
  await page.goto('/login')
  await caption(page, '장면 1. 선생님 — 학습 준비', '시연용 테스트 계정 · 테스트 DB')
  await pause(page)
  await loginUi(page, teacher.email, 'teacher')
  await pause(page)
  await page.getByRole('button', { name: /제자 관리/ }).click()
  await page.getByRole('button', { name: /시연 학생/ }).first().click()
  await pause(page)
  await page.getByRole('button', { name: '숙제 배정' }).click()
  const dialog = page.getByRole('dialog', { name: '숙제 등록' })
  await caption(page, '장면 1. 선생님 — 학습 문장 숙제 배정', `연습 콘텐츠 "${CONTENT}" (첫 문장: ${TARGET})`)
  await dialog.getByLabel('콘텐츠 검색').fill(CONTENT)
  await dialog.getByRole('button', { name: '콘텐츠 찾기' }).click()
  await dialog.getByRole('button', { name: `${CONTENT} 선택` }).click()
  await pause(page)
  await dialog.getByRole('button', { name: '숙제 등록' }).click()
  await expect(dialog).toHaveCount(0)
  await caption(page, '장면 1. 숙제 저장 완료', '서버에 저장된 숙제가 학생 계정에 표시됩니다')
  await pause(page, 2000)
  await page.getByRole('button', { name: '로그아웃' }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/teacher'))

  // ── 장면 2. 학생: 말하기 연습 ──
  await caption(page, '장면 2. 학생 — 말하기 연습', '선생님이 배정한 숙제 확인')
  await loginUi(page, student.email)
  const homeworkCard = page.getByRole('region', { name: '오늘의 숙제' }).getByRole('button').first()
  await expect(homeworkCard).toBeVisible()
  await pause(page)
  await homeworkCard.click()
  await expect(page.getByText(new RegExp(`연습: ${CONTENT}`))).toBeVisible()
  await pause(page)
  await page.getByRole('button', { name: /연습하러 가기/ }).first().click()
  await expect(page.getByText(TARGET).first()).toBeVisible()
  await pause(page)
  await caption(page, '장면 2. 마이크 녹음', `마이크 입력: 합성음 파일 "${TARGET}" (실제 아동 음성 아님)`)
  await page.getByRole('button', { name: '녹음 시작' }).click()
  await expect(page.getByText(/녹음 중…/).first()).toBeVisible()
  await page.waitForTimeout(4200) // 문장(약 3.4초) 뒤 무음이 조금 남도록 녹음한다
  await page.getByRole('button', { name: '녹음 중지' }).click()
  await expect(page.getByLabel('내 녹음 재생')).toBeVisible()
  await pause(page)

  // ── 장면 3. AI: 음성 인식·비교·저장 ──
  await caption(page, '장면 3. 음성 인식 — 녹음 파일을 서버로 전송', '로컬 음성 인식(whisper.cpp)으로 글자로 바꾸고 목표 문장과 비교합니다')
  await page.getByRole('button', { name: '분석하기' }).click()
  await expect(page.getByText('텍스트 일치율').first()).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText('학습 기록에 저장했어요.')).toBeVisible()
  const transcript = (await page.locator('dt', { hasText: '인식 결과' }).first().locator('xpath=following-sibling::dd[1]').innerText()).trim()
  const rateText = await page.getByRole('progressbar', { name: /텍스트 일치율/ }).first().getAttribute('aria-label')
  const rate = Number(rateText?.match(/(\d+)%/)?.[1])
  expect(transcript.length).toBeGreaterThan(0)
  expect(Number.isFinite(rate)).toBe(true)
  await caption(page, '장면 3. 분석 결과 저장', `인식 결과 "${transcript}" · 텍스트 일치율 ${rate}% (발음 점수 아님)`)
  await page.getByText('텍스트 일치율').first().scrollIntoViewIfNeeded()
  await pause(page, 3000)

  // 재조회: 새로 불러온 학습 기록 화면에 서버에 저장된 기록이 보인다
  await page.goto('/student/history')
  await caption(page, '장면 3. 저장된 기록 다시 조회', '새로 불러온 학습 기록 화면')
  const saved = page.locator('div, article, li').filter({ hasText: CONTENT }).filter({ hasText: '숙제 연습' }).filter({ hasText: `텍스트 일치율 ${rate}%` })
  await expect(saved.first()).toBeVisible()
  await pause(page, 2500)
  await page.goto('/student/mypage')
  await page.getByRole('button', { name: '로그아웃' }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/student'))

  // ── 장면 4. 선생님: 학습 결과 확인 ──
  await caption(page, '장면 4. 선생님 — 학습 결과 확인', '학생이 방금 한 숙제 연습 기록')
  await loginUi(page, teacher.email, 'teacher')
  await page.getByRole('button', { name: /제자 관리/ }).click()
  await page.getByRole('button', { name: /시연 학생/ }).first().click()
  await page.getByRole('tab', { name: '학습 현황' }).click()
  const records = page.getByRole('region', { name: '연습 기록' })
  const homeworkRecord = records.locator('article').filter({ hasText: '숙제 연습' })
  await expect(homeworkRecord).toHaveCount(1)
  await expect(homeworkRecord).toContainText(TARGET)
  await expect(homeworkRecord).toContainText(transcript)
  await expect(homeworkRecord).toContainText(`${rate}%`)
  await homeworkRecord.scrollIntoViewIfNeeded()
  await caption(page, '장면 4. 학생 화면과 같은 기록', `인식 결과 "${transcript}" · 텍스트 일치율 ${rate}% — 학생이 본 결과와 같습니다`)
  await pause(page, 3500)

  // 저장 데이터 교차 확인(화면 밖): 선생님 API의 숙제 연습 기록 1건이 화면 값과 같다
  const attempts = await api<{ content?: { attemptType?: string; transcript?: string; textMatchRate?: number }[] }>('GET',
    `/api/v1/teachers/me/students/${student.studentId}/practice-attempts`, undefined, teacher.token)
  const rows = attempts.content ?? []
  expect(rows).toHaveLength(1)
  expect(rows[0].attemptType).toBe('HOMEWORK')
  expect(rows[0].transcript?.trim()).toBe(transcript)
  expect(Math.round(rows[0].textMatchRate ?? -1)).toBe(rate)
  expect(aiRequests, '시연 중 외부 AI 설명 생성 요청이 없어야 한다').toEqual([])

  await caption(page, '시연 끝', '교사 숙제 배정 → 학생 녹음 → 음성 인식·비교·저장 → 교사 확인 (발음 정확도 평가는 하지 않음)')
  await pause(page, 3000)
})
