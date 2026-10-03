# 테스트 실행 결과 보고서

최종 실행: 2026-10-03 08:46 (`REQUIRE=1 scripts/test-all.sh`, 13절) · 이전 08:04(12절) · 이전 01:17(11절), 00:37(10절), 2026-10-02 23:51(9절) · 이전 기준 실행 19:17 · 환경: macOS(Apple M2), JDK 17, Node 26, Google Chrome, MariaDB 13.0.2(테스트 전용 인스턴스, 127.0.0.1:3310), whisper.cpp 1.9.4 + ggml-large-v3-turbo-q5_0 + Silero VAD v5.1.2, Mailpit

## 1. 요약

| 단계 | 결과 | 테스트 | PASS | FAIL | SKIPPED | 비고 |
|---|---|---|---|---|---|---|
| 백엔드 단위 (`gradlew unitTest`) | PASS | 70 | 70 | 0 | 0 | DB 없이 실행 |
| 백엔드 통합 (`gradlew integrationTest`) | PASS | 53 | 53 | 0 | 0 | 테스트 DB 필수, 실제 whisper.cpp·Mailpit 포함 |
| 프런트 (lint·tsc·Vitest·build) | PASS | 55 | 55 | 0 | 0 | lint 오류 0(경고 1, 기존) |
| E2E (Playwright + Chrome) | PASS | 16 | 16 | 0 | 0 | 모바일 14 + 데스크톱 2 |
| **합계** | **PASS** | **194** | **194** | **0** | **0** | |

테스트 케이스 명세 91건: **PASS 88 / FAIL 0 / BLOCKED 2 / NOT RUN 0 / 미구현 1**.
GitHub Actions 워크플로(`.github/workflows/ci.yml`)는 작성만 했고 **실행하지 않았다(NOT RUN)** — 이 환경에서 GitHub 러너를 실행할 수 없다. 같은 단계를 로컬 실행기(`scripts/test-all.sh`)로 실행했다.

## 2. 이번 작업 단계별 결과

| 단계 | 구현 | 새 테스트 | 결과 |
|---|---|---|---|
| 2A 중복 음성 분석 방지 | 녹음마다 `Idempotency-Key`(선택 헤더, 기존 API 호환). `speech_analyses.request_key/request_hash` + 유니크 `(student_id, request_key)`. 처리 중·완료면 같은 분석 반환(`reused:true`), 같은 키+다른 녹음 409, 실패 시 키 해제, 동시 요청은 유니크 제약으로 1건만 추론. 화면은 분석 중 버튼 차단, 재시도는 같은 키. CORS 허용 헤더 추가 | 단위 5, 통합 5(동시 4건 포함), CORS 1, 프런트 3, E2E 1(응답 유실 후 재시도) | PASS |
| 2B JWT 만료 검증 | 코드 변경 없음(기존 필터가 만료·변조 토큰을 401로 처리함을 확인) | 단위 4(만료·실제 만료 대기·변조·none·빈 값·설정 누락), 통합 2(401 규약, 공개 API 영향 없음, refresh 재발급) | PASS |
| 2C 민감 정보 로그 | 500 로그를 마스킹 요약으로 변경(`LogMasking`), 회원 예외 메시지 마스킹, Spring Boot 임시 사용자·생성 비밀번호 로그 제거(빈 UserDetailsService, httpBasic·formLogin 비활성) | 단위 3, 통합 2(실제 로그 캡처) | PASS |
| 3A 숙제 배정 모달 | 취소 버튼, 저장 실패 시 모달 안 서버 오류 표시, 목록에 없는 학생 미선택 처리. 서버 권한 검증은 기존(403) 유지·테스트 | 프런트 5, E2E 1 | PASS |
| 3B 메일 설정 누락 | 오류 코드 분리: `MAIL_NOT_CONFIGURED` / `MAIL_DELIVERY_FAILED`(503). 빈 MAIL_HOST를 설정됨으로 오판하던 결함 수정 | 통합 3(누락·발송 실패·Mailpit 실제 발송) | PASS |
| 3C 이전 기간 비교 | 서버가 `comparison`(일치율 변화 %p, 기록 수 변화, 숙제 완료율·변화)을 같은 기준으로 계산. 기록 없음·숙제 0건은 null. 화면은 서버 값 사용 | 단위 3, 통합 2(경계 시각·하루 조회·잘못된 기간) | PASS |
| 3D 데스크톱 레이아웃 | 코드 변경 없음(1280px에서 문제 미발견). 앱은 가운데 정렬 모바일 폭(max-w-md) 설계 | E2E 2(가로 넘침·화면 밖 요소·하단 메뉴·모달 1280×720) | PASS |
| 4 CI 구성 | `unitTest`/`integrationTest` 분리, 통합은 테스트 DB 없으면 원인 표시 후 실패(개발 DB 사용 금지), `REQUIRE_SPEECH_MODEL`/`REQUIRE_MAILPIT`이면 누락을 FAIL, `scripts/ci/setup-whisper.sh`(모델 SHA-256 검증), `scripts/test-all.sh`(단계별 PASS/FAIL/SKIPPED/BLOCKED/NOT RUN 요약), GitHub Actions 워크플로 | 실패 동작 3종 직접 확인(아래) | 로컬 PASS, Actions NOT RUN |
| 5 분석·보류 기준 문서 | `docs/speech/analysis-and-hold-criteria.md` | — | 문서화(아동 음성 검증은 미완료) |

CI 실패 동작 확인(실행 결과):
- 테스트 DB 연결 불가: `테스트 DB(127.0.0.1:3999)에 연결할 수 없습니다…`로 3초 만에 중단.
- 모델 없음(기본): LocalWhisper 테스트 10개 중 6개 SKIPPED, 실패 0.
- 모델 없음 + `REQUIRE_SPEECH_MODEL=true`: 6개 FAIL(원인 메시지 포함).
- 모델 파일 손상: `setup-whisper.sh`가 체크섬 불일치를 감지하고 다시 내려받아 검증.

## 3. 실패 내역과 조치

### 제품 결함

| ID | 증상 | 원인 | 수정 | 재검증 |
|---|---|---|---|---|
| DEF-03 | MAIL_HOST가 빈 값인데 메일 설정이 있는 것으로 판단해 기본 SMTP로 발송 시도 | Spring Boot가 빈 host로도 JavaMailSender를 만들고, 코드는 발송기 존재로 판단 | 설정값(host·from)으로 판단 | MailNotConfiguredIntegrationTest PASS |
| DEF-04 | (출시 전 발견) 새 `Idempotency-Key` 헤더가 CORS 허용 목록에 없어 브라우저가 분석 요청을 막을 상황 | 허용 헤더가 Authorization·Content-Type만 | 허용 헤더 추가 + 사전 요청 테스트 | CORS 테스트·E2E PASS |
| DEF-05 | 시작 로그에 Spring Boot 생성 비밀번호 출력, 500 로그에 예외 원문(이메일 등 입력값 포함 가능) | 기본 사용자 자동 구성, 예외 전체 출력 | 빈 UserDetailsService, 마스킹 요약 로그 | SensitiveLogIntegrationTest PASS |

