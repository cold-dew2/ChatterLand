import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from '@playwright/test'

const here = path.dirname(fileURLToPath(import.meta.url))

/**
 * 브라우저 E2E. 백엔드는 테스트 전용 DB(backend/scripts/test-db.sh start)로만 실행한다.
 * 개발용 DB로 실행 중인 백엔드를 실수로 재사용하지 않도록 8080 포트가 사용 중이면 실패한다(E2E_REUSE_BACKEND=1로 명시할 때만 재사용).
 * 필요한 것: Google Chrome, whisper.cpp + 모델(백엔드 README), MariaDB(테스트 DB), mailpit(비밀번호 재설정 메일).
 */
// 테스트 DB 연결 정보: 환경변수(TEST_DB_URL 등, CI) 우선, 없으면 backend/scripts/test-db.sh 가 만든 파일.
const testDbEnvFile = path.resolve(here, '../backend/build/test-db/env')
const fileEnv: Record<string, string> = fs.existsSync(testDbEnvFile) ? Object.fromEntries(fs.readFileSync(testDbEnvFile, 'utf8').split('\n')
  .filter((line) => line.includes('=')).map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)])) : {}
const testDb = {
  TEST_DB_URL: process.env.TEST_DB_URL ?? fileEnv.TEST_DB_URL,
  TEST_DB_USERNAME: process.env.TEST_DB_USERNAME ?? fileEnv.TEST_DB_USERNAME,
  TEST_DB_PASSWORD: process.env.TEST_DB_PASSWORD ?? fileEnv.TEST_DB_PASSWORD,
}
if (!testDb.TEST_DB_URL) throw new Error('테스트 DB 설정이 없습니다. backend/scripts/test-db.sh start 를 실행하거나 TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD 를 지정하세요. (개발 DB로는 E2E를 실행하지 않습니다)')

export default defineConfig({
  testDir: './e2e',
  timeout: 180_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['json', { outputFile: 'test-results/e2e-results.json' }]],
  use: {
    baseURL: 'http://localhost:3000',
    channel: 'chrome',
    viewport: { width: 390, height: 844 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    { command: 'mailpit --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025', url: 'http://127.0.0.1:8025', reuseExistingServer: true },
    {
      command: 'sh gradlew bootRun -q', cwd: path.resolve(here, '../backend'), url: 'http://localhost:8080/api/v1/centers',
      reuseExistingServer: process.env.E2E_REUSE_BACKEND === '1', timeout: 240_000,
      env: {
        ...(process.env as Record<string, string>),
        DB_URL: testDb.TEST_DB_URL!, DB_USERNAME: testDb.TEST_DB_USERNAME ?? '', DB_PASSWORD: testDb.TEST_DB_PASSWORD ?? '',
        MAIL_HOST: '127.0.0.1', MAIL_PORT: '1025', MAIL_FROM: 'no-reply@chatterland.local',
        AUDIO_STORAGE_PATH: path.resolve(here, '../backend/build/test-db/audio'),
      },
    },
    { command: 'npm run dev', url: 'http://localhost:3000/login', reuseExistingServer: true, timeout: 180_000 },
  ],
})
