# ChatterLand 테스트 계획

작성일: 2026-10-02 · 대상: `frontend/`(Next.js 16, React 19), `backend/`(Spring Boot 4, MyBatis, MariaDB/MySQL)

## 1. 범위

| 포함 | 제외(사유) |
|---|---|
| 인증·계정(가입, 로그인, 로그아웃, 토큰 갱신, 아이디 찾기, 비밀번호 재설정) | AI 대화의 실제 응답: 외부 AI API 키가 없어 실행 불가(BLOCKED). 설정 누락 시 503만 검증 |
| 학생: 홈, 숙제, 말하기 연습, 녹음, 분석 결과, 학습 기록 | 실제 아동 음성 정확도: 아동 음성 샘플 없음(BLOCKED) |
| 교사: 담당 학생, 숙제, 분석 화면, 음성 검토·확정, 리포트·PDF | 발음 점수: 검증된 음소 평가 모델 미도입, 성공 대상이 아님(미구현) |
| 음성 분석: whisper.cpp 실제 추론, VAD, 자동 분석 근거, 판정 보류, 실패 처리 | 부하·성능 테스트, 운영 배포 환경 |
| 보안·권한: 미인증, 역할 위반, 담당 범위, 타인 데이터, 민감 정보 노출 | 외부 SMTP 실서버(개발용 mailpit/GreenMail로 대체) |

## 2. 테스트 계층과 도구

| 계층 | 도구 | 위치 | 외부 의존 |
|---|---|---|---|
| 백엔드 단위 | JUnit 5, Mockito, jakarta.validation | `backend/src/test/java/**/service/impl`, `**/dto`, `**/jwt` | 없음(일부는 셸 스크립트로 whisper-cli 대체) |
| 백엔드 통합 | Spring Boot Test, MockMvc, JdbcTemplate, GreenMail | `backend/src/test/java/com/example/backend/*IntegrationTest.java` | 테스트 전용 MariaDB, whisper.cpp+모델(음성 테스트) |
| 프런트 단위·컴포넌트·기능 | Vitest, Testing Library, jsdom | `frontend/src/**/*.test.ts(x)` | 없음(API는 mock) |
| E2E | Playwright + Google Chrome(가짜 마이크) | `frontend/e2e/*.spec.ts` | 백엔드(테스트 DB), whisper.cpp, mailpit |

추가한 개발 의존성(프런트, devDependencies만): `vitest`, `@vitejs/plugin-react`, `jsdom`, `@testing-library/react`, `@testing-library/dom`, `@playwright/test`. 프런트에 테스트 실행기가 전혀 없었고, Next.js 공식 문서(`node_modules/next/dist/docs/01-app/02-guides/testing`)가 권장하는 구성이다. jest-dom 매처는 추가하지 않고 DOM 속성으로 직접 검증한다. 백엔드는 의존성을 추가하지 않았다.

## 3. 테스트 데이터와 격리

- 테스트 DB: `backend/scripts/test-db.sh start`가 `backend/build/test-db`에 별도 MariaDB 인스턴스(127.0.0.1:3310, DB `chatterland_test`)를 만든다. 비밀번호는 무작위로 생성해 `build/test-db/env`(권한 600, git 제외)에만 저장한다.
- `./gradlew test`는 이 파일이 있으면 자동으로 테스트 DB를 사용한다(`build.gradle`의 test 작업). 없으면 경고 후 `.env`의 개발 DB를 쓴다.
- 통합 테스트는 `@Transactional`로 각 테스트 후 롤백한다. E2E 데이터는 테스트 DB에 남으며 `scripts/test-db.sh reset`으로 모두 지운다.
- E2E 백엔드는 Playwright가 테스트 DB 설정으로 직접 실행한다. 개발 DB로 실행 중인 백엔드를 재사용하지 않도록 8080 포트가 사용 중이면 실패한다.
- 계정 규칙: 통합 `speech-*/authz-*@example.test`(롤백), E2E `e2e-<시나리오>-<시각>-<난수>@example.test`.
- 음성 샘플: 모두 macOS `say -v Yuna` **합성음**(+ Zeroth-Korean CC BY 4.0 배경 소음) 또는 생성한 신호음이다. 실제 아동 녹음이 아니다. 목록은 `backend/src/test/resources/speech/README.md`, `frontend/e2e/fixtures/README.md`.

## 4. 실행 방법

```bash
backend/scripts/test-db.sh start   # 테스트 DB(개발 DB와 분리). 통합·E2E는 이게 없으면 원인을 표시하고 실패한다
scripts/ci/setup-whisper.sh        # whisper.cpp + 모델(SHA-256 검증)
scripts/test-all.sh                # 단위 → 통합 → 프런트 → E2E, 결과를 PASS/FAIL/SKIPPED/BLOCKED/NOT RUN 으로 요약
scripts/test-all.sh unit frontend  # 단계 선택
REQUIRE=1 scripts/test-all.sh      # whisper 모델·Mailpit 누락을 SKIPPED가 아니라 FAIL로(CI)
```

단계별 직접 실행: `cd backend && sh gradlew unitTest | integrationTest | test`, `cd frontend && npm test`, `npm run test:e2e`.
GitHub Actions: `.github/workflows/ci.yml`(MariaDB·Mailpit 서비스 컨테이너, whisper 캐시, 단계별 job).

## 5. 합격 기준

- 실행한 테스트만 집계한다. 건너뛴 테스트는 PASS가 아니다.
- 실패 시 코드 결함과 환경 문제를 구분해 기록하고, 검증 조건을 약화하지 않는다.
- 불변 조건: `textMatchRate`는 발음 점수가 아님, 자동 분석과 교사 확정 결과는 서로 덮어쓰지 않음, 판정 보류는 정상 결과와 구분, 실패 시 가짜 결과 저장 없음, 권한 없는 데이터 조회 불가.

## 6. 알려진 한계

- 자모 비교 오류 후보는 음성 인식 텍스트 기반 추정이다. 실제 음향학적 발음 오류를 확정하지 않는다(예: "다디오"를 Whisper가 "타디오"로 적으면 후보는 ㄹ→ㅌ).
- 판정 보류 기준값과 인식 정확도는 합성음으로만 확인했다.