(이전 주기 DEF-01·DEF-02는 수정 완료 상태 유지, 회귀 테스트 PASS.)

### 테스트 코드 오류(제품 결함 아님)

| 테스트 | 원인 | 조치 |
|---|---|---|
| MailDeliveryFailureIntegrationTest | 테스트 트랜잭션으로 감싸 서비스 롤백이 일어나지 않아 두 번째 요청이 재발송 제한에 걸림(202) | 트랜잭션 없이 실행하고 만든 계정을 직접 삭제(실제 롤백을 검증) |
| MailNotConfiguredIntegrationTest | "빈 MAIL_HOST면 발송기 없음"이라는 잘못된 가정 | 실제 동작(발송기 생성됨)을 확인하고 DEF-03으로 코드 수정 |
| MailpitDeliveryIntegrationTest | 메서드 이름이 공통 헬퍼(requireMailpit)와 충돌해 컴파일 오류 | 메서드 이름 변경 |
| teacher-homework.spec | 같은 문구 요소 2개로 선택자 모호 | 영역으로 범위 축소 |
| scripts/test-all.sh | macOS bash 3.2가 연관 배열 미지원 / CLI `--reporter`가 설정의 json 파일 출력을 덮어써 E2E를 BLOCKED로 오분류(실제 16/16 통과) | 일반 변수로 변경 / 설정의 리포터 사용 |

검증 조건을 없애거나 완화한 테스트는 없다.

## 4. 실행 명령

```bash
backend/scripts/test-db.sh start      # 테스트 DB 준비(개발 DB와 분리)
scripts/ci/setup-whisper.sh           # whisper.cpp·모델(체크섬 검증)
mailpit --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025 &
scripts/test-all.sh                   # 전체: unit → integration → frontend → e2e
REQUIRE=1 scripts/test-all.sh         # 모델·Mailpit 누락을 FAIL로
cd backend && sh gradlew unitTest     # 단계별 실행도 가능(integrationTest / test)
```

결과 요약은 `test-reports/summary.md`, 로그는 `test-reports/*.log`(git 제외)에 남는다.

## 5. 데이터 격리

- 모든 통합·E2E는 테스트 DB(`chatterland_test`)만 사용했다. 개발 DB(`chatterland`)와 `.env`는 변경하지 않았다.
- 통합 테스트는 롤백한다. 동시성·롤백 검증 테스트(SpeechIdempotency, MailDeliveryFailure)는 트랜잭션 없이 실행하고 만든 데이터를 직접 지운다(실행 후 해당 계정 0건 확인).
- E2E 계정(`e2e-*@example.test`)은 테스트 DB에 남으며 `backend/scripts/test-db.sh reset`으로 지운다.

## 6. 남은 미완료 항목과 제약

| ID | 항목 | 상태 | 사유·후속 조치 |
|---|---|---|---|
| ETC-01 | AI 대화 실제 응답 | BLOCKED | AI_API_URL/AI_API_KEY 없음 |
| SPE-26 | 실제 아동 음성 인식 정확도·판정 보류 기준 | BLOCKED | 적법한 동의·데이터 확보 전(검증 계획: `docs/speech/analysis-and-hold-criteria.md` 4절) |
| SPE-27 | 발음 점수 | 미구현 | 검증된 음소 모델 없음 |
| CI-01 | GitHub Actions 실행 | NOT RUN | 원격 러너에서 미실행. Linux whisper.cpp 소스 빌드 단계는 로컬(macOS)에서 검증하지 못함 |

## 7. 참고

- 판정 보류 기준값은 합성음·브라우저 가짜 마이크 녹음으로만 확인했다(아동 음성 미검증).
- 서버 중복 방지는 `Idempotency-Key`가 있을 때만 동작한다. 헤더 없이 호출하는 기존 클라이언트는 이전처럼 매번 새 분석을 만든다(하위 호환).

## 8. 재검증 (2026-10-02 22:48)

| 항목 | 방법 | 결과 |
|---|---|---|
| 전체 재실행 | 결과 폴더 삭제 후 `REQUIRE=1 scripts/test-all.sh`(필수 모드) | unit 70 / integration 53 / frontend 55 / e2e 16 모두 PASS, SKIPPED 0, 종료 코드 0 |
| 결과 파일 대조 | JUnit XML·vitest.json·e2e-results.json 집계와 선언된 @Test 메서드 대조 | 선언 123 = 실행 123(단위·통합 중복 없음), 프런트 55·E2E 16 일치 |
| 테스트 DB 연결 불가 | `TEST_DB_URL`을 닫힌 포트로 지정 | integration BLOCKED + 원인 메시지, 종료 코드 1 |
| Mailpit 없음(기본) | Mailpit 미실행 | integration PASS, 52 통과 + 1 SKIPPED(통과에 포함하지 않음) |
| Mailpit 없음(필수) | `REQUIRE=1` | integration FAIL(1건, 원인 메시지), 종료 코드 1 |
| 기존 테스트 약화 여부 | 기존 추적 테스트 파일의 삭제된 단언 검색 | 삭제된 단언 0건. 두 테스트의 입력 녹음을 앞뒤 무음이 있는 파일(`*-recorded.wav`)로 바꿈(새 보류 규칙에서 원본 합성음은 끝 무음이 없어 보류됨). 단언은 유지·추가 |
| Linux CI whisper 빌드 명령 | 같은 cmake 명령(v1.9.4, `BUILD_SHARED_LIBS=OFF`)을 macOS에서 실행 | 두 실행 파일 빌드·정적 링크, 인식·VAD 정상, 이 실행 파일로 음성 통합 테스트 10건 PASS(잘못된 경로 대조군은 503 FAIL). **Linux 자체는 미검증** |
| GitHub Actions | — | **NOT RUN**: `gh` 미설치, 변경 사항이 커밋·푸시되지 않아 원격 실행 불가 |

재검증 중 CI 설정 보강: whisper.cpp 정적 빌드(`-DBUILD_SHARED_LIBS=OFF`), 체크섬 도구 자동 선택(`sha256sum`/`shasum`), 워크플로·MariaDB 시간대 `Asia/Seoul`.

## 9. 코드 리뷰·품질 개선 주기 (2026-10-02 23:51, `REQUIRE=1 scripts/test-all.sh`)

