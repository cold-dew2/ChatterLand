# 기능별 테스트 케이스

상태: **PASS**(실행·통과) / **FAIL**(실행·실패) / **BLOCKED**(환경·데이터 부족으로 실행 불가) / **NOT RUN**(자동화하지 않았거나 이번 주기에 실행하지 않음) / **미구현**(기능 없음).
실제 결과는 2026-10-02 최종 실행 기준이다(`test-results.md`). "자동화" 열은 해당 케이스를 검증하는 테스트(클래스#메서드 또는 파일 › 이름)이다.
음성 데이터는 모두 합성음·생성 신호이며 실제 아동 음성이 아니다.

## 4.1 인증 및 계정

| ID | 계층 | 사전 조건·데이터 | 단계 | 기대 결과 | 실제 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|---|
| AUTH-01 | E2E / 통합 | 가입된 학생 | 이메일·비밀번호로 로그인 | 토큰 저장, 학생 홈 이동 | 학생 홈 이동, 토큰 저장 | PASS | LoginPage.test › stores tokens…; e2e helpers.loginUi(전 시나리오); BackendApiIntegrationTest#studentSignupAuthenticates… |
| AUTH-02 | E2E / 통합 | 가입된 학생 | 틀린 비밀번호 | 401, "이메일 또는 비밀번호를 확인해 주세요." | 동일 문구 표시 | PASS | auth-and-homework.spec › AUTH-E2E 로그인 실패…; AuthorizationIntegrationTest#wrongPasswordAndUnknownAccountGetTheSameFailure |
| AUTH-03 | 통합 | 없는 이메일 | 로그인 | 틀린 비밀번호와 같은 401 응답(계정 존재 노출 없음) | 같은 메시지 | PASS | AuthorizationIntegrationTest#wrongPasswordAndUnknownAccount… |
| AUTH-04 | 단위·기능 / 통합 | 빈 값, `wrong-email`, 7자 비밀번호 | 제출 | 화면 필드 오류, API 미호출 / 서버 400 | 필드 오류, API 0회 / 400 | PASS | validation.test; LoginPage.test › shows field errors…; RequestValidationTest#signup…; AuthorizationIntegrationTest#malformedRequests… |
| AUTH-05 | 기능 | 로그인 요청 진행 중 | 제출 2회 | API 1회만 호출 | 1회 | PASS | LoginPage.test › ignores a second submit… |
| AUTH-06 | E2E / 기능 | 학생 계정 | 선생님 탭으로 로그인 | 역할 불일치 안내, 발급 refresh 토큰 폐기, 토큰 미저장 | 안내 표시, revoke 호출 | PASS | auth-and-homework.spec › AUTH-E2E 로그인 실패…; LoginPage.test › rejects a role mismatch… |
| AUTH-07 | E2E / 통합 | 로그인 상태 | 로그아웃 → 보호 화면 접근 | refresh 토큰 폐기, 학생 화면 접근 불가 | 루트로 이동, 토큰 삭제, refresh 401 | PASS | auth-and-homework.spec › E2E-10; AuthorizationIntegrationTest#logoutRevokesTheRefreshToken |
| AUTH-08 | 단위 | access 401, refresh 유효/무효 | API 호출 | 1회 갱신 후 재시도 / 실패 시 토큰 삭제·세션 만료 이벤트 | 기대대로 | PASS | client.test › refreshes once…, clears tokens… |
| AUTH-09 | 통합 | refresh 토큰 | 같은 토큰 2회 갱신 | 두 번째 401(재사용 차단) | 401 | PASS | BackendApiIntegrationTest#refreshTokenCanOnlyBeRotatedOnce |
| AUTH-10 | 기능 / E2E | 세션 만료 상태 | 로그인 화면 | "로그인 시간이 만료되었어요" 안내 | 표시 | PASS | LoginPage.test › explains an expired session (E2E 실제 만료 대기는 NOT RUN) |
| AUTH-11 | E2E | 학생으로 로그인 | `/teacher` 접근 | 학생 홈으로 이동 | 이동 | PASS | auth-and-homework.spec › E2E-10 |
| AUTH-12 | E2E / 통합 | 가입된 교사 | 아이디 찾기 | 일부 가린 이메일만 표시, 불일치 시 안내 | 원본 이메일 미노출 | PASS | auth-and-homework.spec › 아이디 찾기…; AccountRecoveryIntegrationTest#findIdReturnsOnlyMaskedEmails… |
| AUTH-13 | E2E / 통합 | mailpit / GreenMail | 코드 요청 → 틀린 코드 → 올바른 코드 → 새 비밀번호 | 남은 시도 안내, 변경 후 새 비밀번호 로그인, 기존 세션 폐기 | 실제 메일 수신, "남은 시도 4회", 로그인 200 | PASS | auth-and-homework.spec › …비밀번호 재설정…; AccountRecoveryIntegrationTest#passwordResetFullFlow… |
| AUTH-14 | 통합 | 만료 코드, 시도 초과, 재발송 제한 | 검증 요청 | 거부, 같은 응답으로 계정 존재 숨김 | 기대대로 | PASS | AccountRecoveryIntegrationTest#expiredCode…, #unknownEmailAndResendCooldown… |
| AUTH-15 | 통합 | MAIL_HOST 빈 값 / SMTP 연결 불가 / Mailpit | 재설정 요청 | 503 MAIL_NOT_CONFIGURED(가입 여부 무관) / 503 MAIL_DELIVERY_FAILED·요청 롤백·즉시 재시도 가능 / 실제 SMTP 수신 | 수정 후 기대대로(DEF-03) | PASS | MailNotConfiguredIntegrationTest, MailDeliveryFailureIntegrationTest, MailpitDeliveryIntegrationTest |
| AUTH-16 | E2E / 단위 | 빈 가입 폼, 만 14세 미만 | 가입 | 필수값·필수 동의·법정대리인 안내 / 서버도 동의 없으면 거부 | 기대대로 | PASS | auth-and-homework.spec › AUTH-E2E 로그인 실패…(가입); consentValidation.test; ConsentAndCenterIntegrationTest#signupRequiresPrivacyConsent… |

## 4.2 학생 기능

| ID | 계층 | 사전 조건·데이터 | 단계 | 기대 결과 | 실제 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|---|
| STU-01 | E2E | 숙제 1건 배정 | 로그인 → 홈 | "오늘의 숙제 완료 0 / 1", 숙제 제목 | 표시 | PASS | auth-and-homework.spec › E2E-01 |
| STU-02 | E2E | 숙제 카드 | 숙제하기 → 연습하러 가기 | 연습 유형 화면 이동 | 이동 | PASS | E2E-01 |
| STU-03 | E2E / 통합 | 미완료 숙제 | 완료 | "남은 숙제가 없어요", 서버 done=true | 기대대로 | PASS | E2E-01; BackendApiIntegrationTest#studentCanViewAssignedHomeworkAndMarkItComplete |
| STU-04 | E2E | 기록 없는 학생 | 홈 | "아직 학습 기록이 없어요" | 표시 | PASS | student-word.spec › E2E-02/03 |
| STU-05 | 기능 / E2E | 가짜 마이크 | 녹음 시작·중지·미리듣기·다시 녹음 | 상태 전환, 미리듣기 오디오, 초기화 | 기대대로 | PASS | useAudioRecorder.test › records, stops…; E2E-02/03, E2E-05 |
| STU-06 | 기능 / E2E | 권한 거부 | 녹음 시작 | "마이크 권한이 거부되었어요", 다시 시도 가능 | 기대대로 | PASS | useAudioRecorder.test › explains a denied…; auth-and-homework.spec › 마이크 권한 거부 |
| STU-07 | 기능 | 마이크 없음/미지원 브라우저 | 녹음 시작 | 각각 안내 | 기대대로 | PASS | useAudioRecorder.test |
| STU-08 | E2E | 분석 요청 | 분석하기 | 분석 중 표시 후 결과 | 결과 표시 | PASS | E2E-02/03 (로딩 문구는 별도 단언 없음) |
| STU-09 | E2E | 분석 API 502(주입) | 분석 → 다시 분석하기 | 오류 안내, 결과 없음 → 재시도 성공, 기록 1건 | 기대대로 | PASS | student-word.spec › E2E-08 |
| STU-10 | E2E | 분석 성공 | 저장 확인 → 홈 새로고침 | 저장 안내, 최근 기록에 같은 수치 | 기대대로 | PASS | E2E-02/03 |
| STU-11 | 기능 / E2E | 분석 요청 진행 중 | 분석하기 연속 클릭, 실패 후 재시도, 새 녹음 | 요청 1회, 재시도는 같은 Idempotency-Key, 새 녹음은 새 키 | 기대대로 | PASS | SpeechActivityScreen.test (3건); student-word.spec › E2E-11 |

## 4.3 교사 기능

| ID | 계층 | 사전 조건·데이터 | 단계 | 기대 결과 | 실제 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|---|
| TCH-01 | 통합 | 교사, 같은 센터 학생 | 담당 등록·목록·상세·수정·해제 | 담당 학생만 조회·수정 | 기대대로 | PASS | BackendApiIntegrationTest#teacherCanManageAssignedStudentHomeworkAndAnalytics |
| TCH-02 | 통합 | 담당 학생 | 숙제 등록·수정·삭제·완료 확인 | 저장·수정 반영, 학생 완료 반영 | 기대대로 | PASS | BackendApiIntegrationTest#teacherCanManage…, #studentCanView… |
| TCH-03 | 통합 / 단위 | 과거 마감일, 0분 | 숙제 등록 | 400 | 400 | PASS | AuthorizationIntegrationTest#malformedRequests…; RequestValidationTest#homework… |
| TCH-04 | E2E / 기능 | 언어재활 학생 녹음 | 음성 검토 탭 | 자동 분석(유형·상태·목표 음소 위치·후보·발화 시간) 표시, "미확정" 표기 | 기대대로 | PASS | therapy-review.spec › E2E-06; SpeechReviewPanel.test › shows automatic evidence… |
| TCH-05 | E2E / 기능 | 자동 후보 1건 | 후보 체크 + "ㄹ 왜곡" 직접 추가 + 판정 저장 | 체크·추가한 것만 confirmedErrors로 전송 | 요청 본문 일치 | PASS | E2E-06; SpeechReviewPanel.test › requires a judgement and sends only… |
| TCH-06 | 기능 | 판정 미선택 | 검토 완료 | "검토 결과를 선택해 주세요.", API 미호출 | 기대대로 | PASS | SpeechReviewPanel.test |
| TCH-07 | E2E / 통합 | 검토 저장 후 | 새로고침·재검토(수정)·확정 오류 비우기 | 확정 결과 유지/교체/삭제, 자동 후보 불변 | 기대대로 | PASS | E2E-06; SpeechAnalysisIntegrationTest#teacherCanReviseConfirmedErrors… |
| TCH-08 | E2E / 통합 | 다른 교사의 학생 | 상세 URL 직접 접속, API 호출 | 안내 문구, 학생 정보 미표시, API 403/404 | 기대대로(수정 후) | PASS | therapy-review.spec › E2E-07; AuthorizationIntegrationTest#teacherCanOnlyAccessLinkedStudents |
| TCH-09 | E2E / 통합 | 학생 연습 기록 | 교사 분석 화면 | 평균 텍스트 일치율 = 기록 평균 | 일치 | PASS | student-word.spec › E2E-09; SpeechAnalysisIntegrationTest#reportStatisticsMatchStoredAttempts… |
| TCH-10 | 통합 / 단위 | 기록 | PDF 다운로드 | PDF, 한글 텍스트·이름·미평가·검토 메모 포함, 여러 쪽 | 텍스트 추출 확인 | PASS | ReportPdfGeneratorTest; SpeechAnalysisIntegrationTest#therapyLearner…, #reportStatistics… |
| TCH-11 | 기능 | 검토 목록 로딩 실패 | 다시 시도 | 오류 상태 → 재시도 성공 | 기대대로 | PASS | SpeechReviewPanel.test › shows an error state… |
| TCH-12 | E2E / 기능 | 담당 학생 | 숙제 배정 모달: 취소·검증·서버 거부·배정 | 취소로 닫힘, 검증 문구, 거부 시 모달 유지·사유 표시, 배정 후 학생 홈 반영 | 기대대로 | PASS | teacher-homework.spec › E2E-12; HomeworkModal.test (5건) |

## 4.4 음성 분석

| ID | 계층 | 데이터 | 단계 | 기대 결과 | 실제 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|---|
| SPE-01 | 통합(whisper) | 합성음 "라디오" | 분석 | WORD, 후보 없음, ㄹ 어두 초성 위치, textMatchRate 100, 점수 없음 | 기대대로 | PASS | SpeechAnalysisIntegrationTest#wordAnalysisRecordsTargetPhonemePositionsAndRepetition |
| SPE-02 | 통합(whisper) | "오늘은 날씨가 좋아요." | 분석 | SENTENCE, 발화 시간(VAD) 측정, 녹음 즉시 삭제 | 기대대로 | PASS | #generalLearnerGetsSentenceMatchWithoutPronunciationScore |
| SPE-03 | 통합(whisper) | "다디오"(목표 라디오) | 분석 | ERROR_CANDIDATES, ㄹ 초성 대치 후보(목표 음소), textMatchRate < 100 | 후보 ㄹ→ㅌ(Whisper가 "타디오"로 인식) | PASS | #mispronouncedWordProducesUnconfirmedCandidatesNotAScore |
| SPE-04 | 통합(whisper) | "토끼가 걸어가요"(목표 …숲속을…) | 분석 | 음절 생략 후보 3건, 단어 누락, 70% | 기대대로 | PASS | #sentenceOmissionAndAdditionProduceSyllableCandidates |
| SPE-05 | 통합(whisper) | "오늘은 날씨가 정말 좋아요" | 분석 | 음절 첨가 후보 2건, 77.78% | 기대대로 | PASS | 같은 테스트 |
| SPE-06 | 단위 | 자모 쌍 | 대치·생략(초성/종성)·첨가·겹받침 | 자리별 후보 | 기대대로 | PASS | HangulPhonemeAnalyzerTest |
| SPE-07 | 통합 / 단위 | 같은 문항 2회 | 반복 녹음 | previousAttempts, 같은 인식 수, 반복 후보 | 기대대로 | PASS | SpeechAnalysisIntegrationTest#word…, #mispronounced…; SpeechAssessmentEvaluatorTest#repetition… |
| SPE-08 | 단위 / 통합 | VAD 구간 | 발화 시간 계산 | 말소리·앞뒤 무음·쉼·초당 음절, VAD 없으면 UNAVAILABLE | 기대대로 | PASS | SpeechAssessmentEvaluatorTest; LocalWhisperRecognitionServiceTest#parsesVadSegments…, #recognitionResultCarriesVad… |
| SPE-09 | 통합(whisper) / E2E | 말 도중 끊긴 녹음 | 분석 | HOLD(SPEECH_CUT_OFF_END), 화면 "판정 보류" | 기대대로 | PASS | #cutOffRecordingIsHeldInsteadOfJudged; student-sentence.spec › E2E-05 |
| SPE-10 | 단위 | 짧은 말소리, 클리핑, 작은 소리, 숫자 인식, 길이 불일치, 시작 잘림 | 평가 | 각 보류 사유 | 기대대로 | PASS | SpeechAssessmentEvaluatorTest#uncertainRecordingsAreHeld |
| SPE-11 | 통합 | 무음, 빈 파일, WAV 아님 | 업로드 | 422 / 400 / 415, 완료 결과 없음 | 기대대로 | PASS | #silentOrInvalidRecordingFailsWithoutStoringResult |
| SPE-12 | 통합(whisper) | 배경 소음, 1kHz 신호음 | 업로드 | 422, 인식 텍스트 저장 없음 | 기대대로 | PASS | #noiseAndNonSpeechAreRejectedWithoutResult; LocalWhisperRecognitionServiceTest#rejectsBackgroundNoise… |
| SPE-13 | 단위(대체 실행 파일) | 너무 짧은/긴 녹음 | 인식 | 422, 추론 미실행 | 기대대로 | PASS | WhisperProcessFailureTest#tooShortTooLongAndSilent… |
| SPE-14 | 단위(대체 실행 파일) | whisper-cli 종료 코드 3 | 인식 | 502 | 502 | PASS | WhisperProcessFailureTest#nonZeroExit… |
| SPE-15 | 단위(대체 실행 파일) | 응답 없는 프로세스 | 인식(0.8초 제한) | 504, 즉시 종료 | 504 | PASS | WhisperProcessFailureTest#hangingProcess… |
| SPE-16 | 단위(대체 실행 파일) | 결과 파일 없음·손상·형식 오류 | 인식 | 502, 가짜 결과 없음 | 502 | PASS | WhisperProcessFailureTest#missingOrCorrupt… |
| SPE-17 | 단위 | 실행 파일·모델 없음 | 인식 | 503 | 503 | PASS | WhisperProcessFailureTest#missingExecutableOrModel…; LocalWhisperRecognitionServiceTest#reportsUnavailableModel… |
| SPE-18 | 단위(mock) | 결과 저장 0건 | 분석 | 500, FAILED 기록, 녹음 삭제 | 기대대로 | PASS | SpeechAnalysisServiceImplTest#databaseSaveFailure… |
| SPE-19 | 단위(mock) | 인식 실패/환각 문구/빈 결과 | 분석 | FAILED 또는 422, 완료 저장 없음 | 기대대로 | PASS | SpeechAnalysisServiceImplTest |
| SPE-20 | 통합 | 같은 분석으로 기록 저장 2회 | 저장 | 기록 1건 | 1건 | PASS | #reportStatistics…; BackendApiIntegrationTest#practiceAttemptIsBound…Idempotent |
| SPE-21 | 통합 | 측정하지 않은 score 전송 | 기록 저장 | 409 | 409 | PASS | #generalLearner… |
| SPE-22 | 통합 / 기능 | 모든 로컬 분석 | 조회 | pronunciation/speechRate/fluency/overall 점수 없음, "미평가" | 기대대로 | PASS | SpeechAnalysisIntegrationTest 다수; SpeechAnalysisResult.test |
| SPE-23 | 통합 / 기능 | 판정 보류 결과 | 조회·표시 | assessmentStatus=HOLD, 정상 결과와 다른 표시 | 기대대로 | PASS | #cutOff…; SpeechAnalysisResult.test › distinguishes a held result… |
| SPE-24 | 통합 | 분석 결과 | 조회 | analysisVersion=jamo-align-v1, modelName 저장 | 기대대로 | PASS | #generalLearner… |
| SPE-25 | 통합(whisper) / 단위 | 같은 녹음·같은 키 | 순차 재시도, 동시 4건, 다른 녹음+같은 키, 키 없음, 실패 후 재시도 | 분석 1건·같은 analysisId(reused), 동시에도 1건, 409, 키 없거나 다르면 별개, 실패 시 키 해제 | 기대대로 | PASS | SpeechIdempotencyIntegrationTest (5건); SpeechAnalysisServiceImplTest (중복 5건); E2E-11 |
| SPE-26 | — | 실제 아동 음성 | 인식 정확도 | — | — | BLOCKED | 허가된 아동 음성 샘플 없음 |
| SPE-27 | — | 발음(음소) 점수 | — | — | — | 미구현 | 검증된 음소 평가 모델 미도입(성공 대상 아님) |

## 4.5 데이터 및 리포트

| ID | 계층 | 단계 | 기대 결과 | 실제 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|
| DAT-01 | 통합 | 학습 기록 생성·조회 | 기록·이력 일치 | 일치 | PASS | SpeechAnalysisIntegrationTest#generalLearner… |
| DAT-02 | 통합 | 다른 학생 기록 추가 | 통계에 섞이지 않음 | 2건 유지 | PASS | #reportStatistics… |
| DAT-03 | 통합 | 기간별 통계(평균 텍스트 일치율·기록 수·숙제 수행 필드) | 저장 기록과 같은 값 | 평균·건수 일치 | PASS | SpeechAnalysisIntegrationTest#reportStatistics…; BackendApiIntegrationTest#teacherCanManage…(숙제 집계 필드 존재) |
| DAT-03b | 통합 / 단위 | 이전 기간 비교 수치 | 같은 일수·바로 앞 기간, 경계 시각 기록은 한 기간에만, 기록 없음·숙제 0건은 null | 기대대로 | PASS | AnalyticsPeriodIntegrationTest (2건); PeriodComparisonTest (3건) |
| DAT-04 | 통합 | 기록 없는 기간 | "기록 없음"/null | 기대대로 | PASS | BackendApiIntegrationTest#teacherCanManage…; ReportPdfGeneratorTest |
| DAT-05 | 통합 | 교사 확정 전후 | 자동 결과 불변, 확정 결과만 변경 | 기대대로 | PASS | #teacherCanRevise…, #therapyLearner… |
| DAT-06 | 단위(mock) | 저장 실패 | 실패 상태 기록, 녹음 삭제 | 기대대로 | PASS | SpeechAnalysisServiceImplTest#databaseSaveFailure… |
| DAT-07 | 통합 | 보관 기간 만료 | 만료 녹음 삭제·재시도·경로 검증 | 기대대로 | PASS | AudioRetentionIntegrationTest |
| DAT-08 | 통합 | 빈 DB에 스키마 적용 | 모든 테이블 생성, 반복 실행 안전 | MariaDB 13 빈 DB에서 17개 테이블 생성 | PASS | 전체 통합 테스트(테스트 DB 최초 실행); BackendApiIntegrationTest#exerciseItemSeedIsNotDuplicatedOnRestart |

## 4.6 보안 및 권한

| ID | 계층 | 단계 | 기대 결과 | 실제 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|
| SEC-01 | 통합 | 토큰 없이·위조 토큰·Basic 인증으로 보호 API 8종 | 401 UNAUTHORIZED | 401 | PASS | AuthorizationIntegrationTest#protectedApisRequireAuthentication… |
| SEC-02 | 통합 | 학생이 교사 API 5종 / 교사가 학생 API 4종 | 403 | 403 | PASS | #studentCannotCallTeacherApis… |
| SEC-03 | 통합 | 교사가 담당 밖 학생 조회·분석·리포트·PDF·음성·검토·숙제 등록·정보 수정·숙제 삭제 | 403/404, 데이터 변경 없음 | 기대대로 | PASS | #teacherCanOnlyAccessLinkedStudents |
| SEC-04 | 통합 | 다른 학생의 분석 조회·기록 연결·숙제 완료 | 404/403, 변경 없음 | 기대대로 | PASS | #studentCannotReadOrUseAnotherStudents… |
| SEC-05 | 통합 | 없는 분석 ID | 404 | 404 | PASS | 같은 테스트 |
| SEC-06 | 통합 | 깨진 JSON, 형식 오류 경로 변수, 없는 경로, 잘못된 메서드·Content-Type | 400/400/404/405/415 | 수정 전 없는 경로·형식 오류가 500 → 수정 후 기대대로 | PASS | #malformedRequestsAreRejectedWithClientErrors (DEF-01) |
| SEC-13 | 통합 | CORS 사전 요청(Idempotency-Key) | 화면 출처에서 허용 | 허용 | PASS | AuthorizationIntegrationTest#corsPreflightAllowsTheIdempotencyKeyHeader… |
| SEC-07 | 통합 | 로그인·내 정보·학생 프로필 응답 | 비밀번호 해시·토큰 해시 미포함 | 미포함 | PASS | #responsesDoNotExposePasswordHashes… |
| SEC-08 | 통합 | 음성 파일 접근 | 담당 교사만 재생, 응답에 저장 경로 없음 | 기대대로(정적 리소스 매핑 없음은 코드 확인) | PASS | SpeechAnalysisIntegrationTest#therapyLearner…; AuthorizationIntegrationTest |
| SEC-09 | 통합 | 동의 기록 | 본인만 조회 | 기대대로 | PASS | ConsentAndCenterIntegrationTest#consentRecordsAreOnlyVisibleToTheirOwner |
| SEC-10 | 통합 / 단위 | 서버 로그의 민감 정보 | 비밀번호·JWT·refresh·이메일·코드·전화·보호자 이름 미기록, 원인(예외 유형·제약명)은 기록 | 수정 후 기대대로(DEF-05) | PASS | SensitiveLogIntegrationTest (2건); LogMaskingTest (3건) |
| SEC-11 | 단위 | 다른 키로 서명한 JWT | 거부 | 거부 | PASS | JwtUtilTest#rejectsTokenSignedWithAnotherKey |
| SEC-12 | 단위 / 통합 | 만료·변조·다른 키·none 알고리즘·누락 JWT | 401 UNAUTHORIZED, 유효 토큰·refresh 재발급 유지 | 기대대로 | PASS | JwtUtilTest (6건); JwtAuthenticationIntegrationTest (2건) |

## 기타

| ID | 영역 | 상태 | 비고 |
|---|---|---|---|
| ETC-01 | AI 대화 실제 응답 | BLOCKED | AI_API_URL/AI_API_KEY 없음. 미설정 시 503(ExternalProviderServiceTest), 요청 형식(같은 테스트의 MockRestServiceServer)만 PASS |
| ETC-02 | 화면 배치(1280px) | PASS | desktop-layout.spec DESK-01·02: 학생·선생님 주요 화면 가로 넘침 없음, 하단 메뉴·모달(1280×720) 확인 |
| ETC-03 | 공통 컴포넌트(Button·Input·Select·Tabs·Modal·ConfirmDialog·ErrorState) | PASS | components.test (props·이벤트·접근성 속성) |

## 코드 리뷰 주기 추가 케이스 (2026-10-02)

| ID | 계층 | 사전 조건·데이터 | 단계 | 기대 결과 | 실제 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|---|
| AUTH-17 | 단위 | 만료 토큰, refresh 1회용 | 동시에 3개 요청 401 | 갱신 1회, 모두 성공, 로그아웃 없음 | 수정 전 FAIL(DEF-06) → 수정 후 기대대로 | PASS | client.test › shares one refresh… |
| AUTH-18 | 단위 | 갱신 중 네트워크 오류 | 보호 API 호출 | NETWORK_ERROR, 토큰 유지, 만료 이벤트 없음 | 기대대로 | PASS | client.test › a network error while refreshing… |
| AUTH-19 | E2E / 단위 | 로그인 상태에서 토큰 무효화 | 새로고침 → 로그인 → 복귀 | `/login?expired=1&next=…` 후 원래 화면 | 기대대로 | PASS | resilience.spec › E2E-13; LoginPage.test; returnPath.test |
| AUTH-20 | E2E / 단위 | 외부·`//`·다른 역할 복귀 경로, 로그인한 사용자 | 로그인 / 로그인 화면 접속 | 복귀 경로 무시하고 역할 홈 / 확인 후 이동 | 기대대로 | PASS | resilience.spec › E2E-14; returnPath.test (2건) |
| AUTH-21 | E2E | 로그아웃 | 뒤로 가기, 직접 주소, 새로고침 | 보호 화면 미표시, 학생 메뉴 없음 | 기대대로 | PASS | resilience.spec › E2E-15 |
| STU-12 | 기능 | 숙제 0건 / 완료만 / 혼합 / 삭제된 숙제 / 서버 오류 / 목록 실패 | 숙제하기 화면 | 각 빈 상태 문구 구분, 완료 1회만 요청, 404면 안내 후 다시 불러오기, 실패 시 항목 유지, 재시도는 실제 재요청 | 기대대로 | PASS | StudentHomeworkScreen.test (6건) |
| STU-13 | 기능 | 기록 없음 / 필터 결과 없음 | 히스토리 | 상황별 문구 | 기대대로 | PASS | StudentHistoryScreen.test |
| TCH-13 | 기능 | 숙제 0건 / 학생 필터 결과 없음 / 로딩 / 오류 | 숙제 관리 | 상황별 문구·버튼, 재시도 호출 | 기대대로 | PASS | HomeworkView.test (2건) |
| TCH-14 | 통합 / 단위 | 숙제 수정 제목·유형 공백 | PATCH | 400, 기존 제목 유지(부분 수정 계약 유지) | 수정 전 저장됨(DEF-07) → 400 | PASS | AuthorizationIntegrationTest#malformedRequests…; RequestValidationTest#homeworkUpdate… |
| UI-01 | E2E / 컴포넌트 | 320px, 80자 이름·긴 이메일·160자 제목·2000자 설명 | 학생·교사 주요 화면 | 화면 밖 요소·가로 스크롤 없음, 완료 버튼 접근 가능 | 수정 전 학생 필터 칩 1076px(DEF-09) → 수정 후 기대대로 | PASS | resilience.spec › E2E-16; components.test › Tabs chip |
| SPE-28 | 단위(대체 실행 파일) | 멈추는 VAD | 인식 | 시간 제한 안에 VAD 없이 인식 계속, 구간 지어내지 않음 | 수정 전 무한 대기(DEF-08) → 기대대로 | PASS | WhisperProcessFailureTest#hangingVadProcess… |
| SPE-29 | 통합(whisper) / 단위 | PROCESSING 10분(서버 종료 흉내) + 같은 키 | 재요청, 동시 재요청 3건, 1분 된 진행 중 분석, 다른 녹음, 주기 정리, 늦은 완료·실패 | 고착 행 FAILED(STALE_PROCESSING)·키 해제 후 새 분석 1건 / 진행 중이면 재사용 / 409 유지 / 기준 미달 행·녹음 보존 / 늦은 갱신 0건 | 기대대로 | PASS | SpeechIdempotencyIntegrationTest (5건); SpeechAnalysisServiceImplTest (5건) |
| AUTH-22 | 통합(모의 메일) | 재설정 요청 3회: 정상 / 발송 실패 / 발송 후 커밋 실패 | 요청·코드 검증 | 실패 시 저장 없음, 첫 코드 유효, 커밋 안 된 코드 거부, 바로 재요청 가능 | 기대대로 | PASS | MailCommitConsistencyIntegrationTest |

## 미해결 항목 처리 주기 추가 케이스 (2026-10-03)

설계: `docs/design/concurrency-and-sessions.md`.

| ID | 계층 | 사전 조건·데이터 | 단계 | 기대 결과 | 실제 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|---|
| TCH-15 | 통합 / 기능 / E2E | 담당 학생, Idempotency-Key | 같은 키 재전송, 같은 키 동시 6건, 같은 키·다른 내용, 다른 키·키 없음, 다른 선생님 같은 키, 실패(담당 아님) 후 재시도, 키 형식 오류, 응답 유실 후 재클릭 | 숙제 1건·같은 ID(reused) / 동시에도 1건 / 409 IDEMPOTENCY_KEY_REUSED·변경 없음 / 각각 생성(기존 동작) / 선생님별 분리 / 실패는 키 미저장 / 400 / 화면 재클릭도 같은 키로 1건 | 기대대로 | PASS | HomeworkConcurrencyIntegrationTest (5건); useTeacherData.test (2건); idempotencyKey.test (2건); concurrency.spec › E2E-17 |
| TCH-16 | 통합 / 기능 / E2E | 숙제 version 0 | 두 탭 수정(같은 버전), 재조회 후 재저장, 버전 없이 저장, 없는 숙제 | 늦은 저장 409 VERSION_CONFLICT·덮어쓰기 없음 / 목록 version 1 → 저장 성공 / 기존처럼 저장 / 404. 화면은 최신 내용으로 모달을 다시 채우고 안내 | 기대대로 | PASS | HomeworkConcurrencyIntegrationTest#anUpdateWithAStaleVersion…; useTeacherData.test (4건); concurrency.spec › E2E-18 |
| TCH-17 | 통합 | 숙제 1건 | 같은 버전으로 동시 수정 5건 / 학생 완료와 선생님 수정 동시(3회) / 학생 완료 뒤 오래된 화면의 '미완료' | 정확히 1건 성공·4건 409 / 200 또는 409만(500 없음)·버전 = 성공 수 / 409·완료 유지 | 수정 전 동시 수정이 500(DEF-10, MariaDB 오류 1020) → 수정 후 기대대로 | PASS | HomeworkConcurrencyIntegrationTest (3건) |
| AUTH-23 | 통합 / E2E | 두 기기 로그인(한 기기는 갱신까지), 버전 클레임 없는 이전 토큰 | 비밀번호 재설정 | 재설정 전 모두 200 → 후 access 3종 모두 401(만료 전이어도), refresh 2종 401, token_version 1, 무효 토큰이 붙은 로그인 요청은 정상, 새 로그인·갱신 토큰 200. 화면은 만료 안내 후 새 비밀번호로 로그인해 원래 화면 복귀 | 기대대로 | PASS | AccountRecoveryIntegrationTest#passwordResetImmediatelyInvalidates…; concurrency.spec › E2E-19 |
| AUTH-24 | 단위 | 토큰 버전 일치/불일치/비활성 계정/DB 조회 실패/위조·없음 | 보호 API 요청 | 인증 / 미인증 / 미인증 / 503 AUTH_CHECK_UNAVAILABLE(로그아웃 유발 안 함, 내부 메시지 미노출) / DB 조회 없음. 버전 클레임 위조는 서명 오류 | 기대대로 | PASS | JwtAuthenticationFilterTest (4건); JwtUtilTest#carriesTheTokenVersion… |
| SPE-30 | 단위(대체 실행 파일) / 단위(mock) | 멈춘 VAD, Whisper 시간 초과, 동시 2건(슬롯 1개), 성공·실패 분석 | 처리 시간 로그 | 단계별 시간(슬롯 대기·VAD·Whisper)·결과 코드·시간 제한 기록, 인식 내용·목표 문장·파일 경로 미기록 | 기대대로 | PASS | WhisperProcessFailureTest (2건); SpeechAnalysisServiceImplTest#timingLog… |
| SPE-31 | 단위(mock) | 완료 저장 0건 + 오류 코드 STALE_PROCESSING | 늦게 끝난 분석 | 덮어쓰지 않음(500), WARN `outcome=LATE_AFTER_STALE` | 기대대로 | PASS | SpeechAnalysisServiceImplTest#aJobFinishingAfter… |
| SPE-32 | 운영 | 운영 처리 시간 로그 | p50·p95·p99·시간 초과 비율 | 기준 시간 적절성 판단 | — | BLOCKED | 운영 로그·메트릭 없음. 측정 로그와 `scripts/speech-timing-report.py` 추가(합성 측정은 test-results 10절) |
| SPE-33 | 단위(mock) / 통합(whisper) | 고착 분석 + 같은 키 동시 재시도 | INSERT·정리 UPDATE가 교착 상태로 되돌려짐 | 이긴 요청의 분석 재사용 또는 재시도, 계속 실패하면 503·녹음 삭제(500 없음) | 수정 전 전체 실행에서 500 1회(DEF-11) → 수정 후 단위 2건 기대대로, 통합 4회 반복 통과(교착 재발 없음) | PASS | SpeechAnalysisServiceImplTest#aDeadlock… (2건); SpeechIdempotencyIntegrationTest#concurrentRetriesOnAStuckAnalysis… |

## 2차 정책 구현 추가 케이스 (2026-10-03)

설계: `docs/design/concurrency-and-sessions.md` 7절. 상태는 test-results 11절 실행 기준.

| ID | 계층 | 사전 조건·데이터 | 단계 | 기대 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|---|
| POL-01 | 통합 | 요청 키로 만든 숙제 | 수정 후 같은 키 재전송 / 삭제 후 같은 키 재전송 | 키 유지·같은 숙제 200 / 키가 함께 삭제되어 새 숙제 201(정책 한계 고정) | PASS | HomeworkConcurrencyIntegrationTest#aKeyIsKept… |
| POL-02 | 통합 / 기능 / E2E | 같은 키·같은 내용 | 재전송, 동시 6건 | 처음만 201, 재전송은 200 + reused, 숙제 1건 / 화면은 200도 성공 처리 | PASS | HomeworkConcurrencyIntegrationTest (2건); useTeacherData.test; concurrency.spec › E2E-17 |
| POL-03 | 통합 / 단위 / 기능 | 숙제 | version 없는 수정·음수 version, 오래된 version | 400 / 409 VERSION_CONFLICT, 변경 없음. 화면은 항상 목록의 version 전송 | PASS | RequestValidationTest; AuthorizationIntegrationTest#malformedRequests…; HomeworkConcurrencyIntegrationTest; useTeacherData.test |
| POL-04 | 통합 / 기능 / E2E | 숙제 | version 없는·형식 오류 삭제, 오래된 version 삭제, 수정·삭제 동시(3회), 다른 선생님 삭제 | 400 / 409·숙제 유지 / 하나만 반영(204+404 또는 200+409) / 404. 화면은 충돌 시 최신 목록과 안내 | PASS | HomeworkConcurrencyIntegrationTest (2건); AuthorizationIntegrationTest; useTeacherData.test (3건); concurrency.spec › E2E-22 |
| POL-05 | 통합 / 단위 / E2E | 두 기기 로그인, 한 기기는 갱신 | 로그아웃(refresh+access / access만 / refresh만 / 재로그아웃) | 이 기기의 갱신 전·후 access 401·refresh 401, 다른 기기 유지 / 각각 세션 종료 / 204. 세션 없는 이전 토큰은 만료까지(한계) | PASS | AuthSessionIntegrationTest (3건); JwtAuthenticationFilterTest; concurrency.spec › E2E-20 |
| POL-06 | 통합 / 단위 | 설정 수명 1.5초 / 기본 설정 | 만료 후 요청 → refresh → 재요청 / 발급 토큰 | 401 → 새 토큰 200 / exp−iat = 설정값, 코드 기본 30분 | PASS | AccessTokenLifetimeIntegrationTest; AuthSessionIntegrationTest (2건); client.test(자동 재발급) |
| POL-07 | 통합 / 기능 / E2E | 로그인 상태 | 미인증, 현재 비밀번호 틀림, 같은 비밀번호, 확인 불일치, 짧음·73자·빈 값, 정상 변경 | 401 / 400(로그아웃 안 됨) ×5 / 200: 이 기기 새 토큰 유지, 이전 토큰·다른 기기 access·refresh 401, 이전 비밀번호 로그인 401, BCrypt 저장, 로그에 비밀번호·토큰 없음 | PASS | AuthSessionIntegrationTest (2건); ChangePasswordForm.test (3건); concurrency.spec › E2E-21 |
| POL-08 | 통합 / 기능 / E2E | 로그인 상태 | 현재 비밀번호 5회 틀림, 막힌 동안 맞는 비밀번호, 15분 경과, 성공 후, 다른 사용자, 동시 8건 | 남은 시도 4→1 안내(400) → 429·로그인 유지·비밀번호 불변 / 429 / 다시 가능·기록 삭제 / 횟수 초기화 / 영향 없음 / 400 4건·429 4건·기록 5건 | PASS | AuthSessionIntegrationTest (2건); AuthMaintenanceIntegrationTest#concurrentWrongAttempts…; ChangePasswordForm.test (429); concurrency.spec › E2E-21 |
| POL-09 | 통합 / 단위 | 현재 세션·최근 폐기(1일)·오래된 폐기(8일)·만료 행, 만료 1,205건, 실패 기록(20분·방금) | 정리 작업 | 만료·8일 폐기·1,205건·20분 기록만 삭제, 현재 세션·최근 폐기·최근 실패는 유지 / 1,000건씩 끝까지 반복·설정 기간 사용 | PASS | AuthMaintenanceIntegrationTest#cleanupRemovesOnly…; AuthServiceCleanupTest |

## AI 연동·학습 피드백 케이스 (2026-10-03)

| ID | 계층 | 단계 | 기대 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|
| AI-01 | 단위(mock) | Gemini 네이티브 주소로 호출 | 설정 URL 그대로·키는 x-goog-api-key 헤더·systemInstruction/contents 형식, 모델은 URL 이름, thought 제외 | PASS | AiTextClientTest |
| AI-02 | 단위(mock) | 401·403·400(API_KEY_INVALID)·429·400·404·500·시간 초과·차단·빈 응답·설정 없음 | 코드별 구분(AUTH_FAILED/RATE_LIMITED/REQUEST_REJECTED/PROVIDER_ERROR/TIMEOUT/BLOCKED/BAD_RESPONSE/NOT_CONFIGURED), 키·응답 본문이 메시지·로그에 없음 | PASS | AiTextClientTest; ExternalProviderServiceTest |
| AI-03 | 단위(mock) / 통합 | 근거 구성·평가 불가·동의·저장·재사용·규칙 위반 응답 | 확인된 근거만 전송(이름·ID·교사 메모·일치율 숫자 제외, 발음 미평가 명시), HOLD·미완료·검토 전 언어재활은 AI 호출 없이 NOT_EVALUABLE, 동의 없으면 403, 점수·진단 표현 응답은 저장 안 함, 같은 근거는 재호출 안 함, 분석 결과·점수 불변 | PASS | SpeechFeedbackServiceImplTest (6건); SpeechFeedbackIntegrationTest (3건) |
| AI-04 | 기능 / E2E | 결과 화면 AI 설명 | 자동 호출 없음, 버튼으로 생성, 만드는 중·설명·평가 불가·동의 필요·미설정·오류 재시도 구분, '점수 아님'·근거 출처 표시, 측정값 미평가 유지 | PASS | AiFeedbackPanel.test (5건); student-word.spec › E2E-23 |
| AI-05 | 실제 호출 | 설정된 Gemini 키로 대화·피드백 생성 | 실제 응답 | PASS(2026-10-06, 표본 9건) | 2026-10-03에는 403으로 BLOCKED였다. 2026-10-06에 해소되어 RAG-06에서 실측했다 |

## AI 2차: 동의 분리·선생님 공유·RAG (2026-10-06)

| ID | 계층 | 단계 | 기대 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|
| RAG-01 | 통합 / 기능 | AI_FEEDBACK 동의 없음·철회 | 생성 요청은 외부 호출 없이 403 CONSENT_REQUIRED, 저장된 설명은 계속 조회, 가입 시 미동의 기록, 동의 항목 5개, AI_CHAT과 별개 | PASS | SpeechFeedbackIntegrationTest; SpeechFeedbackServiceImplTest#consentIsChecked…; ConsentAndCenterIntegrationTest; AiFeedbackPanel.test |
| RAG-02 | 통합 / 기능 / E2E | 담당 선생님 조회, 담당 아닌 선생님·다른 학생(IDOR)·학생 토큰 | 학생과 같은 본문·출처, 재조회 시 AI 재호출 없음, 404/404/403, 재판정 후 outdated 표시, 교사 판정 불변 | PASS | SpeechFeedbackIntegrationTest#theAssignedTeacher…; SpeechFeedbackServiceImplTest#teacherView…; TeacherAiFeedback.test (3건); therapy-review.spec › E2E-06 |
| RAG-03 | 단위 | 목표 문장 조항 감지·검색 | 신라→20항, 국물→18항, 국밥→23항, 같이→17항, 강릉→19항, 옷을→13항, 부엌→9항, 라디오→없음, 어절 경계 넘지 않음 / 조항이 맞는 승인 청크만(자모만 같은 청크 제외), 학생 결과와 겹치면 우선 / DB 오류 RAG_UNAVAILABLE | PASS | KnowledgeRetrieverTest (3건) |
| RAG-04 | 단위 / 통합 / E2E | 관련 자료 없음, 검수 전(DRAFT) 자료만 있음 | AI·동의 확인 없이 INSUFFICIENT_SOURCES, 화면 '근거 자료 부족'·생성 버튼 없음 | PASS | SpeechFeedbackServiceImplTest; SpeechFeedbackIntegrationTest#unapprovedDraftSources…; student-word.spec › E2E-23 |
| RAG-05 | 단위(mock) | 출처 없음·없는 번호·근거 밖 자모·자동 후보 단정·'근거 부족' 응답·자료 속 지시문·점수/진단 | AI_UNGROUNDED / INSUFFICIENT_SOURCES / AI_BAD_RESPONSE, 저장 안 함, 구분자 탈출 불가 | PASS | SpeechFeedbackServiceImplTest (10건) |
| RAG-06 | 실제 호출 | Gemini RAG 피드백(합성 10건 × 4회 실행)·대화·잘못된 키·시간 초과·일시 오류·하루 한도 | READY 9건(근거 검증 9/9 통과) · 대화 3회 성공 · 401→AI_AUTH_FAILED · AI_TIMEOUT · 503 재시도 · 429 하루 한도 안내 | PASS(표본 부족) | LiveAiIntegrationTest; AiTextClientTest(재시도·하루 한도). 무료 등급 하루 20회 한도로 표본 9건 |
| RAG-07 | 사람 검수 | 실제 답변 품질(교사 검수 예시 기준) | 근거 정확성·일치성·유용성·쉬운 표현·근거 없는 주장 평가 | NOT RUN | 교사 검수 예시 없음. 개발자 예비 관찰만(ai-feedback.md 9.7) |

## 학습 서비스 확장 (2026-10-06)

설계: `docs/design/learning-service.md`.

| ID | 계층 | 단계 | 기대 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|
| LRN-01 | 통합 / 기능 | 연습 콘텐츠 목록·검색어·카테고리·난이도·발음 유형·콘텐츠 유형·페이지·잘못된 필터·미인증·없는 ID | 학생·교사 조회, 필터 결과 일치, 페이지 겹침 없음, 400/400/401/404 | PASS | LearningServiceIntegrationTest; PracticeBrowseScreen.test (3건) |
| LRN-02 | 통합 / E2E | 숙제 없이 콘텐츠 선택 → 녹음 → Whisper → 분석 → 저장 → 조회 | PRACTICE로 저장, 히스토리 '자율 연습' 표시 | PASS | LearningServiceIntegrationTest; learning.spec › E2E-25 |
| LRN-03 | 통합 / 기능 / E2E | 교사가 콘텐츠를 골라 담당 학생에게 숙제 → 학생이 숙제에서 바로 연습 | 숙제에 exerciseId·exerciseTitle, 학생 숙제에 연습 이름·연습 횟수, HOMEWORK로 저장 / 다른 세트 409 / 다른 학생 숙제 ID 404 / 담당 아닌 학생 403 / 없는 콘텐츠 400 / 자유 숙제 그대로 | PASS | LearningServiceIntegrationTest (2건); HomeworkModal.test (2건); StudentHomeworkScreen.test; E2E-25 |
| LRN-04 | 통합 / 기능 / E2E | 교사 학습 현황: 자율·숙제 연습 기록과 요약 | 종류 구분·필터, 횟수 요약, 분석 상태, 텍스트 일치율 '발음 점수 아님'·발음 '미평가', 기존 AI 설명 조회 / 다른 선생님·다른 학생 ID 403/404·학생 토큰 403·잘못된 종류 400 | PASS | LearningServiceIntegrationTest; StudentPracticePanel.test (2건); E2E-25 |
| LRN-05 | 통합 | 대량 콘텐츠(1,115개) | 1,000개 이상, 모든 발음·난이도·유형 존재, 빈 문항·세트 내 중복 없음, 음운 규칙 콘텐츠 전부 백엔드 감지기로 재확인, 전체 목록 페이지 응답 | PASS | PracticeContentSeedIntegrationTest (3건) |
| LRN-06 | 단계 확장 | 22 → 102 → 500 → 1,115개 | 각 단계 학습 통합 테스트 통과, 테스트 DB 개수 일치 | PASS | build_seed.py --limit + LearningServiceIntegrationTest |
| LRN-07 | 단위(mock) / 실제 호출 | AI 품질 보강(v3): 듣지 않은 발음 칭찬, 자모 이름, 자료 기반 연습 | 칭찬·근거 밖 자모 이름은 AI_UNGROUNDED, 근거 있는 자모 이름은 통과 / 실제 Gemini 10건 근거 검증 10/10 | PASS(교사 품질 검수 NOT RUN) | SpeechFeedbackServiceImplTest (11건); LiveAiIntegrationTest |
| LRN-08 | — | RAG 지식자료 확장 | — | NOT RUN | 이번 범위에서 제외(지시 14절) |


## RAG 자료 확장: 출처·사용 권한 (2026-10-06)

| ID | 계층 | 단계 | 기대 결과 | 상태 | 자동화 |
|---|---|---|---|---|---|
| RAG-08 | 단위 / 통합 | 출처·권한 미확인(PENDING·REJECTED), 검수 전(DRAFT), 사용 중지(RETIRED) 자료 | 검색에서 제외. 승인 상태여도 PENDING이면 쓰지 않음(개발자 요약 자료 포함), AI 호출 없이 INSUFFICIENT_SOURCES | PASS | KnowledgeRetrieverTest#onlyVerifiedAndApproved…; SpeechFeedbackIntegrationTest#approvedButUnverifiedOrPending…, #unapprovedDraftSources… |
| RAG-09 | 단위 / 통합 | source_id·제목·원문 위치·분류·판본·권한 보존 | 검색 결과와 저장된 sources_json, 응답 sources[]에 sourceId·title·location·category·sourceVersion·license·url·발췌 유지 | PASS | KnowledgeRetrieverTest; SpeechFeedbackServiceImplTest#officialSourcesAreLinked…; SpeechFeedbackIntegrationTest#theStoredExplanationLinks… |
| RAG-10 | 통합 | 원문 seed 메타데이터 | nikl-pron-* 7개 문서 모두 VERIFIED·URL·판본·권한·확인 시각·분류·발행 기관 있음, DRAFT·승인자 없음, 교사 예시 0건 | PASS | SpeechFeedbackIntegrationTest#seededOfficialSources… |
| RAG-11 | 단위 / 통합 | 안내 자료(조음 위치·방법 등) 검색 | 적용 조항 없는 낱말(라디오 ㄹ→ㄴ)에 승인된 조음 위치 자료 사용, 자모가 겹치지 않는 자료·분류 없는 무태그 자료 제외, 점수는 조항 자료보다 낮음, 구체적인 자료 우선 | PASS | KnowledgeRetrieverTest#guideSources…; SpeechFeedbackIntegrationTest#approvedGuideSources… |
| RAG-12 | 단위 / 통합 | 교사 설명 예시(TEACHER_EXAMPLE) | 승인자(reviewed_by) 없거나 PENDING이면 제외, 승인자가 있으면 별도 분류로 사용·화면에 '선생님 승인 설명 예시' 표시 | PASS | KnowledgeRetrieverTest#teacherExamples…; SpeechFeedbackIntegrationTest#teacherExamples…, #approvedTeacherExamples…; AiFeedbackPanel.test |
| RAG-13 | 단위 | 안내 자료가 있을 때의 근거 검증 | 인용 없음·없는 번호·인용 자료/분석 근거에 없는 자모·자동 후보 단정 → AI_UNGROUNDED, 점수·진단·등급 → AI_BAD_RESPONSE, 인용 자료에 있는 내용은 통과 | PASS | SpeechFeedbackServiceImplTest#groundingRulesStillApply… |
| RAG-14 | 단위 / 통합 | 기존 조항 검색 회귀 | 조항 감지·조항 태그 일치·학생 결과 우선·DB 오류 구분 그대로, 신라 피드백 READY(제5장 제20항) | PASS | KnowledgeRetrieverTest (기존 3건); SpeechFeedbackIntegrationTest (기존 6건) |
