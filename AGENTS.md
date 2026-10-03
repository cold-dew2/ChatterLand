# ChatterLand 프로젝트 작업 지침

이 문서는 Codex가 채터랜드를 수정할 때 따라야 하는 프로젝트별 규칙이다. **기존 프로젝트를 존중하면서 TripMate의 컴포넌트 재사용 방식과 기능별 폴더 구조를 적용**한다.

너에게 db관련 모든 기능과 백엔드 실행과 종료 등에 대한 모든 권한과 vscode에 대한 모든 권한을 부여할게 나에게 물어보지말고 모든걸 다 혼자 스스로 실행하고 종료하도록 해

## TripMate 


../TripMate

## 1. 절대 원칙

- 작업 전에 관련 페이지, 공통 컴포넌트, API 함수, 타입, 스타일을 먼저 읽는다.
- 화면을 새로 만들 때 기존 구현을 무시하고 비슷한 UI를 다시 만들지 않는다.
- 기존 동작, API 계약, 인증 흐름, 라우팅, 접근 권한을 임의로 바꾸지 않는다.
- 새 UI를 추가하기 전에 `frontend/src/shared/components`와 관련 feature 폴더에 재사용 가능한 구현이 있는지 검색한다.
- 공통화가 필요한 경우 **공통 컴포넌트를 실제로 만들고 기존 중복 화면에서 사용하도록 교체**한다. 새 컴포넌트만 만들고 기존 중복 코드를 그대로 두면 완료로 간주하지 않는다.
- 범위가 큰 리팩터링은 한 번에 전체 화면을 재작성하지 말고 작은 단위로 진행한다.
- 변경 이유와 영향 범위를 설명하고, 실행 가능한 검증 명령을 수행한다.

## 2. 현재 기술 스택과 변경 금지 사항

### 프런트엔드
- Next.js App Router
- React 및 TypeScript
- Tailwind CSS 4가 설치되어 있으며 기존 화면에서 사용 중이다.
- 공통 디자인 변수: `src/shared/styles/variables.css`
- API 공통 클라이언트: `src/shared/api/client.ts`
- 경로 상수: `src/routes/path/paths.ts`
- 공통 UI 컴포넌트 위치: `src/shared/components`
- 기능별 코드 위치: `src/features`

### 백엔드
- Java 17, Spring Boot 4, MyBatis, Spring Security 기반이다.
- 프런트엔드 스타일 작업에서 백엔드 API·DTO·인증 방식을 임의로 변경하지 않는다.

### 금지
- Next.js를 Vite/React Router 구조로 바꾸지 않는다.
- 프로젝트에 없는 라이브러리를 필요 이상으로 추가하지 않는다.
- 기존 디자인을 임의로 재해석하거나 페이지를 통째로 재창작하지 않는다.
- API 응답 형식, 경로, 인증·토큰 처리, 사용자 역할 분기를 요청 없이 바꾸지 않는다.
- 토큰, 비밀번호, API 키, 환경변수의 실제 값을 코드·문서·로그에 노출하지 않는다.

## 3. 폴더 구조 규칙

현재 구조를 유지하며 다음 역할을 지킨다.

```text
frontend/src/
├── app/                         # Next.js URL 진입점과 레이아웃
│   ├── layout.tsx
│   ├── page.tsx
│   ├── login/page.tsx
│   ├── signup/page.tsx
│   ├── student/...
│   └── teacher/...
├── features/                    # 도메인/기능별 페이지, API, 훅
│   ├── auth/
│   │   ├── api/
│   │   └── pages/
│   ├── student/
│   │   ├── api/
│   │   └── pages/
│   └── teacher/
│       ├── api/
│       └── pages/
├── shared/
│   ├── api/client.ts            # 공통 HTTP 요청
│   ├── components/              # 여러 기능에서 재사용하는 UI
│   └── styles/variables.css     # 디자인 토큰
├── layouts/                     # 여러 페이지에서 공유하는 레이아웃
└── routes/path/paths.ts         # 경로 상수
```