| 단계 | 테스트 | PASS | FAIL | SKIPPED | 이번 주기 신규 |
|---|---|---|---|---|---|
| 백엔드 단위 | 77 | 77 | 0 | 0 | +7 (VAD 멈춤 1, PROCESSING 고착 5, 숙제 수정 검증 1) |
| 백엔드 통합 | 59 | 59 | 0 | 0 | +6 (PROCESSING 고착 5, 메일·커밋 일관성 1) / 기존 권한 테스트에 숙제 수정 공백 검증 추가 |
| 프런트 (Vitest) | 73 | 73 | 0 | 0 | +18 (동시 401·갱신 3, 복귀 경로 2, 로그인 3, 숙제 화면 6, 칩 1, 숙제 관리 빈 상태 2, 히스토리 빈 상태 1) |
| E2E | 20 | 20 | 0 | 0 | +4 (만료 후 복귀, 외부 복귀 경로 무시·로그인 화면 이동, 로그아웃 후 뒤로 가기, 320px 긴 텍스트) |
| **합계** | **229** | **229** | **0** | **0** | **+35** |

결함과 조치는 `code-review.md`에 정리했다(DEF-06~09 수정, R-1 조치, R-2 현행 유지·테스트).

재현 테스트를 먼저 작성해 실패를 확인한 결함: DEF-06(동시 401 시 로그아웃), DEF-08(VAD 멈춤 시 무한 대기). 수정 후 같은 테스트가 통과했다.

이번 주기의 테스트 코드 오류(제품 결함 아님): ① LoginPage 테스트의 모의 라우터가 렌더링마다 새 객체를 돌려줘 effect가 반복 실행됨 → 실제 Next 라우터처럼 고정 객체로 수정 ② E2E-16의 테스트 이메일 로컬 부분이 64자를 넘어 서버 검증(400)에 걸림 → 한도 안으로 수정.

데이터 정리: 트랜잭션 없이 실행한 테스트(중복 분석·고착 복구·메일 커밋)가 만든 계정은 실행 후 0건, 테스트 DB에 PROCESSING으로 남은 분석 0건.

## 10. 미해결 항목 처리 주기 (2026-10-03 00:37, `REQUIRE=1 scripts/test-all.sh`)

설계: `docs/design/concurrency-and-sessions.md`.

| 단계 | 테스트 | PASS | FAIL | SKIPPED | 이번 주기 신규 |
|---|---|---|---|---|---|
| 백엔드 단위 | 89 | 89 | 0 | 0 | +12 (토큰 필터 4, 토큰 버전 1, 숙제 버전 검증 1, 처리 시간 로그 3, 늦은 완료 1, 교착 상태 2) |
| 백엔드 통합 | 69 | 69 | 0 | 0 | +10 (숙제 중복·동시 수정 9, 재설정 후 토큰 무효화 1) |
| 프런트 (Vitest·lint·tsc·build) | 81 | 81 | 0 | 0 | +8 (useTeacherData 6, 요청 키 2) |
| E2E | 23 | 23 | 0 | 0 | +3 (E2E-17 응답 유실 재클릭, E2E-18 두 탭 수정 충돌, E2E-19 재설정 후 다른 기기 로그아웃) |
| **합계** | **262** | **262** | **0** | **0** | **+33** (기준선 229개 유지) |

같은 주기의 첫 전체 실행(00:25)은 **FAIL**이었다. 다음 두 문제를 고친 뒤 다시 실행했다.
- 통합 1건 실패: 고착 분석의 동시 재시도에서 500이 났다. 이것은 제품 결함 DEF-11(InnoDB 교착 상태)이다.
- 프런트 tsc·build 실패: 새 테스트의 `vi.spyOn` 타입 오류다. 테스트 코드 오류이며 제품 결함이 아니다.

수정 전 실패를 테스트로 확인한 결함:
- **DEF-10**: 숙제 동시 수정이 409가 아니라 500이 되었다. MariaDB 오류 1020 때문이다. 해당 메서드의 트랜잭션을 없애 수정했다.
- **DEF-11**: 고착 정리와 INSERT가 교착 상태에 빠지면 500이 되었다. 재확인·재시도로 수정했다. 수정 후 통합 테스트를 4회 반복했는데 교착 상태가 재발하지 않았으므로, 수정 경로는 예외를 주입한 단위 테스트로만 확인했다.

E2E 실행 전 8080 포트를 쓰던 백엔드를 종료했다. IntelliJ에서 00:09에 시작한 개발 DB(3306) 연결 서버였다. E2E는 기존 설정대로 테스트 DB로 새 백엔드를 띄워 실행했다.

데이터 정리: 트랜잭션 없이 실행한 테스트가 만든 `hw-*` 계정 0건, PROCESSING 분석 0건, 삭제된 선생님의 숙제 0건.

### 처리 시간 측정 (합성 데이터 — 운영 검증 아님)

`python3 backend/scripts/speech-timing-report.py --junit backend/build/test-results/integrationTest/*.xml`. 실제 whisper.cpp·VAD, 합성 음성, Apple M2, 동시 처리 1, `WHISPER_TIMEOUT` 60초.

| 구간 | n | p50 | p95 | p99 | 최대 |
|---|---|---|---|---|---|
| 인식 전체 | 22 | 3,672ms | 3,990ms | 3,999ms | 3,999ms |
| 슬롯 대기 | 22 | 0ms | 0ms | 1ms | 1ms |
| VAD | 22 | 240ms | 429ms | 684ms | 684ms |
| Whisper | 22 | 3,389ms | 3,700ms | 3,703ms | 3,703ms |
| 분석 전체(행 생성 → 최종 상태) | 27 | 3,625ms | 3,997ms | 4,004ms | 4,004ms |

- 결과 분포
  - 인식 OK 22건, 422(말소리 없음) 2건, 504·503 0건
  - 분석 COMPLETED 22건, FAILED:422 5건, LATE_AFTER_STALE 0건
- 고착 기준 300초 대비 최대값은 1.33%다.
- **완료 표본이 22건(<200)이고 테스트 환경이라, 이 수치로 기준 시간이 적절하다고 판단하지 않는다.** 운영 검증은 BLOCKED(SPE-32)다.

## 11. 2차 정책 구현 주기 (2026-10-03 01:17, `REQUIRE=1 scripts/test-all.sh`)

설계: `docs/design/concurrency-and-sessions.md` 7절. 케이스: `test-cases.md` POL-01~07.

