# 숙제 중복·동시 수정, 비밀번호 재설정 후 토큰, PROCESSING 기준 시간

작성: 2026-10-03. 이전 코드 리뷰(`docs/testing/code-review.md` 4절)에서 미해결로 남긴 네 항목의 설계와 검증 결과다.
테스트 결과 수치는 `docs/testing/test-results.md` 10절을 따른다.

## 1. 숙제 중복 생성 방지 (Idempotency-Key)

**문제**: 화면은 저장 중 버튼을 잠가 중복 클릭을 막지만, 서버가 저장한 뒤 응답이 유실되면(네트워크 끊김·시간 초과) 사용자가 다시 눌러 같은 숙제가 두 번 생긴다. 같은 요청이 동시에 도착해도 마찬가지다.

**설계**: 음성 분석에 이미 쓰는 `Idempotency-Key` 방식을 숙제 등록(`POST /api/v1/teachers/me/homeworks`)에 확장했다.

| 항목 | 정의 |
|---|---|
| 생성 | 화면(`useTeacherData.addHomework`)이 등록 의도마다 만든다(`shared/api/idempotencyKey.ts`). `crypto.randomUUID`가 없는 HTTP 페이지에서는 `getRandomValues`로 만든다. |
| 재사용 | 실패 뒤 **같은 내용**으로 다시 보내면 같은 키를 쓴다. 내용을 바꾸거나 등록에 성공하면 새 키를 만든다. |
| 전달 | 요청 헤더 `Idempotency-Key` (선택). 영문·숫자·`-`·`_` 8~64자, 형식 오류는 400. CORS 허용 헤더에 이미 포함. |
| 저장 | `homeworks.request_key`, `homeworks.request_hash`(학생·제목·유형·설명·목표 시간·마감일의 SHA-256). 유니크 키 `uq_homework_request_key (teacher_id, request_key)`. |
| 유효 범위 | 선생님별. 다른 선생님이 같은 키를 써도 서로 영향이 없다. |
| 만료 | 시간 기반 만료 없음. 키는 숙제 행과 함께 있다가 숙제를 삭제하면 함께 사라진다(2차에서 이 정책 유지 확정). **한계**: 삭제 뒤 같은 키로 늦게 도착한 재시도는 새 숙제를 만든다. 화면은 성공하면 키를 버리므로 정상 흐름에서는 생기지 않고, 응답 유실 → 다른 곳에서 삭제 → 같은 내용 재전송이 겹칠 때만 생긴다(`HomeworkConcurrencyIntegrationTest#aKeyIsKept…`로 동작 고정). |
| 같은 키·같은 내용 | 새로 만들지 않고 처음 만든 숙제를 돌려준다. **200 OK**(2차에서 201→200 변경, 7절), 응답에 `reused: true`. 처음 생성만 201. |
| 같은 키·다른 내용 | 409, `code: IDEMPOTENCY_KEY_REUSED`. 데이터 변경 없음. |
| 실패한 요청 | 저장되지 않았으므로 키가 남지 않는다. 같은 키로 다시 시도하면 새로 저장된다. |
| 키 없음 | 기존과 같다(매번 새로 생성). 기존 클라이언트 호환. |

**동시성**: 판단의 근거는 DB 유니크 제약이다. 먼저 키로 조회하고, 없으면 INSERT하고, 유니크 제약에 걸리면 다른 요청이 저장한 행을 다시 읽어 돌려준다. 이 메서드는 트랜잭션으로 묶지 않았다. REPEATABLE READ 트랜잭션에서는 먼저 잡힌 스냅샷 때문에 다른 요청이 방금 커밋한 행이 보이지 않기 때문이다. 쓰기는 INSERT 한 문장이므로 부분 저장이 생기지 않는다.

**호환성**: 헤더는 선택이다. 응답에는 필드 `version`과 `reused`(재전송 시)가 추가될 뿐이고, URL·메서드·기존 필드·상태 코드는 같다. DB에는 nullable 컬럼 2개와 유니크 키가 추가된다. 기존 행은 NULL이고, NULL은 유니크 제약에서 서로 겹치지 않는다.

## 2. 숙제 동시 수정 충돌 방지 (낙관적 잠금)

