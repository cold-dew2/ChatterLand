# E2E 가짜 마이크 입력

Chrome `--use-file-for-fake-audio-capture`로 마이크 입력을 대신하는 파일이다. 실제 아동 음성이 아니다.

- `mic-radio.wav`: macOS `say -v Yuna` 합성음 "라디오" + 배경 소음
- `mic-weather.wav`: 합성음 "오늘은 날씨가 좋아요." (약 3.4초, 파일이 반복 재생된다)
- `mic-dadio.wav`: 합성음 "다디오"(목표 "라디오"에 대한 ㄹ 대치 흉내) + 배경 소음
- 배경 소음은 Zeroth-Korean 말뭉치(CC BY 4.0, https://www.openslr.org/40/)의 무음 구간이다.
