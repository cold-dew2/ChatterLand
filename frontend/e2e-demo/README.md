# 발표용 시연 영상

```bash
cd backend && bash scripts/test-db.sh start   # 테스트 DB(개발 DB 아님)
cd frontend && npx playwright test -c playwright.demo.config.ts
```

- 결과 영상: `frontend/test-results/demo/<테스트 이름>/video.webm` (1280×720, 한 페이지에서 선생님 → 학생 → 선생님 순서)
- 일반 E2E(`npx playwright test`)에는 포함되지 않는다(`testDir: ./e2e`).
- 서버 구성은 E2E와 같다: 테스트 DB로 띄운 백엔드(8080, 사용 중이면 실패), mailpit, 프런트(3000).
- `mic-weather-demo.wav`: `e2e/fixtures/mic-7weather.wav`(macOS `say -v Yuna` 합성음 "오늘은 날씨가 좋아요." + Zeroth-Korean 무음 구간 배경 소음, CC BY 4.0) 뒤에 디지털 무음 4초를 붙인 파일. **실제 아동 음성이 아니다.** 음성 부분은 바꾸지 않았다.
- 외부 AI(Gemini)는 부르지 않는다(요청이 나가면 테스트 실패). 발음 정확도 평가는 하지 않는다(텍스트 일치율은 발음 점수가 아님).
- 자막은 시연 설명용으로 화면 위에 덧씌운 것이며, 결과 값은 실제 서버 응답에서 읽어 표시한다.