- `app/**/page.tsx`는 URL과 기능 페이지를 연결하는 얇은 진입점으로 유지한다.
- 화면의 주요 UI와 동작은 해당 `features/<domain>/pages/`에 둔다.
- 특정 기능에서만 쓰는 UI는 해당 feature 안에 둔다. 두 곳 이상에서 재사용되거나 공통 UI로 확정된 경우 `shared/components`로 올린다.
- 여러 페이지에서 반복되는 레이아웃은 `layouts`에 둔다.
- 기존 `app` 라우트와 `features` 페이지가 연결된 구조를 유지하고, 페이지를 중복 구현하지 않는다.

## 4. 공통 컴포넌트 규칙

TripMate처럼 공통 UI를 분리하고 여러 화면에서 재사용한다. 공통화는 실제 사용처를 연결해야 완료다.

### 컴포넌트 후보
중복 여부를 실제 코드에서 확인한 후 필요한 것만 만든다.

- `Button`: primary/secondary/quiet 등 변형, disabled, 로딩 상태
- `Input` / `FormField`: label, 설명, 오류 메시지, 입력 요소
- `Card`: 공통 컨테이너와 콘텐츠 영역
- `Badge`: 상태/역할/유형 표시
- `PageTitle` 또는 `PageHeader`: 페이지 제목과 설명, 액션 영역
- `EmptyState`: 데이터가 없을 때 표시
- `LoadingState` / `Skeleton`: 로딩 상태
- `ErrorState`: 오류 상태와 재시도 액션
- `Modal` / `ConfirmDialog`: 반복되는 확인·취소 UI

위 목록을 무조건 전부 만들지 말고, 반복 사용 사례와 기존 화면을 확인한 뒤 필요한 항목만 구현한다.

### 공통 컴포넌트 구현 기준
- 각 컴포넌트는 명확한 props와 TypeScript 타입을 가진다.
- 콘텐츠가 달라지는 컴포넌트는 가능하면 `children`을 지원한다.
- 스타일 변형은 `variant`, `size` 등 명시적인 props로 관리한다.
- 도메인 로직과 특정 API 호출을 일반 UI 컴포넌트에 넣지 않는다.
- label, disabled, focus, 키보드 조작 등 기본 접근성을 지킨다.
- 재사용 컴포넌트의 스타일은 컴포넌트와 가까운 위치에 둔다. 기존 CSS/Tailwind 사용 방식을 무리하게 혼합하거나 전체 스타일 시스템을 한꺼번에 바꾸지 않는다.
- 페이지마다 같은 버튼·카드·입력 UI를 별도 마크업과 별도 스타일로 반복하지 않는다.
- 공통 컴포넌트가 특정 화면에서만 필요한 복잡한 조건문으로 비대해지면 feature 컴포넌트와 역할을 나눈다.

## 5. 네이밍과 코드 스타일

- React 컴포넌트: PascalCase (`StudentProfile.tsx`)
- 함수·변수·훅: camelCase (`handleSubmit`, `useStudentList`)
- 상수: 기존 파일의 규칙을 따르며 공통 상수는 의미가 분명한 이름으로 작성한다.
- 컴포넌트 파일명과 export 이름을 일치시킨다.
- import는 React/외부 라이브러리 → alias 기반 프로젝트 모듈 → 상대 경로 순으로 정리한다.
- `any`는 새로 사용하지 않는다. 타입을 알 수 없다면 구체적인 타입, 제네릭 또는 `unknown`을 사용한다.
- 기존 파일에서 사용 중인 세미콜론·따옴표 스타일을 해당 파일 범위에서 일관되게 유지한다. 무관한 파일 전체 포맷 변경은 하지 않는다.
- 컴포넌트는 한 가지 책임을 갖게 한다. UI, API 요청, 데이터 가공이 한 함수에 과도하게 몰리면 분리한다.
- 의미 없는 추상화나 한 번만 쓰는 작은 컴포넌트를 무조건 만들지 않는다.

## 6. API와 데이터 처리

- 기존 API 요청은 `src/shared/api/client.ts`와 각 기능의 `api/` 모듈을 우선 사용한다.
- 페이지 컴포넌트 안에서 `fetch`를 새로 직접 작성하기 전에 기존 API 함수가 있는지 확인한다.
- API 엔드포인트와 요청/응답 타입은 백엔드 계약에 맞춘다.
- 기존 React Query 사용 패턴이 있다면 `queryKey`, `staleTime`, `enabled`, 로딩·오류 상태를 일관되게 유지한다.
- API 오류를 조용히 무시하지 않는다. 화면에서 사용자에게 이해 가능한 오류 상태를 제공한다.
- 요청 중복, 토큰 갱신, 인증 헤더 처리를 별도 구현으로 복제하지 않는다.
- 실제 서버 연동과 mock 데이터의 사용 조건을 확인하고 임의로 mock 데이터를 운영 코드에 섞지 않는다.