**문제**: 두 탭(또는 선생님과 학생)이 같은 숙제를 바꾸면 마지막 저장이 앞의 변경을 말없이 덮어썼다. 예를 들어 학생이 완료한 숙제를, 완료 전 화면을 보던 선생님이 '미완료'로 되돌릴 수 있었다.

**설계**

- `homeworks.version INT NOT NULL DEFAULT 0`. 선생님 수정·완료 토글, 학생 완료 처리 모두 `version=version+1`.
- 선생님 목록·수정 응답에 `version`이 포함된다.
- `PATCH /homeworks/{id}` 요청의 `version`은 **필수**다(2차, 누락·음수는 400). `WHERE ... AND version=#{version}` 조건으로 수정한다. `DELETE /homeworks/{id}?version=N`도 필수(누락·형식 오류 400)이며 조건부 DELETE 한 문장으로 지운다.
  - 0건이고 숙제가 있으면 409 `VERSION_CONFLICT`(덮어쓰지 않음).
  - 숙제가 없으면 404.
- 화면에서 수정이 충돌하면 목록을 다시 불러오고, 수정 모달을 최신 내용으로 다시 채우고(모달 `key`에 버전 포함), "다른 곳에서 먼저 바뀐 숙제예요…" 안내를 모달 안에 보여준다. 사용자가 확인하고 다시 저장하면 최신 버전으로 반영된다.
- 완료 토글이 충돌해도 목록을 다시 불러오고 안내한다.

**구현 중 발견한 결함 (DEF-10)**: 동시 수정 테스트에서 충돌한 요청이 409가 아니라 **500**이 되었다. MariaDB(테스트 DB 13.0, `innodb_snapshot_isolation`)는 REPEATABLE READ 트랜잭션이 먼저 SELECT한 뒤 다른 트랜잭션이 바꾼 행을 UPDATE하면 오류 1020("Record has changed since last read")을 낸다. MySQL 8은 이 경우 0건을 돌려준다. 그래서 숙제 수정(`TeacherServiceImpl.updateHomework`)과 학생 완료(`StudentServiceImpl.completeHomework`)의 트랜잭션을 없앴다. 두 메서드 모두 쓰기가 조건부 UPDATE 한 문장이라 트랜잭션이 필요 없다. 학생 완료와 선생님 수정이 겹치는 경우도 테스트로 확인했다(500 없음, 성공한 변경 수만큼만 버전 증가).

**호환성(2차 변경)**: `version`을 보내지 않는 클라이언트는 수정·삭제가 400으로 거절된다. 저장소의 클라이언트(화면·E2E·테스트)는 모두 버전을 보내도록 함께 고쳤다. 저장소 밖의 다른 클라이언트가 있다면 함께 배포해야 한다.

## 3. 비밀번호 재설정 후 기존 토큰

**현재 흐름(추적 결과)**

- **access token**: JWT, 기본 24시간(`JWT_EXPIRATION_MS`). 화면이 `sessionStorage`에 두고 `Authorization: Bearer`로 보낸다.
- **refresh token**: 무작위 값, 30일. DB에는 SHA-256 해시만 저장하고, 한 번 쓰면 새 값으로 바꾼다(재사용 시 401). 화면은 본문(JSON)으로 주고받는다.
- **쿠키**: 서버가 쿠키를 발급하지 않는다. 화면 요청의 `credentials: 'include'`는 실질적으로 효과가 없다. 그래서 쿠키 처리 항목은 해당이 없다.
- **비밀번호 변경 경로**: 로그인 상태에서 바꾸는 기능은 없고, 이메일 인증 코드로 재설정하는 기능만 있다. 재설정 시 해당 사용자의 refresh token을 모두 폐기해 왔다. 기존 코드 주석에 "다른 기기에 남은 로그인 세션을 모두 끊는다"고 적혀 있다.
- **빈틈**: 이미 발급된 access token은 서명과 만료만 검사하므로, 재설정 후에도 최대 24시간 유효했다.

**선택지**

