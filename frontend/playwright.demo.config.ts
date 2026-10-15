import { defineConfig } from '@playwright/test'
import base from './playwright.config'

/**
 * 발표용 시연 영상 녹화(일반 E2E와 별도 실행: npx playwright test -c playwright.demo.config.ts).
 * 서버 구성(테스트 DB 백엔드·mailpit·프런트)은 E2E 설정을 그대로 쓴다. 개발 DB로는 실행하지 않는다.
 * 영상: test-results/demo/<테스트>/video.webm (한 페이지에서 선생님 → 학생 → 선생님 순서로 진행해 영상이 하나로 이어진다)
 */
export default defineConfig({
  ...base,
  testDir: './e2e-demo',
  testMatch: '**/*.demo.ts',
  timeout: 300_000,
  outputDir: 'test-results/demo',
  reporter: [['list']],
  use: {
    ...base.use,
    viewport: { width: 1280, height: 720 },
    video: { mode: 'on', size: { width: 1280, height: 720 } },
    trace: 'off',
  },
})
