# 학생·교사 E2E 시나리오

자동화 위치: `frontend/e2e/*.spec.ts`(Playwright, Google Chrome). 마이크는 Chrome 가짜 장치(`--use-file-for-fake-audio-capture`)에 합성음 WAV를 넣어 실제 녹음 경로(MediaRecorder → 16kHz WAV 변환 → 업로드 → whisper.cpp)를 그대로 거친다. 녹음 길이(말하는 시간)만 고정 시간이고, 그 밖의 대기는 화면 상태와 API 응답으로 판단한다.

| ID | 시나리오 | 파일 | 단계 요약 | 기대 결과 |
|---|---|---|---|---|
| E2E-01 | 학생 로그인 → 홈 → 숙제 확인 → 학습 화면 이동 → 숙제 완료 | auth-and-homework.spec.ts | 교사가 API로 숙제 배정 → 학생 로그인 → 홈 "오늘의 숙제" → 숙제 카드 → "연습하러 가기" → 홈 "전체 보기" → "완료" | 홈에 "완료 0 / 1", 학습 화면 이동, 완료 후 "남은 숙제가 없어요", API에서 done=true |
| E2E-02/03 | 낱말 녹음 → 결과 → 기록 저장 → 새로고침 후 유지 | student-word.spec.ts | ㄹ 발음 "라디오" 녹음 2.5초 → 분석 | 텍스트 일치율·"낱말"·발음 지표 3개 "미평가", "학습 기록에 저장했어요.", 화면 수치 = API 기록, 홈 새로고침 후 최근 기록에 같은 수치 |
| E2E-04 | 문장 녹음 → 결과 | student-sentence.spec.ts | "오늘은 날씨가 좋아요." 4.2초 녹음 | "문장" 유형, 단어 비교, 기록 저장 |
| E2E-05 | 말하는 도중 녹음 종료 → 판정 보류 → 다시 녹음 | student-sentence.spec.ts | 같은 문장을 1.5초만 녹음 | "판정 보류"와 "말이 끝나기 전에 녹음이 멈췄어요" 안내, 다시 녹음 시 초기화 |
| E2E-06 | 언어재활 녹음 → 자동 오류 후보 → 교사 확정 → 새로고침 후 유지 | therapy-review.spec.ts | THERAPY 학생이 "다디오" 녹음 → 교사 로그인 → 학생 상세 → 음성 검토 → 후보 체크 + "ㄹ 왜곡" 직접 추가 → 판정 저장 → 새로고침 | 학생 화면 "선생님 확인 대기"(일치율 없음), 교사 화면 "자동 오류 후보 있음"·목표 음소 위치, 새로고침 후 확정 오류 2건과 자동 후보가 함께 유지 |
| E2E-07 | 권한 없는 교사의 접근 차단 | therapy-review.spec.ts | 다른 교사가 `/teacher/students/{id}` 직접 접속 | "담당 학생 정보에 접근할 수 없습니다." 안내, 학생 정보 미표시, API 403 |
| E2E-08 | 분석 서버 오류 → 안내 → 재시도 | student-word.spec.ts | `/speech/analyze` 첫 요청만 502로 대체(Playwright route) | 서버 오류 문구 표시, 결과 없음 → "다시 분석하기"로 정상 결과, 기록은 성공한 1건만 |
| E2E-09 | 학습 이력 → 교사 분석 수치 일치 | student-word.spec.ts | 학생 연습 저장 → 교사 분석 API·화면 | 교사 분석 averageMatchRate = 학생 기록 평균, 화면 "평균 텍스트 일치율"에 같은 값 |
| E2E-10 | 로그아웃 → 보호 화면 재접근 | auth-and-homework.spec.ts | 마이페이지 로그아웃 → `/student/history` 직접 접속 → 학생으로 `/teacher` 접속 | 학생 화면에 머물지 않음, 토큰 삭제, 역할이 다른 화면은 학생 홈으로 이동 |
| AUTH-E2E-1 | 로그인 실패·역할 불일치·가입 검증 | auth-and-homework.spec.ts | 틀린 비밀번호 → 선생님 탭으로 학생 계정 로그인 → 빈 가입 제출 | 서버 오류 문구, 역할 불일치 안내, 필수값·필수 동의 안내 |
| AUTH-E2E-2 | 마이크 권한 거부 | auth-and-homework.spec.ts | getUserMedia가 NotAllowedError를 내도록 설정 | "마이크 권한이 거부되었어요" 안내, 녹음 시작 버튼 유지 |
| AUTH-E2E-3 | 아이디 찾기 → 비밀번호 재설정 | auth-and-homework.spec.ts | 마스킹 이메일 확인 → mailpit에서 실제 코드 수신 → 틀린 코드 → 올바른 코드 → 새 비밀번호 | 원본 이메일 미노출, 오류 안내, 새 비밀번호로 로그인 200 |

자동화하지 않은 흐름과 사유는 `test-results.md` 6절에 적는다.