| 방식 | 즉시 무효화 | 비용·의존성 | 장애 시 | 비고 |
|---|---|---|---|---|
| A. 만료까지 유지(이전) | ✗ (최대 24시간) | 없음 | 영향 없음 | refresh는 이미 폐기됨 |
| **B. 사용자별 token_version (채택)** | ✓ access·refresh 모두 | 인증 요청마다 PK 조회 1회 | DB 조회 실패 시 503(로그아웃 아님) | 기존 MySQL만 사용 |
| C. access 수명 단축(예: 15분) | 부분(최대 15분) | 갱신 요청 증가 | 영향 없음 | 설정 변경만으로 가능. B와 함께 쓸 수 있음 |
| D. 토큰 폐기 목록·Redis | ✓ | Redis 신규 도입 | Redis 장애 대응 필요 | 현재 인프라에 Redis 없음 |

**채택 이유**: 기존 코드는 이미 "재설정하면 모든 기기 세션을 끊는다"는 정책을 택하고 refresh token을 폐기해 왔다. B는 그 정책에서 빠져 있던 access token을 같은 정책으로 맞춘 것이라 새 정책 결정이 아니다. 새 인프라도 필요 없다.

**구현**

- `users.token_version INT NOT NULL DEFAULT 0`. 재설정 시 비밀번호 변경과 같은 UPDATE에서 1 올린다.
- access token에 `tv` 클레임으로 발급 당시 버전을 담는다(로그인·갱신 모두). 클레임이 없는 이전 토큰은 0으로 본다. 그래서 배포 직후에도 기존 로그인은 유지되고, 재설정한 사용자만 끊긴다.
- `JwtAuthenticationFilter`가 서명·만료를 확인한 뒤 `SELECT token_version FROM users WHERE user_id=? AND status='ACTIVE'`로 비교한다.
  - 다르거나 활성 계정이 아니면 인증하지 않는다(보호 API는 401).
  - DB 조회가 실패하면 503 `AUTH_CHECK_UNAVAILABLE`로 응답한다. 401을 주면 화면이 로그아웃시키기 때문이다.
- refresh token은 기존대로 모두 폐기되므로 무효화된 access token을 refresh로 다시 발급받을 수 없다. 새로 로그인해 받은 토큰은 새 버전을 담는다.
- 화면 쪽은 바꾸지 않았다. 401 → 갱신 실패 → 기존 세션 만료 흐름(`/login?expired=1&next=…`)으로 처리된다.

**비용**: 인증된 요청마다 기본 키 조회가 1회 늘어난다. 캐시는 두지 않았다. 여러 서버가 있을 때 캐시가 서로 달라지면 무효화가 늦어지기 때문이다.

**남은 결정**: 5절의 표를 참고한다.

## 4. PROCESSING 기준 시간 운영 검증

**현재 계산(추적 결과)**

- 고착 기준 = max(`SPEECH_STALE_PROCESSING_AFTER`(기본 5분), 3 × `WHISPER_TIMEOUT` + 60초). 기본값이면 max(300초, 240초) = **300초**다.
- 3배인 이유: 분석 행(PROCESSING)을 만든 뒤 세 단계가 각각 `WHISPER_TIMEOUT` 안에서 끝나거나 실패한다.
  - 분석 슬롯 대기: `tryAcquire`, 동시 처리 수 `WHISPER_MAX_CONCURRENCY`(기본 1)
  - VAD: 시간 초과 시 VAD 없이 계속
  - Whisper: 시간 초과 시 504
- 60초 여유는 파일 저장, 이전 기록 조회, 결과 저장(DB 잠금 대기 포함)을 위한 것이다.
- 정리는 같은 키로 재요청할 때 즉시 하고, 그 밖에는 5분마다(시작 30초 후) 한다(`SpeechAnalysisRecoveryScheduler`). 고착 판정은 행의 `created_at`(DB 시각, 초 단위)으로 한다.
- **늦은 결과**: 완료 저장(`completeLocalAnalysis`)과 실패 저장은 `status='PROCESSING'` 조건이 있어, 이미 정리된 행을 덮어쓰지 않는다. 이번에 이 경우를 `outcome=LATE_AFTER_STALE` 경고 로그로 따로 남기게 했다. 이 로그가 나오면 정상 작업을 고착으로 잘못 판정한 것이다.