| 단계 | 테스트 | PASS | FAIL | SKIPPED | 이번 주기 신규 |
|---|---|---|---|---|---|
| 백엔드 단위 | 89 | 89 | 0 | 0 | 0(기존 테스트를 새 계약에 맞게 수정: version 필수 검증, 필터 세션 확인) |
| 백엔드 통합 | 80 | 80 | 0 | 0 | +11 (로그아웃·비밀번호 변경·수명 7, 수명 만료→재발급 1, 삭제 버전·수정/삭제 경쟁·키 수명 3) |
| 프런트 (Vitest·lint·tsc·build) | 86 | 86 | 0 | 0 | +5 (비밀번호 변경 폼 3, 숙제 삭제 버전 3, 버전 없는 이전 동작 테스트 1건 삭제 — 계약 변경으로 해당 동작이 없어짐) |
| E2E | 26 | 26 | 0 | 0 | +3 (E2E-20 로그아웃 후 서버 차단, E2E-21 마이페이지 비밀번호 변경, E2E-22 삭제 버전 충돌) · E2E-17에 재전송 200 확인 추가 |
| **합계** | **281** | **281** | **0** | **0** | **+19** (기준선 262개 유지) |

같은 주기 01:10 실행은 **FAIL**(통합 1, E2E 1)이었다. 둘 다 환경 문제였고 코드를 고치지 않고 다시 실행해 통과했다.
- 통합 1건: Mailpit이 실행 중이 아니었다. 앞서 띄운 Mailpit이 실행 시간 제한으로 종료되었고, 필수 모드에서는 이를 실패로 처리한다. Mailpit을 다시 띄웠다.
- E2E-06 시간 초과: 180초 동안 Chrome 브라우저 컨텍스트(`browser.newContext`)를 만들지 못했다. 제품 코드에 도달하기 전에 멈췄으며, 같은 테스트가 01:17 실행에서는 통과했다.

계약 변경에 맞춰 수정한 기존 테스트(검증 약화 아님, 새 계약 반영):
- 숙제 수정·삭제 요청에 version을 추가했다(BackendApi·Authorization 통합).
- 재전송 기대 상태를 201에서 200으로 바꿨다.
- version 없는 수정은 200이 아니라 400을 기대하도록 바꿨다.
- 필터 단위 테스트를 세션 확인 인터페이스에 맞췄다.
- 비밀번호 로그 검사 테스트는 MockMvc의 실패 시 요청 덤프(테스트 도구 출력)를 끄고 애플리케이션 로그만 검사한다.

관찰: 통합 테스트도 백엔드의 `.env`를 읽는다(`spring.config.import`). 그 결과 적용된 access token 수명이 24시간이었다. 코드 기본값(30분)보다 로컬 `.env`가 우선한다는 뜻이다. `.env` 파일 자체는 읽지 않았다.

E2E 실행 전 IntelliJ에서 00:44에 다시 시작한 개발 백엔드(개발 DB 3306)를 8080 포트 확보를 위해 종료했다.

데이터 정리: 트랜잭션 없이 실행한 숙제 테스트 계정 0건, PROCESSING 분석 0건(세션·수명 테스트는 트랜잭션 롤백).

## 12. 비밀번호 변경 시도 제한·인증 기록 정리 (2026-10-03 08:04, `REQUIRE=1 scripts/test-all.sh`)

설계: `docs/design/concurrency-and-sessions.md` 7.6·7.7절. 케이스: POL-08, POL-09.

| 단계 | 테스트 | PASS | FAIL | SKIPPED | 신규 |
|---|---|---|---|---|---|
| 백엔드 단위 | 90 | 90 | 0 | 0 | +1 (정리 작업 1,000건 단위 반복) |
| 백엔드 통합 | 84 | 84 | 0 | 0 | +4 (시도 제한 2, 동시 시도 1, 정리 대상 1) |
| 프런트 (Vitest·lint·tsc·build) | 87 | 87 | 0 | 0 | +1 (429 안내) |
| E2E | 26 | 26 | 0 | 0 | 0 (E2E-21 안내 문구에 남은 시도 반영) |
| **합계** | **287** | **287** | **0** | **0** | **+6** (기준선 281개 유지) |

- 기존 테스트 변경: 현재 비밀번호 오류 문구에 "(남은 시도 N회)"가 붙었다. 이에 맞춰 문구 단언을 정확한 새 문구로 바꿨다(AuthSessionIntegrationTest, E2E-21).
- 데이터 정리: `maint-*` 계정 0건, `hw-*` 계정 0건, 사용자 없는 실패 기록 0건.

## 13. AI(Gemini) 연동·학습 피드백 (2026-10-03 08:46, `REQUIRE=1 scripts/test-all.sh`)

설계: `docs/design/ai-feedback.md`. 케이스: AI-01~AI-05.

| 단계 | 테스트 | PASS | FAIL | SKIPPED | 신규 |
|---|---|---|---|---|---|
| 백엔드 단위 | 101 | 101 | 0 | 0 | +11 (AI 호출 형식·오류 5, 피드백 규칙 6) |
| 백엔드 통합 | 87 | 87 | 0 | 0 | +3 (피드백 API 권한·동의·저장·AI 실패 격리) |
| 프런트 (Vitest·lint·tsc·build) | 92 | 92 | 0 | 0 | +5 (AI 설명 패널 상태) |
| E2E | 27 | 27 | 0 | 0 | +1 (E2E-23 실제 녹음·분석 후 AI 설명, 외부 AI 응답만 대체) |
| **합계** | **307** | **307** | **0** | **0** | **+20** (기준선 287개 유지) |

**실제 Gemini 호출(`LIVE_AI_TEST=1 … LiveAiIntegrationTest`, 3건)**: **FAIL 2 / PASS 1 → 실제 응답 생성은 BLOCKED.**
- 설정된 키: Gemini가 403 `PERMISSION_DENIED`("Your project has been denied access")로 거부했다. 서버는 `AI_AUTH_FAILED`로 안내했다. 원인은 Google 쪽 프로젝트 접근 차단이다(`ai-feedback.md` 6절).
- 잘못된 키 → `AI_AUTH_FAILED`(Gemini 400 `API_KEY_INVALID`), 1ms 시간 제한 → `AI_TIMEOUT`. 둘 다 기대대로였다.
- 이 테스트는 기본 실행에서 제외된다(외부 비용·의존). 위 합계에는 포함하지 않았다.

**구현 중 발견·수정**
- AI 호출 클래스에 생성자가 두 개여서 Spring이 고르지 못했고, 앱이 시작되지 않는 결함이 있었다. 기존 통합 테스트(ConsentAndCenter)에서 발견해 `@Autowired`로 수정했다.
- 이전에는 통합 테스트가 개발자 `.env`의 실제 AI 키를 읽었다. 이제 테스트 실행 시 AI 설정을 비운다.

**기존 테스트 변경(새 동작 반영, 검증 약화 아님)**
- ExternalProviderServiceTest: 키 거부 시 502 대신 503 `AI_AUTH_FAILED`를 기대하고, 메시지에 키가 없음을 추가로 확인한다.
- SpeechActivityScreen.test: 모의 API에 AI 설명 상태 조회 함수를 추가했다.

