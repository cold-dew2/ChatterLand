# 음성 테스트 픽스처

- `word-radio.wav`, `sentence-weather.wav`, `sentence-turtle.wav`: macOS `say -v Yuna`로 생성한 한국어 **합성 음성**(16kHz, mono, 16-bit PCM).
- `silence.wav`: 1초 무음.
- `room-noise.wav`: 말소리가 없는 실제 녹음 배경 소음 2초. Zeroth-Korean 말뭉치(CC BY 4.0, https://www.openslr.org/40/)의 무음 구간을 이어 붙였다.
- `*-recorded.wav`: 위 합성음 앞뒤에 `room-noise.wav` 배경 소음을 0.5초씩 붙인 것(버튼을 누르고 잠시 뒤 말하고 멈추는 실제 녹음 형태).
- `word-dadio-recorded.wav`: 합성음 "다디오". 목표 "라디오"에 대한 ㄹ→ㄷ 대치를 흉내 낸 입력(Whisper는 "타디오"로 인식한다).
- `sentence-weather-cut.wav`: 문장 앞부분 60%만 남기고 말소리가 녹음 끝까지 이어지는 잘린 녹음.
- `tone-1khz.wav`: 사람 목소리가 아닌 1kHz 신호음 1.5초.

실제 아동 음성이나 개인정보가 아니며, 아동 음성 인식 성능을 대표하지 않는다.