**검증 중 발견한 결함 (DEF-11, 이전 주기 R-1 코드)**: 전체 회귀 실행에서 고착 분석에 같은 키 재시도 3건이 동시에 들어온 테스트가 한 번 500으로 실패했다(이전 주기에는 통과). 원인은 InnoDB 교착 상태(MariaDB 1213)다. 고착 정리 UPDATE(`request_key=NULL`)와 같은 `(student_id, request_key)`의 INSERT가 같은 유니크 인덱스 범위를 잠가 한 문장이 되돌려지고, 그 예외가 그대로 500이 되었다. 수정 내용은 다음과 같다.
- 정리 UPDATE가 되돌려지면 다른 요청이 정리한 것으로 보고 다시 조회한다.
- INSERT가 되돌려지면 그 사이 만들어진 분석이 있으면 그것을 돌려주고, 없으면 최대 3회까지 다시 시도한다. 그래도 안 되면 503("분석 요청이 겹쳤어요")으로 끝내고 녹음을 지운다.
- 교착 상태는 타이밍에 따라 생기므로, 수정 경로는 예외를 주입한 단위 테스트(2건)로 확인했다. 수정 후 같은 통합 테스트를 4회 반복했고 모두 통과했지만, 그 4회에서는 교착 상태가 다시 발생하지 않았다. 따라서 이 반복 실행이 수정 경로를 실제 DB에서 거쳤다는 뜻은 아니다.

**운영 데이터 유무**: 저장소와 실행 환경에 운영 처리 시간 로그나 메트릭이 없다(Actuator·Micrometer 미도입, 운영 로그 접근 없음). 따라서 **운영 p50·p95·p99와 시간 초과 비율은 산출하지 못했다.**

**추가한 측정**: 의존성은 추가하지 않고 INFO 로그 두 줄을 남긴다. 음성, 인식 내용, 목표 문장, 파일 경로, 학생·분석 ID는 기록하지 않는다(테스트로 확인).

```text
speech.timing stage=recognize outcome=OK|503|504|422|502|500 totalMs= queueMs= vad=OK|UNAVAILABLE|OFF vadMs= whisperMs= audioMs= timeoutMs=
speech.timing stage=analysis  outcome=COMPLETED|FAILED:<상태>|LATE_AFTER_STALE totalMs= staleAfterMs=
```

**분석 도구**: `backend/scripts/speech-timing-report.py`

- 출력 항목
  - 단계별 p50·p95·p99·최대
  - 504·503 비율
  - VAD 사용 여부
  - LATE_AFTER_STALE 건수
  - 고착 기준 대비 p99·최대 비율
- 완료 표본이 200건 미만이면 "기준 시간의 적절성을 판단하지 않는다"고 표시한다.
- `--junit`으로 테스트 실행 로그를 읽으면 결과가 합성 데이터라고 표시한다.

**합성 데이터 측정**: `docs/testing/test-results.md` 10절 참고. 테스트 실행 로그이며 운영 처리 시간을 대신하지 않는다.

**운영 검증 절차(남은 작업)**

1. 운영 또는 스테이징의 실제 사용 로그를 1~2주 모은다(완료 200건 이상, 가능하면 동시 사용이 많은 시간대 포함).
2. `python3 scripts/speech-timing-report.py <로그>`를 실행한다.
3. 판단 기준(제안):
   - LATE_AFTER_STALE이 0건이어야 한다. 1건이라도 있으면 기준을 올린다.
   - 분석 전체 최대값이 기준의 50% 이하여야 한다.
   - 504 비율이 1% 이하여야 한다. 넘으면 `WHISPER_TIMEOUT`, 서버 사양, `WHISPER_MAX_CONCURRENCY`를 먼저 검토한다.
   - 503(슬롯 대기 초과) 비율을 함께 본다.
4. 기준을 바꾸려면 `SPEECH_STALE_PROCESSING_AFTER`만 조정한다. 3T+60초보다 작게 설정하면 자동으로 올라간다.

## 5. 정책 결정 현황 (2차에서 결정·구현, 7절)

