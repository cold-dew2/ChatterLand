# 코드 리뷰 및 품질 개선 결과

작성일: 2026-10-03 · 범위: 프런트엔드(Next.js) 전체 화면·공통 API 처리, 백엔드(Spring Boot) Controller~Mapper·예외·인증·트랜잭션·외부 프로세스
제외: GitHub Actions 실행, 외부 AI API 실제 연동(키 없음 — BLOCKED 유지)

## 1. 분석 결과 분류

| 분류 | ID | 내용 | 위치 | 영향 화면·API | 재현·근거 | 조치 | 우선순위 |
|---|---|---|---|---|---|---|---|
| 실제 결함 | DEF-06 | 여러 요청이 동시에 401을 받으면 각자 토큰 갱신 → 서버는 refresh 토큰을 한 번만 회전시키므로 두 번째 갱신이 실패하고 `clearAuth`가 방금 받은 토큰까지 지워 로그아웃됨 | `frontend/src/shared/api/client.ts` `request` | 모든 보호 화면(홈처럼 여러 API를 동시에 부르는 화면) | Vitest 재현 테스트 실패 → 수정 후 통과 | 진행 중인 갱신 하나를 공유(single-flight), 이미 바뀐 토큰이면 갱신 없이 재시도, 갱신 중 네트워크 오류는 로그아웃이 아니라 `NETWORK_ERROR` | 높음 |
| 실제 결함 | DEF-08 | VAD 프로세스가 멈추면 표준출력을 끝까지 읽느라 시간 제한 검사 전에 영원히 대기 → 분석 슬롯(기본 1개) 점유로 이후 모든 음성 분석이 멈춤, 분석은 PROCESSING에 고착 | `LocalWhisperRecognitionService.detectSpeechSegments` | `POST /speech/analyze`, AI 음성 메시지 | 멈추는 대체 VAD로 재현(10초 초과) → 수정 후 0.8초 제한 안에 VAD 없이 인식 계속 | 출력을 파일로 받고 `waitFor(timeout)` 후 읽기, 초과 시 강제 종료 | 높음 |
| 실제 결함 | DEF-07 | 숙제 수정 API가 공백만 있는 제목·유형을 저장(생성은 `@NotBlank`로 거부) | `HomeworkUpdateRequest` | `PATCH /teachers/me/homeworks/{id}` | 통합 테스트로 확인 | 보낸 값만 "공백 아님" 검증(부분 수정·미전송 값은 그대로) → 400 | 중간 |
| 실제 결함 | DEF-09 | 긴 학생 이름이 숙제 화면의 학생 필터 칩을 약 1000px로 늘림(320px 화면) | `shared/components/tabs/Tabs.tsx` chip | 교사 숙제 관리 | E2E-16 레이아웃 검사 실패 → 수정 후 통과 | 칩 최대 폭 12rem + 말줄임, 전체 이름은 title·접근 이름으로 유지 | 낮음 |
| 잠재적 위험 | R-1 | 서버 종료·비정상 종료 시 PROCESSING 고착 → 같은 녹음을 같은 키로 다시 분석할 수 없음 | `SpeechAnalysisServiceImpl` | `POST /speech/analyze` | 복구 코드 없음(코드 확인) | 기준 시간(기본 5분, 최소 3×WHISPER_TIMEOUT+60초로 자동 보정) 초과 시 조건부 실패 전환(`STALE_PROCESSING`)·키·녹음 정리. 같은 키 재요청 시 즉시, 그 외 5분마다 정리 작업 | 높음 → 조치 |
| 잠재적 위험 | R-2 | 메일 발송 성공 후 DB 커밋 실패 시 메일로 나간 코드가 DB에 없음 | `AccountRecoveryServiceImpl.requestPasswordReset` | `POST /auth/password-reset/request` | 커밋 직전 실패를 주입한 통합 테스트로 재현 | **현행 유지**(아래 3절) + 회귀 테스트 | 중간 → 분석·테스트 |
| UI 개선 | UI-1 | 세션 만료 후 원래 화면으로 돌아오지 못함, 로그인한 사용자가 로그인 화면에 머묾 | `RoleLayout`, `LoginPage`, `app/login/page.tsx` | 로그인·보호 화면 | — | 만료 시 `/login?expired=1&next=…`, 로그인 후 검증된 경로로 복귀(같은 역할 화면의 상대 경로만 허용, 외부·`//`·역슬래시 거부), 토큰이 있으면 로그인 화면에서 확인 후 이동 | 중간 |
| UI 개선 | UI-2 | 숙제 완료 시 선생님이 삭제한 숙제(404)가 목록에 계속 남음 | `StudentHomeworkScreen` | 학생 숙제하기 | 컴포넌트 테스트 | 안내 후 목록을 서버 기준으로 다시 불러옴 | 낮음 |
| UI 개선 | UI-3 | 필터를 고른 빈 목록과 데이터가 아예 없는 상태의 안내가 같음 | `StudentHistoryScreen`, `HomeworkView` | 학생 히스토리, 교사 숙제 관리 | 컴포넌트 테스트 | 필터별 문구·버튼 | 낮음 |
| 테스트 부족 | T-1 | 동시 401, 로그아웃 후 뒤로 가기, 만료 후 복귀, 320px 긴 텍스트, 숙제 화면 상태, 메일 커밋 불일치, PROCESSING 고착 | — | — | — | 테스트 추가(4절) | — |
| 이미 구현됨 | — | 화면별 오래된 응답 차단(`active` 플래그), 인증 확인 전 보호 화면 숨김, 로그인·모달·숙제 완료·분석 버튼 중복 제출 방지, 숙제 완료 서버 멱등(조건부 UPDATE), 분석 Idempotency-Key, 담당 범위·역할 서버 검증, 4xx/5xx 응답 규약, 이메일 소문자 저장·중복 409, 비밀번호 규칙 8~72자 앞뒤 일치, 리포트·분석 재시도, 메일 미설정/실패 오류 코드, PDF 한글·여러 쪽 | 코드·기존 테스트 | — | — | 수정하지 않음 | — |
| 외부 의존성 | ETC-01 | AI 대화 실제 응답 | — | AI 대화 | API 키 없음 | BLOCKED 유지 | — |