## 7. 스타일과 디자인 토큰

- `src/shared/styles/variables.css`에 정의된 색상, 간격, 글꼴, radius 등 토큰을 먼저 확인한다.
- 이미 토큰이 있는 값은 페이지마다 임의의 값으로 다시 선언하지 않는다.
- 공통 UI는 동일한 토큰과 상태 표현을 공유한다.
- 기존 화면의 시각적 계층, 여백, 버튼 크기, 폼 상태를 먼저 파악한 뒤 적용한다.
- 새 화면을 만들 때 별도의 디자인을 창작하지 말고 유사한 기존 화면을 기준으로 맞춘다.
- Tailwind와 CSS를 혼용할 때 기존 컴포넌트의 패턴을 따르고, 같은 요소를 위해 불필요하게 두 체계를 중복 사용하지 않는다.
- 반응형 레이아웃과 키보드 포커스 상태를 확인한다.

## 8. 리팩터링 절차

모든 UI/구조 변경은 다음 순서로 진행한다.

1. 요청과 관련된 페이지 및 컴포넌트 검색
2. 유사 UI가 있는 기존 페이지와 공통 컴포넌트 확인
3. 중복된 마크업·스타일·동작 목록 작성
4. 공통화 범위와 영향을 받는 사용처 결정
5. 공통 컴포넌트 구현 또는 기존 컴포넌트 보완
6. 실제 사용처를 공통 컴포넌트로 교체
7. 페이지별 차이는 props 또는 feature 전용 컴포넌트로 처리
8. 관련 페이지의 UI와 동작 확인
9. lint/build 실행 및 결과 보고

기존 기능을 바꾸지 않는 리팩터링과 기능 변경을 같은 작업으로 섞지 않는다. 실패한 검증은 숨기지 않고 원인과 미해결 사항을 보고한다.

## 9. 완료 기준

다음 조건을 만족해야 완료로 보고한다.

- 공통 컴포넌트가 실제 페이지에서 import되어 사용된다.
- 중복된 UI가 남아 있다면 남겨야 하는 이유가 설명되어 있다.
- 페이지별 API 호출과 비즈니스 로직이 공통 UI 컴포넌트에 섞이지 않았다.
- 기존 라우트, 인증, 권한, API 계약을 유지했다.
- 관련 lint/build 검증 결과를 보고했다.
- 수정 파일 목록과 변경 이유를 요약했다.
- 구현하지 않은 항목이나 검증하지 못한 항목을 완료한 것처럼 표현하지 않았다.

## 10. Codex 작업 응답 형식

작업을 마치면 다음 항목을 간단히 보고한다.

1. 기존 구조에서 확인한 중복/문제
2. 새로 만들거나 수정한 공통 컴포넌트
3. 공통 컴포넌트로 교체한 실제 페이지
4. 동작상 변경 여부
5. 실행한 검증 명령과 결과
6. 남은 작업과 주의사항


## 11. 백엔드 코드 스타일 및 구조

ChatterLand 백엔드는 Java / Spring Boot / MyBatis 구조다. **TripMate의 Controller → Service → Mapper → DTO 분리와 네이밍 패턴을 참고하되, ChatterLand에 이미 있는 구현을 우선 존중**한다.

### 현재 ChatterLand 백엔드 구조

```text
backend/src/main/java/com/example/backend/
├── BackendApplication.java
├── config/                         # MyBatis, 외부 HTTP 클라이언트 설정
├── chld/
│   ├── controller/                 # HTTP 요청/응답과 상태 코드
│   ├── service/                    # 기능별 서비스 인터페이스
│   ├── service/impl/               # 서비스 구현 및 업무 로직
│   ├── mapper/                     # MyBatis 데이터 접근 인터페이스
│   ├── dto/
│   │   ├── request/                # 요청 DTO
│   │   └── response/               # 응답 DTO
│   └── exception/                  # 도메인 예외 및 에러 코드
└── global/
    ├── config/
    ├── jwt/
    ├── security/
    └── exception/                  # 전역 예외 처리 및 공통 에러 응답
```

