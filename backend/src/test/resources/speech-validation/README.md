# 말하기 측정값 검증 매니페스트

- `synthetic-selftest.tsv`: 검증 도구(`SpeechValidation`, `SpeechValidationRun`)가 동작하는지 확인하는 **자체 점검용** 매니페스트다. 입력은 `speech/`(검증 녹음 폴더 = `src/test/resources`)의 성인 합성음 픽스처이며 **실제 아동 음성이 아니다.** 이 매니페스트의 결과를 측정 정확도·아동 음성 검증 결과로 쓰지 않는다.
- 기준 구간은 픽스처를 만든 위치(앞 배경 소음 0.5초 등)와 배경 소음 최대 진폭(554)의 1.5배를 넘는 10ms 프레임으로 계산했다. `selftest-B`는 전사자 간 일치도 계산을 확인하기 위한 가상 표시다.
- 실제 검증용 매니페스트 형식과 작성 기준은 `docs/design/speech-metrics.md` 8장을 따른다. 실제 아동 음성 매니페스트·녹음은 이 저장소에 넣지 않는다.
- 실행 예(자체 점검): `cd backend && SPEECH_VALIDATION_MANIFEST=$PWD/src/test/resources/speech-validation/synthetic-selftest.tsv SPEECH_VALIDATION_AUDIO_ROOT=$PWD/src/test/resources sh gradlew speechValidation`