## 부록 A. 최종 실행 테스트 전체 목록

### 백엔드 단위 (unitTest, 70개)

| 클래스 | 테스트 | 결과 | 시간(초) |
|---|---|---|---|
| RequestValidationTest | homeworkRejectsPastDueDateAndNonPositiveMinutes | PASS | 0.12 |
| RequestValidationTest | attemptScoreMustBeAMeasured0to100ValueWhenPresent | PASS | 0.01 |
| RequestValidationTest | reviewAcceptsOnlyKnownJudgementsAndValidConfirmedErrors | PASS | 0.11 |
| RequestValidationTest | signupRequiresRoleNameEmailPasswordLengthAndCenter | PASS | 0.04 |
| AudioStorageServiceTest | storesAllowedAudioWithGeneratedName | PASS | 0.03 |
| AudioStorageServiceTest | rejectsUnsupportedMimeType | PASS | 0.02 |
| AudioStorageServiceTest | rejectsFileWhoseBytesDoNotMatchDeclaredAudioType | PASS | 0.00 |
| AudioStorageServiceTest | rejectsEmptyAudio | PASS | 0.00 |
| ExternalProviderServiceTest | doesNotPretendAiProviderIsAvailableWhenUnconfigured | PASS | 1.36 |
| ExternalProviderServiceTest | sendsOpenAiCompatibleChatRequestWithConfiguredUrlKeyAndModel | PASS | 1.13 |
| ExternalProviderServiceTest | doesNotPretendSpeechProviderIsAvailableWhenUnconfigured | PASS | 0.01 |
| ExternalProviderServiceTest | reportsProviderRejectionAsBadGateway | PASS | 0.03 |
| HangulPhonemeAnalyzerTest | recordsSubstitutionOmissionAndAdditionCandidates | PASS | 0.01 |
| HangulPhonemeAnalyzerTest | ignoresNonHangulCharactersInAlignment | PASS | 0.00 |
| HangulPhonemeAnalyzerTest | findsTargetPhonemeOnsetAndCodaPositions | PASS | 0.01 |
| LocalWhisperRecognitionServiceTest | vadKeepsShortWordRecognition | PASS | 5.96 |
| LocalWhisperRecognitionServiceTest | parsesVadSegmentsRelativeToOriginalAudio | PASS | 0.00 |
| LocalWhisperRecognitionServiceTest | vadIsDisabledWhenModelIsMissing | PASS | 0.00 |
| LocalWhisperRecognitionServiceTest | recognitionResultCarriesVadSegmentsAndAudioQuality | PASS | 5.45 |
| LocalWhisperRecognitionServiceTest | rejectsSilentRecordingBeforeInference | PASS | 0.01 |
| LocalWhisperRecognitionServiceTest | recognizesKoreanSentenceWithLocalModel | PASS | 5.27 |
| LocalWhisperRecognitionServiceTest | hallucinationPhrasesAreNotTreatedAsRecognizedSpeech | PASS | 0.01 |
| LocalWhisperRecognitionServiceTest | reportsUnavailableModelInsteadOfFakingResult | PASS | 0.00 |
| LocalWhisperRecognitionServiceTest | rejectsBackgroundNoiseWithoutSpeechUsingVad | PASS | 0.30 |
| LocalWhisperRecognitionServiceTest | recognizesShortWordWithoutHallucination | PASS | 5.08 |
| PeriodComparisonTest (신규) | emptyPeriodsGiveNullInsteadOfZeroOrDivisionByZero | PASS | 0.01 |
| PeriodComparisonTest (신규) | computesChangesWhenBothPeriodsHaveData | PASS | 0.00 |
| PeriodComparisonTest (신규) | zeroCompletedIsAZeroPercentRateNotMissing | PASS | 0.00 |
| ReportPdfGeneratorTest | embedsKoreanFontAndExtractsKoreanText | PASS | 1.56 |
| ReportPdfGeneratorTest | longContentFlowsOntoMultiplePagesWithPageNumbers | PASS | 0.42 |
| SpeechAnalysisServiceImplTest | hallucinatedOrBlankTranscriptIsRejected | PASS | 0.16 |
| SpeechAnalysisServiceImplTest | databaseSaveFailureMarksAnalysisFailedAndDeletesAudio | PASS | 0.10 |
| SpeechAnalysisServiceImplTest | requestsWithoutKeyAreNotDeduplicatedAndInvalidKeysAreRejected | PASS | 0.03 |
| SpeechAnalysisServiceImplTest | therapyLearnerKeepsAudioForReviewAndStoresNoTextMatchRate | PASS | 0.02 |
| SpeechAnalysisServiceImplTest | losingAConcurrentInsertReturnsTheWinnerAndDeletesItsOwnAudio | PASS | 0.02 |
| SpeechAnalysisServiceImplTest | recognitionFailureIsStoredAsFailedWithoutResult | PASS | 0.01 |
| SpeechAnalysisServiceImplTest | analysisIsRecordedAsProcessingBeforeInference | PASS | 0.04 |
| SpeechAnalysisServiceImplTest | sameKeyReturnsTheExistingAnalysisWithoutStoringOrInferringAgain | PASS | 0.00 |
| SpeechAnalysisServiceImplTest | generalLearnerStoresTextMatchRateAndAssessmentWithoutScoreAndDeletesAudio | PASS | 0.03 |
| SpeechAnalysisServiceImplTest | sameKeyWithDifferentRecordingOrItemIsRejected | PASS | 0.01 |
| SpeechAnalysisServiceImplTest | retryAfterFailureWithTheSameKeyRunsANewAnalysis | PASS | 0.02 |
| SpeechAssessmentEvaluatorTest | timingIsUnavailableWithoutVad | PASS | 0.00 |
| SpeechAssessmentEvaluatorTest | repetitionFindsRecurringCandidates | PASS | 0.01 |
| SpeechAssessmentEvaluatorTest | uncertainRecordingsAreHeld | PASS | 0.00 |
| SpeechAssessmentEvaluatorTest | sentenceTimingCountsPauses | PASS | 0.00 |
| SpeechAssessmentEvaluatorTest | cleanWordHasNoHoldAndMeasuresTiming | PASS | 0.00 |
| StudentServiceImplTest | countsFromYesterdayWhenThereIsNoActivityToday | PASS | 0.01 |
| StudentServiceImplTest | breaksStreakAtFirstMissingDayAndReturnsZeroForOldActivity | PASS | 0.00 |
| StudentServiceImplTest | countsConsecutiveActivityThroughToday | PASS | 0.00 |
| TranscriptComparatorTest | identicalSentenceIgnoringPunctuationAndSpacingMatchesFully | PASS | 0.00 |
| TranscriptComparatorTest | matchRateIsSyllableBasedAndNeverNegative | PASS | 0.00 |
| TranscriptComparatorTest | reportsSubstitutedMissingAndInsertedWords | PASS | 0.01 |
| WavAudioTest | rejectsNonWavContent | PASS | 0.01 |
| WavAudioTest | detectsSilence | PASS | 0.01 |
| WavAudioTest | readsPcmWavWithExtraChunksAndPadsSilence | PASS | 0.00 |
| WhisperProcessFailureTest | missingExecutableOrModelIsServiceUnavailable | PASS | 0.01 |
| WhisperProcessFailureTest | nonZeroExitIsReportedAsBadGateway | PASS | 0.33 |
| WhisperProcessFailureTest | hangingProcessTimesOutAsGatewayTimeout | PASS | 0.82 |
| WhisperProcessFailureTest | validResultIsParsedWithoutVadTiming | PASS | 0.24 |
| WhisperProcessFailureTest | tooShortTooLongAndSilentRecordingsAreRejectedBeforeInference | PASS | 0.01 |
| WhisperProcessFailureTest | missingOrCorruptResultFileIsNotTreatedAsRecognizedSpeech | PASS | 0.78 |
| LogMaskingTest (신규) | leavesOrdinaryMessagesAndNullsAlone | PASS | 0.00 |
| LogMaskingTest (신규) | describeMasksTheWholeCauseChainAndKeepsAppFrames | PASS | 0.00 |
| LogMaskingTest (신규) | masksEmailsTokensSecretsAndPhones | PASS | 0.00 |
| JwtUtilTest | refusesToStartWithoutSecretOrWithNonPositiveExpiration | PASS | 0.00 |
| JwtUtilTest | rejectsTokenSignedWithAnotherKey | PASS | 0.21 |
| JwtUtilTest | parsesUserAndRoleFromSignedToken | PASS | 0.01 |
| JwtUtilTest | tokenIssuedByThisUtilExpiresAfterTheConfiguredTime | PASS | 1.12 |
| JwtUtilTest | rejectsExpiredToken | PASS | 0.00 |
| JwtUtilTest | rejectsTamperedPayloadAndUnsignedToken | PASS | 0.01 |