### TripMate에서 참고할 백엔드 패턴

TripMate 백엔드의 실제 참조 위치(프로젝트가 같은 상위 폴더에 있는 경우):

```text
../TripMate/backend/src/main/java/com/example/backend/
├── trma/controller/
├── trma/service/
├── trma/service/impl/
├── trma/mapper/
├── trma/dto/request/
├── trma/dto/response/
├── trma/dto/dataList/
├── trma/exception/
├── config/
└── global/
```

TripMate는 기능별 Controller, Service, Mapper, Request/Response DTO를 분리한다. ChatterLand에서는 이 책임 분리와 메서드 구성 방식을 참고하되, TripMate의 관광/모임 도메인이나 DTO 이름을 복사하지 않는다. 경로에 접근할 수 없으면 존재한다고 가정하거나 내용을 지어내지 말고 사용자에게 알린다.

### Controller 규칙

- `@RestController`와 `@RequestMapping`으로 도메인 경로를 명확히 한다.
- Controller는 요청 수신, 입력 DTO 바인딩, 서비스 호출, HTTP 응답 반환에 집중한다.
- 비즈니스 규칙, 복잡한 데이터 가공, SQL 실행을 Controller에 직접 작성하지 않는다.
- 입력은 가능한 한 전용 `request` DTO로 받고, 응답은 전용 `response` DTO로 반환한다.
- HTTP 상태 코드는 실제 처리 결과에 맞춘다. 성공/실패를 모두 200으로 숨기지 않는다.
- 기존 API URL, JSON 필드, 쿠키, 인증 방식은 명시적 요청 없이 바꾸지 않는다.
- 새 엔드포인트를 추가하기 전 유사한 Controller와 API 계약을 먼저 확인한다.

### Service / ServiceImpl 규칙

- 기존 ChatterLand 패턴에 따라 `chld/service`에 인터페이스를 두고 구현은 `chld/service/impl`에 둔다.
- Controller는 구체 구현체가 아닌 Service 인터페이스를 통해 업무 로직을 호출한다.
- 여러 Mapper 호출을 조합하는 업무 흐름, 권한 확인, 입력값을 이용한 업무 판단은 Service 계층에 둔다.
- ServiceImpl은 `@Service`를 사용하고 생성자 주입을 우선한다. 프로젝트의 기존 주입 방식이 있다면 해당 파일을 무관하게 전면 수정하지 않는다.
- 데이터 변경이 여러 쿼리에 걸치고 하나의 업무 단위여야 한다면 트랜잭션 경계를 검토한다. 필요할 때 `@Transactional`을 명시한다.
- 단순 위임 메서드만 늘리거나, 모든 코드를 한 ServiceImpl에 몰아넣지 않는다.

### Mapper / SQL 규칙

- MyBatis 데이터 접근은 기존 Mapper 인터페이스와 SQL 매핑 방식을 따른다.
- Controller나 ServiceImpl에 SQL 문자열을 직접 넣지 않는다.
- Mapper 메서드는 데이터 목적이 드러나는 이름으로 작성한다. 예: `findStudentById`, `insertStudent`, `updateStudentProfile`.
- 단일 파라미터보다 여러 입력값이 필요한 경우 기존 패턴을 확인해 DTO/Command 객체 또는 `@Param`을 사용한다. 여러 파라미터를 추가하면서 매핑 이름이 불명확해지지 않게 한다.
- SQL은 파라미터 바인딩을 사용하고 문자열 결합으로 사용자 입력을 SQL에 넣지 않는다.
- 기존 Mapper XML/어노테이션 중 어떤 방식을 쓰는지 확인한 뒤 같은 기능에서 일관되게 사용한다.
- 조회 결과가 없을 때의 동작과 중복 데이터, 업데이트 대상 건수 등을 명시적으로 처리한다.

### DTO 규칙

