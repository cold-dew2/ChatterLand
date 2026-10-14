# ChatterLand 백엔드 GCP 배포 절차 (Cloud Run + Cloud SQL + GitHub Actions)

이 문서는 **준비된 상태와 운영 반영 절차**를 정리한다. 운영 리소스 생성·IAM 변경·비밀값 등록·DB 이전·최초 배포는 모두 **승인 후** 실행한다.
비밀값(DB 비밀번호, JWT 키, AI 키, 메일 비밀번호)은 이 문서·저장소·workflow에 적지 않는다.

> `<PROJECT_ID>`, `<REGION>`, `<INSTANCE>` 등은 실제 GCP 조사 후 정한다. 기존 리소스가 있으면 그것을 우선 쓴다.

## 1. 구성

| 구성 요소 | 내용 |
|---|---|
| 프런트엔드 | Vercel `https://chatter-land.vercel.app` (그대로 유지) |
| 백엔드 | Cloud Run 서비스 `<SERVICE>` (이미지: `backend/Dockerfile`) |
| DB | Cloud SQL for MySQL 8.4(또는 8.0), DB `chatterland`, 문자셋 utf8mb4 |
| 이미지 | Artifact Registry `<REGION>-docker.pkg.dev/<PROJECT_ID>/<REPOSITORY>/backend:<커밋 SHA>` |
| 음성 파일 | Cloud Storage 버킷을 Cloud Run 볼륨으로 마운트(`/mnt/audio`) — 컨테이너 디스크는 재시작 시 사라진다 |
| 배포 | `.github/workflows/deploy-backend.yml` — main의 CI 성공 후 자동 |

## 2. 반드시 정해야 할 것(승인 필요)

### 2.1 음성 인식(whisper.cpp)
측정(개발 Mac M2, 2.4초 음성, large-v3-turbo q5_0): GPU(Metal) **2.3초**, CPU만 **27~35초**, small 모델 CPU **7초**.
Cloud Run 기본은 CPU만 있으므로 지금 모델 그대로면 녹음 한 번에 수십 초가 걸리고 `WHISPER_TIMEOUT`(60초)에 걸릴 수 있다.

| 선택지 | 장점 | 단점·비용(대략, 공식 가격표로 재확인) |
|---|---|---|
| A. Cloud Run GPU(NVIDIA L4) + CUDA 빌드 | 지금 모델·정확도 유지, 수 초 처리 | GPU 지원 리전 제한(서울 미지원일 수 있음), 실행 중 시간당 약 $0.7~1. 최소 인스턴스 0이면 콜드 스타트 수십 초 |
| B. CPU + 작은 모델(small 등) | 비용 낮음 | 인식 정확도 하락 → 텍스트 일치율 의미가 바뀜(교육적 판단 필요), 그래도 10초 이상 |
| C. 외부 음성 인식 API(SPEECH_ENGINE=external) | 인프라 단순 | 아동 음성을 외부로 전송(동의 문구·개인정보 검토 필요), 현재 코드의 외부 제공자 형식에 맞는 서비스 연동 개발 필요 |
| D. CPU 4 vCPU 그대로 | 추가 개발 없음 | 녹음당 30~60초 이상, 사용 불가에 가까움 |

Dockerfile은 **현재 기능을 그대로 유지**(large-v3-turbo + Silero VAD, CPU 빌드)하도록 준비했다. 결정 후 바꾼다.

### 2.2 Cloud SQL 시간대
앱 SQL이 `CURDATE()`, `DATE(created_at)`로 '오늘·기간'을 계산한다. 개발 DB는 KST, Cloud SQL 기본은 UTC → **데이터베이스 플래그 `default_time_zone=+09:00` 필수**(없으면 집계가 9시간 어긋남).

### 2.3 기존 GCP 데이터
이 PC에는 gcloud가 없어 기존 Cloud SQL 데이터 유무를 확인하지 못했다. 이전 전에 확인:
- 대상 DB가 **비어 있음** → `import_dev_dump.sh`로 전체 복구(스크립트가 빈 DB만 허용).
- `chatterland_cloudsql.sql`(기본 스키마·콘텐츠)로 이미 만들었고 사용자 데이터 없음 → 그 DB를 백업한 뒤 **새 DB 이름**(예: `chatterland_v2`)에 복구하고 앱을 새 DB로 연결하는 방식 권장(기존 DB는 그대로 보존).
- 운영 사용자 데이터가 있음 → 병합하지 않는다. 충돌 목록(같은 이메일, 같은 ID)을 뽑아 별도 결정.

## 3. 사전 준비(승인 후, 프로젝트 관리자 계정으로)

