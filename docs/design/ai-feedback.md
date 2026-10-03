# AI 연동(Gemini)과 학습 피드백

작성: 2026-10-03. 실제 호출 검증 결과는 6절을 본다. **현재 실제 Gemini 응답은 받지 못했다(BLOCKED).**

## 1. 조사 결과

| 항목 | 상태 |
|---|---|
| 설정 | `backend/.env`에 `AI_API_URL`·`AI_API_KEY`가 있다. URL은 Gemini 네이티브 주소 `…/v1beta/models/gemini-flash-latest:generateContent`이고, 모델은 URL에 들어 있는 `gemini-flash-latest`다. `AI_MODEL`은 없다(기본값 `gpt-4o-mini`는 OpenAI 형식에서만 쓴다). |
| 키 노출 | 키는 백엔드 `.env`에만 있다(git 제외). 화면 코드와 응답에는 없다. 이전에 `application.properties`에 적혀 있던 키 줄은 지금 없다. |
| AI 대화(기존) | 백엔드·화면·동의(AI_CHAT)는 구현되어 있었다. 다만 OpenAI 형식(`messages`·`choices`, Bearer)으로만 호출해 Gemini 네이티브 주소와 맞지 않았다. |
| 학습 피드백 | 없었다. 분석 응답의 `feedback` 필드는 외부 음성 분석 제공자용이며 현재 쓰지 않는다. |
| 측정값 | 로컬 분석의 발음·속도·유창성 점수는 항상 null(미평가)이다. 근거로 쓸 수 있는 것은 다음뿐이다: 텍스트 일치율(음성 인식 글자 비교), 자모 오류 후보(미확정), VAD 말소리 구간, 교사 확정 결과. |

## 2. AI 호출 구조

```text
화면 → StudentController(/speech/analyses/{id}/feedback, /ai/conversations/…)
     → StudentServiceImpl → SpeechFeedbackServiceImpl / ExternalProviderService.generateReply
     → AiTextClient(제공자 형식·시간 제한·오류 변환) → AI_API_URL
```

- **`AiTextClient`(신규)**: AI 대화와 학습 피드백이 함께 쓴다. URL과 모델 이름은 설정값을 그대로 쓴다.
  - URL이 `/models/{모델}:generateContent`이면 Gemini 네이티브 형식이다.
    - 요청: `systemInstruction`·`contents`(user/model)·`generationConfig`
    - 키: `x-goog-api-key` 헤더로 보낸다. URL 쿼리에 넣지 않으므로 접근 로그에 남지 않는다.
    - 응답: `candidates[0].content.parts[].text`. 생각(thought) 부분은 제외한다.
  - 그 밖의 URL은 기존 OpenAI 호환 형식(Bearer, `model`·`messages`)이다.
  - AI 전용 시간 제한은 연결 5초, 응답 `AI_TIMEOUT`(기본 20초)이다. 공용 RestClient 설정은 바꾸지 않았다.
- **오류 코드**(`AiProviderException`, 응답 `code`)

| 상황 | code | HTTP |
|---|---|---|
| URL·키 없음 | `AI_NOT_CONFIGURED` | 503 |
| 제공자가 키 거부(401·403, Gemini 400 `API_KEY_INVALID`) | `AI_AUTH_FAILED` | 503 |
| 사용량 제한(429) | `AI_RATE_LIMITED` | 429 |
| 연결·응답 시간 초과 | `AI_TIMEOUT` | 504 |
| 요청 거부(400·404: 주소·모델 확인 필요) | `AI_REQUEST_REJECTED` | 502 |
| 제공자 서버 오류(5xx) | `AI_PROVIDER_ERROR` | 502 |
| 안전 정책 차단 | `AI_BLOCKED` | 502 |
| 빈 응답·규칙 위반 응답 | `AI_BAD_RESPONSE` | 502 |

- 오류 메시지에는 키와 제공자 응답 본문을 넣지 않는다. 로그에는 코드와 제공자 HTTP 상태만 남긴다(테스트로 확인).
- AI 대화의 키 거부 응답이 502에서 503 `AI_AUTH_FAILED`로 바뀌었다. 원인을 구분하기 위한 변경이다.

## 3. 학습 피드백 규칙