| 항목 | 현재 동작 | 선택지 | 영향 |
|---|---|---|---|
| 숙제 Idempotency-Key 만료 | **확정: 현행 유지**(숙제와 함께 유지·삭제) | — | 한계는 1절 '만료' 행 |
| 재전송 응답 상태 | **변경: 200 OK** + `reused: true` | — | 처음 생성만 201 |
| 숙제 수정 `version` 필수화 | **변경: 필수**(누락 400) | — | 버전 없는 클라이언트는 거절 |
| 숙제 삭제 시 버전 비교 | **변경: 필수**(`?version=`, 불일치 409) | — | 조건부 DELETE |
| 로그아웃 시 access token | **변경: 즉시 무효화(이 기기 세션만)** | — | 7절 |
| 로그인 상태 비밀번호 변경 기능 | **구현: 이 기기 유지, 다른 기기 로그아웃** | — | 7절 |
| access token 수명 | **기본 30분**(`JWT_EXPIRATION_MS`) | 15~30분 권장 | `.env`에 값이 있으면 그 값이 우선 |
| PROCESSING 기준 시간 | 300초(합성 측정 기준 충분한 여유, 운영 미검증) | 운영 로그 측정 후 조정 | 4절 절차 |

## 6. 배포 시 주의

- 스키마는 `schema.sql`의 조건부 ALTER로 추가된다. 컬럼 4개와 유니크 키 1개가 추가되며 모두 기본값 또는 NULL이다. 기존 데이터를 바꾸거나 지우지 않는다. 앱을 시작할 때 자동으로 적용된다(`spring.sql.init.mode=always`).
- MariaDB 13에서만 검증했다. MySQL 8 실제 인스턴스에서는 실행하지 않았다.
- 배포 직후 기존 access token(버전 클레임 없음)은 그대로 유효하다. 비밀번호를 재설정한 사용자만 다시 로그인하게 된다.

## 7. 2차 정책 결정·구현 (2026-10-03)

### 7.1 숙제 API 계약 변경

| API | 이전 | 변경 후 |
|---|---|---|
| `POST /api/v1/teachers/me/homeworks` 같은 키·같은 내용 재전송 | 201 + `reused:true` | **200** + `reused:true` (처음 생성 201 유지, 다른 내용 409) |
| `PATCH /api/v1/teachers/me/homeworks/{id}` | `version` 선택 | `version` **필수**(누락·음수 400, 불일치 409 `VERSION_CONFLICT`) |
| `DELETE /api/v1/teachers/me/homeworks/{id}` | 버전 없음 | `?version=N` **필수**(누락·형식 오류 400, 불일치 409, 없으면 404) |

- 화면(`apiClient`)은 2xx를 모두 성공으로 처리하므로 200 재전송도 성공으로 처리된다(E2E-17에서 재전송 응답 200 확인).
- 삭제가 충돌하면 확인 창을 닫고, 최신 목록을 다시 불러와 "삭제하지 않았어요"라고 안내한다.
- 수정과 삭제가 동시에 들어오면 둘 중 하나만 반영된다. 삭제가 이기면 수정은 404, 수정이 이기면 삭제는 409다(통합 테스트 3회 반복).

### 7.2 access token 수명 (정책 6)

- 조사 결과
  - access token은 `jwt.expiration` ← `JWT_EXPIRATION_MS`로 이미 설정할 수 있었고, 기본값은 24시간이었다.
  - 화면은 401을 받으면 refresh token으로 한 번만(single-flight) 재발급하고 같은 요청을 다시 보낸다.
  - refresh token은 30일, 1회용 회전이다(변경하지 않음).
- 결정: 기본값을 **30분**(1,800,000ms)으로 했다(`application.properties`, `.env.example`).
  - 재발급이 자동이라 15분과 30분은 사용자 경험 차이가 없다.
  - 로그아웃·비밀번호 변경은 수명과 관계없이 즉시 차단되므로(7.3) 수명은 탈취 토큰의 노출 시간만 줄인다. 그래서 갱신 요청이 더 적은 30분을 택했다.
- 환경별 설정: 운영과 개발 모두 `JWT_EXPIRATION_MS`로 바꾼다. 백엔드는 `.env`를 읽으므로(`spring.config.import`), **기존 `.env`에 값이 있으면 그 값이 기본값보다 우선한다.** 이번 통합 테스트 실행에서 로컬 `.env`가 적용된 수명이 24시간으로 관찰되었다(`.env` 파일 자체는 읽지 않음, test-results 11절). 30분을 적용하려면 `.env`의 `JWT_EXPIRATION_MS`를 바꿔야 한다.