### 백엔드 통합 (integrationTest, 53개)

| 클래스 | 테스트 | 결과 | 시간(초) |
|---|---|---|---|
| AccountRecoveryIntegrationTest | expiredCodeAndTooManyAttemptsAreRejected | PASS | 2.02 |
| AccountRecoveryIntegrationTest | unknownEmailAndResendCooldownGiveSameResponseWithoutExtraMail | PASS | 0.26 |
| AccountRecoveryIntegrationTest | findIdReturnsOnlyMaskedEmailsAndRejectsMismatchOrInvalidCenter | PASS | 0.28 |
| AccountRecoveryIntegrationTest | passwordResetFullFlowWithSingleUseCodeAndTokenRevokesSessions | PASS | 1.16 |
| AnalyticsPeriodIntegrationTest (신규) | previousPeriodUsesTheSameLengthAndBoundariesAreNotDoubleCounted | PASS | 0.80 |
| AnalyticsPeriodIntegrationTest (신규) | emptyPreviousPeriodAndSingleDayRange | PASS | 0.59 |
| AudioRetentionIntegrationTest | failedDeletionIsRecordedAndRetriedAndPathsOutsideStorageAreNeverDeleted | PASS | 0.05 |
| AudioRetentionIntegrationTest | deletesExpiredFilesKeepsUnexpiredAndKeepsAnalysisRecords | PASS | 0.02 |
| AudioRetentionIntegrationTest | deleteAllForStudentRemovesEveryStoredRecording | PASS | 0.01 |
| AuthorizationIntegrationTest | malformedRequestsAreRejectedWithClientErrors | PASS | 0.88 |
| AuthorizationIntegrationTest | protectedApisRequireAuthenticationAndRejectForgedTokens | PASS | 0.02 |
| AuthorizationIntegrationTest | teacherCanOnlyAccessLinkedStudents | PASS | 1.15 |
| AuthorizationIntegrationTest | wrongPasswordAndUnknownAccountGetTheSameFailure | PASS | 0.57 |
| AuthorizationIntegrationTest | logoutRevokesTheRefreshToken | PASS | 0.60 |
| AuthorizationIntegrationTest | studentCannotCallTeacherApisAndTeacherCannotCallStudentApis | PASS | 0.79 |
| AuthorizationIntegrationTest | studentCannotReadOrUseAnotherStudentsAnalysisOrHomework | PASS | 1.18 |
| AuthorizationIntegrationTest | responsesDoNotExposePasswordHashesOrTokensOfOthers | PASS | 0.56 |
| AuthorizationIntegrationTest | corsPreflightAllowsTheIdempotencyKeyHeaderForTheWebOrigin | PASS | 0.01 |
| BackendApiIntegrationTest | studentCanViewAssignedHomeworkAndMarkItComplete | PASS | 0.80 |
| BackendApiIntegrationTest | practiceAttemptIsBoundToItsOwnedCompletedAnalysisAndRetriesAreIdempotent | PASS | 0.45 |
| BackendApiIntegrationTest | teacherCanManageAssignedStudentHomeworkAndAnalytics | PASS | 0.46 |
| BackendApiIntegrationTest | exerciseItemSeedIsNotDuplicatedOnRestart | PASS | 0.01 |
| BackendApiIntegrationTest | refreshTokenCanOnlyBeRotatedOnce | PASS | 0.39 |
| BackendApiIntegrationTest | studentSignupAuthenticatesAndReadsDatabasePracticeCatalog | PASS | 0.39 |
| BackendApiIntegrationTest | teacherCannotReadStudentAssignedToAnotherTeacher | PASS | 0.97 |
| BackendApplicationTests | audioRetentionPurgeIsRegisteredAsScheduledTask | PASS | 0.01 |
| BackendApplicationTests | contextLoads | PASS | 0.00 |
| ConsentAndCenterIntegrationTest | centersApiListsOnlyActiveCentersAndSignupValidatesCenter | PASS | 0.05 |
| ConsentAndCenterIntegrationTest | signupRequiresPrivacyConsentCurrentVersionAndGuardianForChildren | PASS | 0.51 |
| ConsentAndCenterIntegrationTest | consentRecordsAreOnlyVisibleToTheirOwner | PASS | 0.56 |
| ConsentAndCenterIntegrationTest | speechAndAiChatAreBlockedOnServerWithoutConsentAndWithdrawalDeletesAudio | PASS | 0.45 |
| JwtAuthenticationIntegrationTest (신규) | expiredTamperedForeignAndMissingTokensGet401WhileValidTokenWorks | PASS | 0.40 |
| JwtAuthenticationIntegrationTest (신규) | expiredAccessTokenCanBeReplacedThroughRefresh | PASS | 0.34 |
| MailDeliveryFailureIntegrationTest (신규) | unreachableSmtpReturnsDeliveryFailedAndLeavesNoPendingCode | PASS | 0.28 |
| MailNotConfiguredIntegrationTest (신규) | serverStartsWithoutMailAndResetReturnsMailNotConfigured | PASS | 0.25 |
| MailpitDeliveryIntegrationTest (신규) | resetCodeIsDeliveredThroughSmtp | PASS | 0.34 |
| SensitiveLogIntegrationTest (신규) | noGeneratedSecurityPasswordUserExists | PASS | 0.01 |
| SensitiveLogIntegrationTest (신규) | authenticationFlowsAndUnexpectedErrorsDoNotLeakSecretsToLogs(CapturedOutput) | PASS | 0.60 |
| SpeechAnalysisIntegrationTest | therapyLearnerIsNotScoredAndRequiresTeacherReview | PASS | 7.97 |
| SpeechAnalysisIntegrationTest | generalLearnerGetsSentenceMatchWithoutPronunciationScore | PASS | 6.03 |
| SpeechAnalysisIntegrationTest | teacherCanReviseConfirmedErrorsWhileAutomaticAssessmentStaysIntact | PASS | 6.18 |
| SpeechAnalysisIntegrationTest | cutOffRecordingIsHeldInsteadOfJudged | PASS | 5.86 |
| SpeechAnalysisIntegrationTest | silentOrInvalidRecordingFailsWithoutStoringResult | PASS | 0.37 |
| SpeechAnalysisIntegrationTest | noiseAndNonSpeechAreRejectedWithoutResult | PASS | 0.89 |
| SpeechAnalysisIntegrationTest | wordAnalysisRecordsTargetPhonemePositionsAndRepetition | PASS | 11.10 |
| SpeechAnalysisIntegrationTest | sentenceOmissionAndAdditionProduceSyllableCandidates | PASS | 11.31 |
| SpeechAnalysisIntegrationTest | mispronouncedWordProducesUnconfirmedCandidatesNotAScore | PASS | 11.20 |
| SpeechAnalysisIntegrationTest | reportStatisticsMatchStoredAttemptsAndDuplicateSaveIsNotCounted | PASS | 17.51 |
| SpeechIdempotencyIntegrationTest (신규) | retryWithTheSameKeyReturnsTheSameAnalysisAndSavesOneAttempt | PASS | 5.83 |
| SpeechIdempotencyIntegrationTest (신규) | differentRecordingsOrNoKeyAreSeparateAnalyses | PASS | 22.14 |
| SpeechIdempotencyIntegrationTest (신규) | concurrentRequestsWithTheSameKeyRunOneAnalysis | PASS | 5.99 |
| SpeechIdempotencyIntegrationTest (신규) | reusingAKeyForAnotherRecordingIsRejected | PASS | 5.71 |
| SpeechIdempotencyIntegrationTest (신규) | failedAnalysisReleasesTheKeySoTheSameRecordingCanBeRetried | PASS | 0.44 |

