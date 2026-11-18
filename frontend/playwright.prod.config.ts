import { defineConfig } from '@playwright/test'

/**
 * 운영 환경 E2E 점검(Vercel 프런트 + Cloud Run 백엔드). 로컬 서버를 띄우지 않는다.
 * - 운영 DB에 e2e-…@example.test 계정과 학습 데이터가 생긴다. 실행 전 Cloud SQL 백업, 실행 후 정리(docs/deploy/gcp-cloud-run.md).
 * - 메일 서버가 필요한 시나리오(E2E-19)는 제외한다(운영 SMTP 미설정).
 * 사용: E2E_API_URL=<Cloud Run 주소> npx playwright test -c playwright.prod.config.ts
 */
const appUrl = process.env.E2E_APP_URL ?? 'https://chatter-land.vercel.app'
if (!process.env.E2E_API_URL) throw new Error('E2E_API_URL(운영 백엔드 주소)이 필요합니다')

export default defineConfig({
  testDir: './e2e',
  grepInvert: /E2E-19/,
  timeout: 240_000,
  expect: { timeout: 30_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['json', { outputFile: 'test-results/e2e-prod-results.json' }]],
  use: {
    baseURL: appUrl,
    channel: 'chrome',
    viewport: { width: 390, height: 844 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