### 7.3 로그아웃 시 access token 무효화 (정책 5)

- 조사 결과(변경 전)
  - 로그아웃은 보낸 refresh token 1개만 폐기했고, access token은 서명·만료만 확인해 만료(24시간)까지 유효했다.
  - 쿠키는 쓰지 않는다. 화면은 `sessionStorage`에서 토큰을 지운다.

**선택지 검토**

| 방식 | 이 기기만 끊기 | 저장 | 성능 | 장애 시 |
|---|---|---|---|---|
| ① 로그아웃 시 token_version 증가 | ✗ (모든 기기 로그아웃) | 없음 | 기존 조회 그대로 | 503 |
| ② jti 폐기 목록(DB 테이블) | ✓ | access 만료까지(최대 30분) 행 보관 + 정리 작업 필요 | 조회 1회 | 503 |
| ③ jti 폐기 목록(Redis, TTL=남은 수명) | ✓ | Redis 자동 만료 | 빠름 | Redis 신규 도입·장애 대응 필요(현재 Redis 없음) |
| **④ 로그인 세션 ID (채택)** | ✓ | 기존 `refresh_tokens`에 `session_id` 컬럼만 추가(새 보관 데이터 없음) | 기존 버전 확인 쿼리에 인덱스 EXISTS 1개 | 503(로그아웃 유발 안 함) |

**구현(④)**

- 로그인할 때마다 세션 ID(UUID)를 만들어 refresh token 행(`refresh_tokens.session_id`)에 저장한다. 갱신(회전)으로 만든 refresh token도 같은 세션 ID를 이어받는다.
- access token에 `sid` 클레임을 담는다.
- JWT 필터는 한 번의 조회로 다음을 함께 확인한다.
  - 활성 계정인지
  - 토큰 버전이 일치하는지
  - 그 세션에 폐기되지 않고 만료되지 않은 refresh token이 남아 있는지
- 로그아웃(`POST /api/v1/auth/logout`, 요청 형식은 같음)은 다음 순서로 처리한다.
  - 본문의 refresh token이 속한 세션 전체를 폐기한다.
  - Authorization 헤더의 access token이 속한 세션도 폐기한다. 그래서 refresh token 없이 access token만 보내도 끊긴다.
  - 이미 끝난 세션을 다시 로그아웃해도 204다.
- 다른 기기의 세션은 유지된다.
- 저장 기간: 세션의 유효 여부는 refresh token 행(최대 30일)으로 판단한다. access token마다 따로 보관하는 데이터는 없다. 오래된 refresh token 행 정리는 7.7절 참고(3차에서 해결).
- **한계**
  - 이 배포 이전에 발급된 access token(`sid` 없음)은 로그아웃으로 끊을 수 없다. 이전 수명(최대 24시간) 동안만 유효하며, 비밀번호를 바꾸면 거절된다(`AuthSessionIntegrationTest#legacyTokens…`).
  - refresh token이 30일 만료로 끝난 세션의 access token은 그 시점부터 거절된다. access 수명이 30분이라 영향은 거의 없다.

### 7.4 로그인 상태 비밀번호 변경 (정책 7)

- API: `PATCH /api/v1/auth/me/password` (인증 필요, 신규)
  - 요청: `{currentPassword, newPassword, newPasswordConfirm}`
  - 응답: `{accessToken, refreshToken}`
- 검증(모두 400, 변경 없음)
  - 새 비밀번호 8~72자(가입·재설정과 같은 규칙)
  - 확인 값 일치
  - 현재 비밀번호 일치(BCrypt `matches`)
  - 새 비밀번호가 현재와 같지 않음