```bash
gcloud auth login && gcloud config set project <PROJECT_ID>
gcloud services enable run.googleapis.com sqladmin.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com iamcredentials.googleapis.com storage.googleapis.com

# Artifact Registry
gcloud artifacts repositories create <REPOSITORY> --repository-format=docker --location=<REGION>

# Cloud SQL (예: Enterprise, 공유 코어는 개발·소규모용)
gcloud sql instances create <INSTANCE> --database-version=MYSQL_8_4 --region=<REGION> \
  --tier=db-g1-small --storage-size=10GB --storage-auto-increase \
  --backup-start-time=18:00 --enable-point-in-time-recovery \
  --database-flags=default_time_zone=+09:00,character_set_server=utf8mb4
gcloud sql databases create chatterland --instance=<INSTANCE> --charset=utf8mb4 --collation=utf8mb4_unicode_ci
gcloud sql users create chatterland_app --instance=<INSTANCE> --password=<강한 비밀번호: 직접 입력>
# 앱 계정은 데이터 조작 권한만(스키마 변경 X). 접속 후: GRANT SELECT,INSERT,UPDATE,DELETE ON chatterland.* TO 'chatterland_app'@'%';

# 음성 파일 버킷(공개 접근 금지)
gcloud storage buckets create gs://<AUDIO_BUCKET> --location=<REGION> --uniform-bucket-level-access --public-access-prevention
```

### 3.1 서비스 계정(최소 권한)
| 계정 | 용도 | 권한 |
|---|---|---|
| `chatterland-run@` (실행) | Cloud Run 런타임 | `roles/cloudsql.client`, 각 비밀값 `roles/secretmanager.secretAccessor`, 버킷 `roles/storage.objectAdmin`(해당 버킷만) |
| `chatterland-deploy@` (배포) | GitHub Actions | `roles/run.developer`, 저장소 `roles/artifactregistry.writer`, 실행 계정에 대한 `roles/iam.serviceAccountUser` — **DB 접속 권한 없음** |

### 3.2 Workload Identity Federation(키 JSON 없음)
```bash
gcloud iam workload-identity-pools create github --location=global
gcloud iam workload-identity-pools providers create-oidc chatterland --location=global --workload-identity-pool=github \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping=google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref \
  --attribute-condition="assertion.repository=='cold-dew2/ChatterLand' && assertion.ref=='refs/heads/main'"
gcloud iam service-accounts add-iam-policy-binding chatterland-deploy@<PROJECT_ID>.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github/attribute.repository/cold-dew2/ChatterLand"
```
신뢰 조건이 `cold-dew2/ChatterLand` + `refs/heads/main`이므로 다른 저장소·브랜치는 토큰을 받을 수 없다.

### 3.3 Secret Manager(값은 콘솔/CLI에서 직접 입력)
| 비밀 이름 | 앱 환경변수 | 비고 |
|---|---|---|
| `chatterland-db-password` | `DB_PASSWORD` | 운영 전용 새 강한 비밀번호(개발 비밀번호 재사용 금지) |
| `chatterland-jwt-secret` | `JWT_SECRET` | `openssl rand -hex 32` 로 새로 생성 |
| `chatterland-ai-api-key` | `AI_API_KEY` | Gemini 키(운영용 별도 키 권장) |
| `chatterland-mail-password` | `MAIL_PASSWORD` | SMTP를 쓸 때만 |

### 3.4 Cloud Run 최초 배포(승인 후 1회, 이후는 workflow가 이미지만 교체)
```bash
gcloud run deploy <SERVICE> --region=<REGION> --image=<이미지@digest> \
  --service-account=chatterland-run@<PROJECT_ID>.iam.gserviceaccount.com \
  --add-cloudsql-instances=<PROJECT_ID>:<REGION>:<INSTANCE> \
  --cpu=4 --memory=8Gi --concurrency=4 --min-instances=0 --max-instances=3 --timeout=300 \
  --allow-unauthenticated \
  --set-env-vars=SQL_INIT_MODE=never,DB_USERNAME=chatterland_app,DB_POOL_SIZE=5,CORS_ALLOWED_ORIGINS=https://chatter-land.vercel.app,AUDIO_STORAGE_PATH=/mnt/audio,AI_API_URL=<Gemini 주소>,AI_MODEL=<모델>,TZ=Asia/Seoul \
  --set-env-vars="^@^DB_URL=jdbc:mariadb://localhost/chatterland?localSocket=/cloudsql/<PROJECT_ID>:<REGION>:<INSTANCE>&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul" \
  --set-secrets=DB_PASSWORD=chatterland-db-password:latest,JWT_SECRET=chatterland-jwt-secret:latest,AI_API_KEY=chatterland-ai-api-key:latest \
  --add-volume=name=audio,type=cloud-storage,bucket=<AUDIO_BUCKET> --add-volume-mount=volume=audio,mount-path=/mnt/audio
```
- DB 연결: Cloud Run의 Cloud SQL 연결(Unix 소켓). 공인 IP 허용 목록을 열지 않는다.
- 연결 수: 인스턴스당 풀 5 × 최대 3 = 15 < Cloud SQL 최대 연결 수. 늘리면 함께 계산.
- 앱 안의 예약 작업(토큰 정리·음성 보관 기간 삭제·멈춘 분석 정리)은 인스턴스가 떠 있을 때만 돈다(최소 인스턴스 0이면 요청이 없을 때 멈춤). 모두 여러 번 실행해도 안전한 작업이지만, 정기 실행이 꼭 필요하면 최소 인스턴스 1 또는 Cloud Scheduler 검토.