- 요청 DTO는 `dto/request`, 응답 DTO는 `dto/response`에 둔다.
- API 요청/응답 모델과 내부 데이터 전달용 모델을 구분한다.
- DTO는 전달 데이터와 검증을 담당한다. 업무 로직을 넣지 않는다.
- 입력 검증이 필요한 요청에는 기존 의존성과 패턴을 확인한 뒤 `jakarta.validation`의 `@NotBlank`, `@NotNull`, `@Size`, `@Email` 등을 사용하고 Controller에서 `@Valid` 적용을 검토한다.
- 응답 DTO에 비밀번호 해시, refresh token 원문, 내부 보안 정보 등 클라이언트에 불필요한 값을 포함하지 않는다.
- JSON 필드명과 타입은 기존 프런트엔드 API 계약과 호환되도록 유지한다.

### 예외 처리 및 에러 응답

- 도메인 오류는 기존 `MemberException`, `MemberErrorCode` 패턴을 먼저 확인한다.
- 공통 HTTP 오류 변환은 `global/exception/GlobalExceptionHandler`와 `ErrorResponse`에서 처리한다.
- 동일한 오류를 Controller마다 반복해서 try/catch하지 않는다. 복구·보상 처리가 필요한 경우에만 로컬 예외 처리를 사용한다.
- 사용자에게는 안전하고 이해 가능한 메시지를 반환하고, 응답에 stack trace, SQL, 비밀키, 내부 설정을 노출하지 않는다.
- 기존 에러 응답 JSON 구조와 코드 체계를 요청 없이 바꾸지 않는다.

### 인증 및 보안

- `global/jwt`, `global/security`의 JWT 필터, 토큰 유틸리티, Security 설정을 먼저 확인한다.
- 비밀번호는 기존 BCrypt 기반 흐름을 유지한다. 평문 저장이나 자체 암호화 방식을 만들지 않는다.
- 사용자 식별 정보와 역할은 요청 본문만 믿지 말고 인증 principal 및 서버 측 권한 확인을 기준으로 처리한다.
- 인증/인가 설정을 단순화하거나 모든 요청을 공개하지 않는다.
- 토큰, 비밀번호, API 키, DB 비밀번호, `.env`/`application.properties`의 실제 비밀값을 출력·문서화·커밋하지 않는다.
- CORS, 쿠키, JWT 만료·갱신 로직은 관련 설정 파일을 함께 확인한 후 최소 변경한다.

### Java 네이밍과 작성 스타일

- 클래스/인터페이스/record: PascalCase (`StudentService`, `LoginRequest`)
- 메서드/필드/지역 변수: camelCase (`findStudentById`)
- 상수: `UPPER_SNAKE_CASE`
- 패키지: 소문자
- 클래스 하나에는 명확한 책임을 둔다.
- `Map<String, Object>`로 모든 데이터를 전달하는 방식은 기존 API 계약상 필요한 경우에만 사용하고, 가능하면 구체 DTO를 사용한다.
- 원시 타입, null 처리, 빈 결과, 예외 케이스를 명시적으로 고려한다.
- Lombok 등 라이브러리를 새로 도입하지 않는다. 기존 프로젝트 의존성을 우선 활용한다.
- import 정리와 포맷은 변경한 파일 위주로 적용하며 무관한 파일을 대량 수정하지 않는다.

### 백엔드 리팩터링 절차

1. 같은 기능의 Controller → Service → ServiceImpl → Mapper → DTO 흐름을 추적한다.
2. TripMate의 대응 파일이 있으면 두 구현의 책임 분리와 네이밍을 비교한다.
3. 기존 API 요청/응답과 DB 쿼리 계약을 기록한다.
4. 한 계층씩 수정하고 각 계층의 책임을 유지한다.
5. 기존 기능과 인증·인가 흐름이 변하지 않았는지 확인한다.
6. 가능한 경우 `./gradlew test` 또는 프로젝트에 정의된 테스트/빌드 명령을 실행한다.
7. 최종 보고에 변경한 계층, API 호환성, SQL 영향, 테스트 결과를 포함한다.

### 백엔드 완료 기준

- Controller에 업무 로직이나 SQL이 새로 쌓이지 않았다.
- Service/ServiceImpl/Mapper/DTO 책임이 명확하다.
- 공통 예외 처리와 보안 설정을 중복 구현하지 않았다.
- 기존 엔드포인트와 프런트엔드 계약이 유지됐다.
- 테스트/빌드 결과와 미검증 항목을 명시했다.