### 프런트엔드 (Vitest, 55개)

| 파일 | 테스트 | 결과 |
|---|---|---|
| src/shared/components/components.test.tsx | Button > fires click and blocks duplicate submit while loading | PASS |
| src/shared/components/components.test.tsx | Input / Select > links label, error message and aria-invalid | PASS |
| src/shared/components/components.test.tsx | Input / Select > renders options with placeholder and reports changes | PASS |
| src/shared/components/components.test.tsx | Tabs > supports click and arrow-key navigation | PASS |
| src/shared/components/components.test.tsx | Modal / ConfirmDialog > closes with Escape unless closing is disabled | PASS |
| src/shared/components/components.test.tsx | Modal / ConfirmDialog > confirms once and disables both buttons while pending | PASS |
| src/shared/components/components.test.tsx | ErrorState > shows the message as an alert with retry | PASS |
| src/features/consent/consentValidation.test.ts | signup consent rules > requires privacy consent, and guardian fields only when a guardian is needed | PASS |
| src/features/consent/consentValidation.test.ts | signup consent rules > asks guardian consent for students under 14 or with unknown age | PASS |
| src/shared/api/client.test.ts | apiClient > sends the bearer token and parses JSON | PASS |
| src/shared/api/client.test.ts | apiClient > turns server error bodies into ApiError with status and code | PASS |
| src/shared/api/client.test.ts | apiClient > reports network failures as NETWORK_ERROR | PASS |
| src/shared/api/client.test.ts | apiClient > refreshes once on 401 and retries with the new token | PASS |
| src/shared/api/client.test.ts | apiClient > clears tokens and emits session-expired when refresh fails | PASS |
| src/shared/api/client.test.ts | apiClient > does not treat a failed login (public auth path) as an expired session | PASS |
| src/shared/api/client.test.ts | apiClient > returns audio and PDF responses as Blob, and 204 as undefined | PASS |
| src/shared/api/client.test.ts | helpers > builds query strings without empty values | PASS |
| src/shared/api/client.test.ts | helpers > uses fallback text for unknown errors | PASS |
| src/features/auth/pages/LoginPage.test.tsx | LoginPage > shows field errors and does not call the API for invalid input | PASS |
| src/features/auth/pages/LoginPage.test.tsx | LoginPage > stores tokens and routes a student to the student home | PASS |
| src/features/auth/pages/LoginPage.test.tsx | LoginPage > rejects a role mismatch and revokes the issued refresh token | PASS |
| src/features/auth/pages/LoginPage.test.tsx | LoginPage > shows the server error message (wrong password / unknown account) | PASS |
| src/features/auth/pages/LoginPage.test.tsx | LoginPage > ignores a second submit while the first login is in flight | PASS |
| src/features/auth/pages/LoginPage.test.tsx | LoginPage > explains an expired session | PASS |
| src/features/auth/utils/validation.test.ts | validateEmail > rejects blank and malformed addresses | PASS |
| src/features/auth/utils/validation.test.ts | validateEmail > accepts a valid address with surrounding spaces | PASS |
| src/features/auth/utils/validation.test.ts | validatePassword (server rule 8~72 chars) > enforces boundaries | PASS |
| src/features/auth/utils/validation.test.ts | validateName > requires 1~80 chars | PASS |
| src/features/student/components/SpeechActivityScreen.test.tsx | SpeechActivityScreen 중복 분석 방지 > 분석 중에는 버튼을 막아 두 번 눌러도 요청이 한 번만 간다 | PASS |
| src/features/student/components/SpeechActivityScreen.test.tsx | SpeechActivityScreen 중복 분석 방지 > 실패 후 다시 분석하기는 같은 녹음의 같은 요청 키로 보낸다 | PASS |
| src/features/student/components/SpeechActivityScreen.test.tsx | SpeechActivityScreen 중복 분석 방지 > 새로 녹음하면 새 요청 키를 쓴다 | PASS |
| src/features/student/components/SpeechAnalysisResult.test.tsx | SpeechAnalysisResult > shows text match rate, word type and leaves pronunciation metrics as 미평가 | PASS |
| src/features/student/components/SpeechAnalysisResult.test.tsx | SpeechAnalysisResult > distinguishes a held result from a normal result | PASS |
| src/features/student/components/SpeechAnalysisResult.test.tsx | SpeechAnalysisResult > therapy learners see teacher review pending, no text match rate, and hold guidance | PASS |
| src/features/student/components/SpeechAnalysisResult.test.tsx | SpeechAnalysisResult > shows only provider values for the external engine and never invents scores | PASS |
| src/features/student/hooks/useAudioRecorder.test.ts | useAudioRecorder > reports unsupported browsers without starting | PASS |
| src/features/student/hooks/useAudioRecorder.test.ts | useAudioRecorder > explains a denied microphone permission and allows retry | PASS |
| src/features/student/hooks/useAudioRecorder.test.ts | useAudioRecorder > explains a missing microphone | PASS |
| src/features/student/hooks/useAudioRecorder.test.ts | useAudioRecorder > records, stops into a previewable blob, and resets for re-recording | PASS |
| src/features/student/utils/mappers.test.ts | student API mappers > maps exercise items from server rows without inventing values | PASS |
| src/features/student/utils/mappers.test.ts | student API mappers > keeps missing scores as null (no fallback to other values) | PASS |
| src/features/student/utils/mappers.test.ts | student API mappers > converts numeric-like values safely | PASS |
| src/features/student/utils/mappers.test.ts | student API mappers > maps homework and detects overdue only when not done | PASS |
| src/features/student/utils/speechAssessment.test.ts | speech assessment display helpers > reads textMatchRate and falls back to legacy matchRate only for the same measurement | PASS |
| src/features/student/utils/speechAssessment.test.ts | speech assessment display helpers > uses analysisType from the server, with a word-count fallback | PASS |
| src/features/student/utils/speechAssessment.test.ts | speech assessment display helpers > describes candidates as unconfirmed candidates with position | PASS |
| src/features/student/utils/speechAssessment.test.ts | speech assessment display helpers > formats target phoneme positions and has labels for every server hold reason | PASS |
| src/features/teacher/components/HomeworkModal.test.tsx | HomeworkModal (교사 숙제 배정) > 미리 선택한 학생으로 배정하고 저장되면 닫는다 | PASS |
| src/features/teacher/components/HomeworkModal.test.tsx | HomeworkModal (교사 숙제 배정) > 필수값·범위를 검증하고 API를 호출하지 않는다 | PASS |
| src/features/teacher/components/HomeworkModal.test.tsx | HomeworkModal (교사 숙제 배정) > 저장 중에는 중복 제출을 막고, 실패하면 모달을 닫지 않고 서버 오류를 보여준다 | PASS |
| src/features/teacher/components/HomeworkModal.test.tsx | HomeworkModal (교사 숙제 배정) > 취소 버튼과 Escape로 닫고, 학생이 없으면 등록할 수 없다 | PASS |
| src/features/teacher/components/HomeworkModal.test.tsx | HomeworkModal (교사 숙제 배정) > 수정 모드에서는 학생을 바꿀 수 없고 변경 내용만 보낸다 | PASS |
| src/features/teacher/components/SpeechReviewPanel.test.tsx | SpeechReviewPanel > shows automatic evidence as unconfirmed candidates, separate from the teacher result | PASS |
| src/features/teacher/components/SpeechReviewPanel.test.tsx | SpeechReviewPanel > requires a judgement and sends only the errors the teacher confirmed (candidate + manual) | PASS |
| src/features/teacher/components/SpeechReviewPanel.test.tsx | SpeechReviewPanel > shows an error state with retry when loading fails | PASS |