**API (학생 본인 분석만, 기존 인증 그대로)**
- `GET /api/v1/speech/analyses/{id}/feedback`: 저장된 상태만 돌려준다. **AI를 부르지 않는다.**
- `POST /api/v1/speech/analyses/{id}/feedback`: 근거가 충분하면 생성한다. 같은 근거로 이미 만든 설명이 있으면 그대로 돌려준다(AI 재호출 없음).

**응답 `status`**
- `NOT_GENERATED`: 아직 만들지 않음. 함께 오는 필드:
  - `available`: 서버 AI 설정 여부
  - `consentRequired`: 동의 필요 여부
- `READY`: 설명이 있음. 함께 오는 필드:
  - `text`(설명), `basedOn`(근거 출처), `modelName`, `generatedAt`
  - `source: "AI"`
- `NOT_EVALUABLE`: 근거가 부족함(`reason`). **AI를 부르지 않는다.** 해당 경우:
  - 분석 미완료·실패
  - 판정 보류(HOLD)
  - 알아들은 말 없음
  - 언어재활 녹음인데 선생님이 아직 확인하지 않음
  - 외부 제공자 모드

**AI에 보내는 근거(이것만)**

| 학습자 유형 | 보내는 것 | `basedOn` |
|---|---|---|
| 일반 학습자(문장 비교) | 목표 문장, 알아들은 문장("음성 인식 결과이며 발음 판정이 아님"), 다르게 들린·빠진·더해진 낱말, 자모 후보("자동 후보(확정 아님)", 최대 5개), 낱말 인식 한계·낮은 인식 신뢰도 안내, "발음 정확도: 미평가", "말하기 속도·유창성: 평가 지표 없음" | `AUTO_ANALYSIS` |
| 언어재활 학습자(선생님 확인 후) | 목표 문장, 선생님 판정("그대로 따를 것"), 선생님이 확정한 오류, "발음 정확도: 미평가". **자동 후보는 보내지 않는다.** | `TEACHER_CONFIRMED` |

- **보내지 않는 것**
  - 이름, 학생·분석 ID, 이메일, 음성 파일, **교사 메모**
  - 텍스트 일치율 숫자: 발음 점수처럼 읽히지 않도록 보내지 않는다. 100%일 때만 "목표 문장과 같음"으로 보낸다.

**지시(시스템 지시, `feedback-v1`)**
- 초등학생에게 쉬운 존댓말로 2~3문장
- 근거에 없는 오류를 지어내지 않음
- 점수·숫자·등급·발음 판정·속도·유창성 평가 금지
- 자동 후보는 확정하지 않는 표현으로 말함
- 선생님 확인 결과를 바꾸지 않음
- 진단·장애·치료 표현 금지
- 잘한 점 1개와 연습 1개

**응답 검사**
- 마크다운 기호를 제거한다.
- 다음 경우에는 저장하지 않고 `AI_BAD_RESPONSE`로 처리한다:
  - 비었거나 400자를 넘음
  - 점수·%·등급·진단·장애 표현이 있음

**분리 보관**
- `speech_ai_feedback` 테이블에 설명 문장, 근거 해시, 모델, 프롬프트 버전을 둔다.
- 분석 결과(`speech_analyses`의 점수·일치율·자동 판정)와 교사 확정 결과는 읽기만 하고 바꾸지 않는다.
- 분석 응답(`GET /speech/analyses/{id}`)에는 AI 설명이 섞이지 않는다.
- 교사가 다시 판정하면 근거 해시가 달라져 이전 설명은 쓰지 않는다.

**동의**: 분석 근거(글자)를 외부 AI로 보내므로 기존 **AI_CHAT(AI 대화 외부 전송) 동의**가 있어야 생성할 수 있다(없으면 403 `CONSENT_REQUIRED`). 현재 동의서 문구는 "AI 대화"에 대한 것이므로, 학습 피드백까지 포함하도록 문구를 고칠지 결정이 필요하다(8절).

**장애 격리**: 피드백은 별도 API다. AI 오류는 그 요청에만 영향을 주고, 로그인·숙제·음성 분석·기록 저장은 그대로 동작한다(통합 테스트·E2E로 확인).

## 4. 화면