## 2. PROCESSING 고착 복구(R-1) 설계

- 생성: `insertAnalysis`(status=PROCESSING) → 완료 `completeLocalAnalysis`·실패 `failAnalysis`(둘 다 `status='PROCESSING'`일 때만 변경).
- 정상 처리의 최대 시간: 분석 슬롯 대기·VAD·Whisper가 각각 최대 `WHISPER_TIMEOUT`(기본 60초) → 180초. 로컬 실측은 요청당 3~6초.
- 기준값: `SPEECH_STALE_PROCESSING_AFTER`(기본 5분). 3×타임아웃+60초보다 짧게 설정하면 그 값으로 올린다(정상 처리 중 분석을 오판하지 않기 위함).
- 정리: `failStaleAnalysis`는 "그 분석 ID이고, 아직 PROCESSING이고, 기준 시간을 넘김"일 때만 FAILED(`STALE_PROCESSING`)·요청 키 해제 → 다른 요청의 키를 해제하거나, 그 사이 완료된 결과를 덮어쓰지 않는다. 전환에 성공한 경우에만 녹음 파일을 지운다.
- 원래 요청이 늦게 끝나도 완료·실패 UPDATE가 0건이 되어 정리 결과를 덮어쓰지 않는다(테스트로 확인).
- 비교한 대안: (a) 시작 시 모든 PROCESSING 정리 — 여러 서버 인스턴스에서 다른 인스턴스의 진행 중 분석을 지울 위험 → 기준 시간 방식 채택. (b) 같은 행을 재시도로 재사용 — 결과 덮어쓰기·이중 추론 위험 → 새 분석 행 생성 채택.

## 3. 메일 발송과 DB 커밋(R-2) 검토

| 방식 | 발송 실패 | 발송 성공 후 커밋 실패 |
|---|---|---|
| **현행: 트랜잭션 안에서 발송** | 전부 롤백 — 기존 코드 유효, 재발송 제한에 걸리지 않음 | 메일로 나간 코드는 DB에 없어 거부됨(가짜 성공 없음), 기존 코드는 유효, 바로 다시 요청 가능 |
| 커밋 후 발송 | 기존 코드는 이미 무효, 새 요청은 저장됐지만 메일 없음, 1분 재발송 제한 → 사용자가 코드를 받을 수 없음 | 발생하지 않음 |
| 아웃박스(발송 대기 테이블+작업) | 처리 가능 | 처리 가능, 단 새 테이블·작업자 필요(큰 변경) |

결론: 현행 유지. 최악의 경우 "받은 코드가 맞지 않음 → 다시 요청" 한 번이며 보안 영향이 없다. `MailCommitConsistencyIntegrationTest`가 두 경우(발송 실패, 커밋 직전 실패 주입)를 모의 메일 서비스로 검증한다(실제 SMTP 검증은 `MailpitDeliveryIntegrationTest`).

## 4. 남은 위험과 후속 작업

| 항목 | 상태 | 우선순위 | 선행 조건 | 예상 변경 | 필요한 테스트 |
|---|---|---|---|---|---|
| AI 대화 실제 응답 | BLOCKED | 중 | AI API 키 | 설정만(코드 변경 없음 예상) | AiChatScreen E2E |
| 실제 아동 음성 정확도·판정 보류 기준 | BLOCKED | 높음 | 동의·데이터(`docs/speech/analysis-and-hold-criteria.md` 4절) | 기준값·분석 버전 | 라벨링 데이터 평가 |
| 검증된 음소 평가 모델 | 미구현 | 중 | 모델 선정·검증 | 새 분석 엔진 | 모델 검증 |
| 숙제 생성 서버 중복 방지 | 수정 완료(2026-10-03) | — | — | Idempotency-Key·유니크 제약(`docs/design/concurrency-and-sessions.md` 1절) | TCH-15 PASS |
| 숙제 수정 동시 변경(낙관적 잠금) | 수정 완료(2026-10-03, version 선택 전송) | 낮음 | version 필수화 여부 정책 결정 | `homeworks.version`·409 VERSION_CONFLICT, DEF-10 수정 | TCH-16·17 PASS |
| 비밀번호 재설정 후 기존 access 토큰 | 수정 완료(2026-10-03, token_version) | 낮음 | 로그아웃 시 access 무효화 여부 등은 정책 결정(설계 문서 5절) | JWT 필터 | AUTH-23·24 PASS |
| PROCESSING 기준 시간 운영 검증 | 측정 도구 추가, 운영 검증 BLOCKED | 중 | 운영 로그 1~2주 | 설정값(`SPEECH_STALE_PROCESSING_AFTER`) | `scripts/speech-timing-report.py`로 판단(설계 문서 4절) |
| GitHub Actions 실행 | NOT RUN(이번 범위 제외) | 중 | 커밋·푸시 | — | 워크플로 실행 |