- **현재 비밀번호가 틀려도 401이 아니라 400**을 돌려준다. 401이면 화면이 세션 만료로 처리해 로그아웃시키기 때문이다.
- 저장과 토큰 처리: 기존 BCrypt `PasswordEncoder`로 해시해 저장한다. 비밀번호 재설정과 같은 정책으로 `token_version`을 올리고 refresh token을 모두 폐기해 **다른 기기는 모두 로그아웃**된다. 변경한 이 기기는 새 세션 토큰을 응답으로 받아 로그인을 유지한다(화면이 받은 토큰을 저장).
- 화면: 공통 `Input`·`Button`·`Notice`로 만든 `ChangePasswordForm`을 학생 마이페이지(카드)와 선생님 화면(상단 열쇠 버튼 → `Modal`)에서 같이 쓴다.
- 로그: 비밀번호·토큰은 기록하지 않는다. 검증 실패 로그는 필드 이름만 남기고, `LogMasking`이 `currentPassword`·`newPassword`를 가린다. 통합 테스트로 애플리케이션 로그에 비밀번호·토큰이 없음을 확인했다.
- 시도 횟수 제한: 7.6절 참고(3차에서 해결).

### 7.5 DB 변경 (모두 추가형, `schema.sql` 조건부 ALTER)

- `refresh_tokens.session_id CHAR(36) NULL`
- 인덱스 `idx_refresh_session (session_id)`
- 기존 행은 NULL이다. 기존 refresh token은 다음 갱신 때 새 세션 ID를 받는다.

### 7.6 비밀번호 변경 시도 제한 (3차)

- **정책**: 15분 안에 현재 비밀번호를 5번 틀리면, 그 기간 안의 가장 오래된 실패가 15분을 넘을 때까지 429로 막는다.
  - 막힌 동안에는 맞는 비밀번호도 받지 않는다.
  - 성공하면 실패 기록을 지운다.
  - 설정 이름: `PASSWORD_CHANGE_MAX_FAILURES`, `PASSWORD_CHANGE_FAILURE_WINDOW`
- 응답은 다음과 같다. 401을 쓰지 않으므로 로그인은 유지된다.
  - 실패: 400 "현재 비밀번호가 올바르지 않아요. (남은 시도 N회)"
  - 제한: 429 "현재 비밀번호를 여러 번 틀렸어요. M분 뒤에 다시 시도해 주세요."
- **저장**: `password_change_failures(user_id, created_at)` 테이블. 실패한 시각만 남기고 비밀번호 값은 저장하지 않는다. Redis는 쓰지 않는다.
- **동시성**: 트랜잭션 안에서 `SELECT … FOR UPDATE`로 사용자 행을 잠근 뒤 횟수를 세고 기록한다. 그래서 동시에 여러 번 시도해도 기록이 5건을 넘지 않는다(동시 8건 → 400 4건·429 4건).
- **트랜잭션**: 실패 기록이 오류 응답과 함께 커밋되도록 `noRollbackFor = ResponseStatusException`을 썼다. 비밀번호 재설정 코드 검증과 같은 방식이며, 다른 쓰기는 모든 검사를 통과한 뒤에만 한다.
- **한계**: 사용자 단위 제한이다. IP 단위 제한은 없다. 다만 로그인된 토큰이 있어야 시도할 수 있다.

### 7.7 오래된 인증 기록 정리 (3차)

- `AuthTokenCleanupScheduler`가 시작 2분 후, 그 뒤로 6시간마다(`AUTH_TOKEN_CLEANUP_INTERVAL`) `AuthService.purgeStaleAuthRecords()`를 실행한다.
- 지우는 대상
  - 만료된 refresh token
  - 폐기된 지 7일(`REFRESH_TOKEN_REVOKED_RETENTION`)이 지난 refresh token
  - 시도 제한 기간이 지난 비밀번호 변경 실패 기록
- **지우지 않는 대상**: 폐기되지 않고 만료 전인 refresh token. 이것이 현재 로그인 세션이며, access token 세션 확인에 쓰인다.
- 폐기된 행을 지워도 동작은 같다.
  - 회전 뒤 이전 refresh token을 다시 쓰면 지워진 뒤에도 401이다.
  - 로그아웃은 access token의 세션으로도 처리된다.
- 한 번에 1,000건씩 지워 잠금을 짧게 유지한다(트랜잭션으로 묶지 않음).
- 정리용 인덱스 `idx_refresh_expires`, `idx_refresh_revoked`를 추가했다.
- 로그에는 지운 건수만 남긴다.
- `app.scheduling.enabled=false`로 다른 정기 작업과 함께 끌 수 있다.