### 3.5 GitHub 설정
저장소 Settings → Secrets and variables → **Variables**(비밀 아님): `GCP_PROJECT_ID`, `GCP_REGION`, `GCP_ARTIFACT_REPOSITORY`, `CLOUD_RUN_SERVICE`, `GCP_WORKLOAD_IDENTITY_PROVIDER`(`projects/<번호>/locations/global/workloadIdentityPools/github/providers/chatterland`), `GCP_DEPLOY_SERVICE_ACCOUNT`.
Settings → Environments → `production` 생성(필요하면 배포 승인자 지정).

## 4. 데이터 이전(개발 DB → Cloud SQL, 승인 후)

1. 백업: `~/chatterland-backups/<시각>/` (덤프·행 수·지문·음성 파일·SHA-256). 저장소에 넣지 않는다.
2. Cloud SQL Auth Proxy로 접속: `cloud-sql-proxy <PROJECT_ID>:<REGION>:<INSTANCE> --port 3307`
3. 점검만: `MYSQL_PWD=<관리 계정 비밀번호> bash backend/scripts/gcp/import_dev_dump.sh --backup-dir <백업> --port 3307 --user <관리 계정> --database chatterland`
4. 복구: 위 명령 + `--audio-from <개발 PC의 storage/audio 절대 경로> --audio-to /mnt/audio --confirm`
   - 대상에 행이 하나라도 있으면 중단한다. 복구 후 26개 테이블 행 수·핵심 11개 테이블 지문·음성 경로를 자동 검증한다.
5. 음성 파일: `tar -xzf audio-files.tgz && gcloud storage cp audio/* gs://<AUDIO_BUCKET>/`
6. 확인: 기존 계정 로그인, 학생 홈·숙제·기록, 선생님 학생 목록(실제 운영 DB에는 테스트 데이터를 만들지 않는다).

개발 DB는 이전·검증이 끝날 때까지 기준 데이터로 보존한다.

## 5. 스키마 변경 정책
- 운영 앱은 `SQL_INIT_MODE=never` — 배포마다 schema.sql·data.sql을 실행하지 않는다(앱 DB 계정도 DDL 권한 없음).
- 스키마가 바뀌는 배포: ① Cloud SQL 백업(`gcloud sql backups create`) ② 테스트 DB·이전 검증용 DB에서 변경 SQL 검증 ③ 승인 후 관리 계정으로 실행(`schema.sql`의 추가분은 IF NOT EXISTS·정보스키마 조건으로 반복 실행 안전) ④ 앱 배포.
- 새 컬럼을 쓰는 코드는 스키마 변경이 먼저 적용된 뒤 배포한다.

## 6. 자동 배포(workflow) 동작
1. 모든 push·PR: `ci.yml`(단위·통합·프런트·E2E) — 배포 없음.
2. main push의 CI가 **성공**하면 `deploy-backend.yml` 실행(다른 브랜치·실패한 CI는 실행 안 됨). 동시에 하나만.
3. 같은 커밋이 이미 서비스 중이면 건너뜀.
4. 이미지 빌드 → `backend:<SHA>` 업로드 → 트래픽 없이 새 리비전 배포(태그 `sha-<7자리>`) → 태그 주소 점검(`/api/v1/centers` 200, 인증 401, 입력 검증 400) → 통과 시 트래픽 100%.
5. 결과 요약에 커밋·이미지 digest·새/이전 리비전 기록.

## 7. 되돌리기
```bash
gcloud run revisions list --service <SERVICE> --region <REGION>
gcloud run services update-traffic <SERVICE> --region <REGION> --to-revisions <이전 리비전>=100
```
DB는 Cloud SQL 자동 백업·시점 복구로 되돌린다(복구는 새 인스턴스로 복원 후 확인).

## 8. Vercel
- 프로젝트 Settings → Environment Variables → `NEXT_PUBLIC_API_BASE_URL` = Cloud Run 주소(https, 끝 `/` 없이). Production에만 설정.
- `NEXT_PUBLIC_*`는 빌드 때 들어가므로 **설정 후 재배포** 필요. (현재 배포본은 `http://localhost:8080`을 쓰고 있어 실제 백엔드에 연결되지 않는다.)
- 백엔드 `CORS_ALLOWED_ORIGINS=https://chatter-land.vercel.app` (미리보기 배포 주소는 허용하지 않음. 필요하면 별도 결정).
- 인증은 `Authorization` 헤더 + 재발급 토큰 본문 전송(쿠키 미사용) → SameSite·서드파티 쿠키 문제 없음. HTTPS↔HTTPS라 Mixed Content 없음.