### E2E (Playwright, 16개)

| 파일 | 테스트 | 결과 |
|---|---|---|
| auth-and-homework.spec.ts | E2E-01 학생 로그인 → 홈 → 오늘의 숙제 → 학습 화면 이동 → 숙제 완료 | PASS |
| auth-and-homework.spec.ts | E2E-10 로그아웃 → 보호된 화면 재접근 시 로그인 요구 | PASS |
| auth-and-homework.spec.ts | AUTH-E2E 로그인 실패·역할 불일치·회원가입 검증 | PASS |
| auth-and-homework.spec.ts | AUTH-E2E 마이크 권한 거부 → 안내 후 다시 시도 가능 | PASS |
| auth-and-homework.spec.ts | AUTH-E2E 아이디 찾기 → 비밀번호 재설정(실제 메일) → 새 비밀번호 로그인 | PASS |
| desktop-layout.spec.ts | DESK-01 학생 주요 화면(1280px): 가로 넘침 없음, 하단 메뉴가 콘텐츠를 가리지 않음 | PASS |
| desktop-layout.spec.ts | DESK-02 선생님 주요 화면·모달(1280×720): 가로 넘침 없음, 모달이 화면 안에 들어오고 저장 버튼에 닿을 수 있음 | PASS |
| student-sentence.spec.ts | E2E-04 문장 녹음 → 문장 유형 결과 | PASS |
| student-sentence.spec.ts | E2E-05 말하는 도중 녹음 종료 → 판정 보류 → 다시 녹음 | PASS |
| student-word.spec.ts | E2E-02/03 낱말 녹음 → 분석 결과 → 학습 기록 저장 → 새로고침 후 유지 | PASS |
| student-word.spec.ts | E2E-08 분석 서버 오류 → 안내 → 다시 분석하기로 복구 | PASS |
| student-word.spec.ts | E2E-09 학생 학습 기록 → 선생님 분석 화면 수치가 저장 데이터와 일치 | PASS |
| student-word.spec.ts | E2E-11 분석은 끝났지만 응답이 유실됨 → 다시 분석하기 → 같은 분석 재사용(중복 분석 없음) | PASS |
| teacher-homework.spec.ts | E2E-12 교사 숙제 배정 모달: 취소 → 검증 → 저장 실패 안내 → 배정 → 학생 화면 반영 | PASS |
| therapy-review.spec.ts | E2E-06 언어재활 녹음 → 자동 오류 후보 → 선생님 확정 → 새로고침 후 유지 | PASS |
| therapy-review.spec.ts | E2E-07 담당이 아닌 선생님은 다른 선생님의 학생 결과에 접근할 수 없다 | PASS |