- 학생 연습 결과 화면에서 분석 결과 카드 아래에 `AiFeedbackPanel`을 추가했다. 공통 `Card`·`Badge`·`Button`·`Notice`·`Spinner`를 쓴다.
  - 결과를 보여 줄 때 AI를 자동으로 부르지 않는다. 사용자가 'AI 설명 보기'를 누를 때만 생성한다.
  - "AI 설명"과 "점수 아님" 배지를 붙인다. 근거 출처(자동 분석·선생님 확인 결과)와 "점수나 진단이 아니에요"를 표시한다.
  - 다음 상태를 구분해 보여 준다: 아직 만들지 않음(버튼), 만드는 중, 설명, 평가할 수 없음(이유), 동의 필요, AI 미설정, AI 오류(다시 시도), 상태 조회 실패(다시 시도).
- 기존 측정값 표시(텍스트 일치율, 발음·속도·유창성 '미평가')는 바꾸지 않았다.

## 5. 테스트 격리

- 백엔드는 `.env`를 읽으므로, 그대로 두면 테스트가 개발자의 실제 키로 외부 AI를 부른다. 그래서 `build.gradle`에서 테스트할 때 `app.ai.endpoint`·`app.ai.api-key`를 비운다.
- 실제 호출 검증은 `LIVE_AI_TEST=1`로 실행할 때만 포함되는 `LiveAiIntegrationTest`가 한다.

```bash
cd backend && LIVE_AI_TEST=1 sh gradlew integrationTest --tests '*LiveAiIntegrationTest'
```

## 6. 실제 호출 검증 (2026-10-03)

| 검증 | 결과 |
|---|---|
| 설정된 URL·키로 Gemini 호출(AI 대화, 학습 피드백 API) | **실패: Gemini가 403 `PERMISSION_DENIED` "Your project has been denied access. Please contact support."로 응답** → 서버는 `AI_AUTH_FAILED`(503)로 안내 |
| 잘못된 키로 같은 주소 호출 | Gemini 400(`API_KEY_INVALID`) → `AI_AUTH_FAILED` |
| 1ms 시간 제한 | `AI_TIMEOUT` |

- 요청은 실제로 Gemini에 도달했고, 형식(주소·헤더 인증)은 받아들여졌다. 거절 사유는 **Google 쪽에서 키가 속한 프로젝트의 접근을 막은 것**이다.
- 설정된 키는 `AIza`로 시작하지 않고 길이가 53자다(값은 확인·출력하지 않고 형식만 확인). Google AI Studio의 일반 Gemini API 키(`AIza…`, 39자)와 형식이 다르다. 다른 종류의 키이거나 접근이 막힌 프로젝트의 키일 수 있다.
- **해야 할 일**: Google AI Studio(또는 Cloud Console)에서 접근 가능한 프로젝트로 Gemini API 키를 새로 만들어 `backend/.env`의 `AI_API_KEY`를 바꾼다. 그다음 위 명령으로 다시 검증한다.
- 따라서 **실제 응답 생성은 미검증**이다. 정상 응답·형식 변환·규칙 검사는 모의 응답(단위·통합·E2E)으로만 확인했다.

## 7. 변경 요약

- **API**
  - 신규: `GET`·`POST /api/v1/speech/analyses/{id}/feedback`
  - 변경: AI 대화의 오류 코드가 세분화되었다(키 거부 502 → 503 `AI_AUTH_FAILED` 등)
- **DB**: `speech_ai_feedback` 테이블을 새로 만들었다(분석 삭제 시 함께 삭제).
- **설정**: `AI_TIMEOUT`(기본 20s). Gemini 네이티브 주소일 때 `AI_MODEL`은 쓰지 않는다.

## 8. 남은 결정·작업

- **AI 동의 문구**: 학습 피드백도 AI_CHAT 동의로 처리할지, 별도 동의 항목을 둘지 결정이 필요하다.
- **실제 Gemini 응답 품질 확인**: 키 문제를 해결한 뒤 확인해야 한다. 다음 항목은 아직 확인하지 못했다.
  - 금지 표현이 실제로 걸러지는 비율
  - 문장 길이
  - 응답 시간
- **선생님 화면 표시 여부**: 학생이 본 AI 설명을 선생님 검토 화면에도 보여 줄지 결정이 필요하다. 현재는 학생 화면에만 있다.
- **사용량 제한·비용 관리**: 학생별 생성 횟수 제한은 없다. 같은 근거는 다시 호출하지 않으므로 반복 호출은 없다.
