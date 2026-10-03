# ChatterLand backend

Spring Boot 4 / Java 17 service for the ChatterLand language rehabilitation app. The backend follows the TripMate-style domain layers: controller, service/implementation, mapper, DTO, and MyBatis XML. MariaDB stores users, student profiles, therapy, practice, speech analysis, conversations, and homework.

## Local development

1. Configure `.env` from `.env.example` with your MariaDB connection and a private `JWT_SECRET`.
2. Run `bash gradlew bootRun` from `backend/`.
3. Run Next.js from `frontend/`; its default API origin is `http://localhost:8080`.

The project can use an existing local MariaDB or MySQL instance. `schema.sql` initializes the required tables and starter practice content. For an isolated Docker database, run `docker compose up -d db`; it uses host port `3307` by default so it does not conflict with a local database on `3306`. Set `DB_URL` to port `3307` when using that container. DB, pool, JWT, audio storage, AI, and speech settings come from environment variables. No external provider credentials are bundled.

## API domains

- `/api/v1/auth`: signup, login, refresh, logout, current user
- `/api/v1/students` and `/api/v1/sessions`: profile, assigned sessions, history
- `/api/v1/practice`: categories, exercises, persisted attempts
- `/api/v1/speech`: audio upload and provider-backed analysis
- `/api/v1/ai`: persisted conversation history, replies, and transcription
- `/api/v1/teachers`: students, sessions, homework CRUD, analytics, reports

Protected APIs enforce JWT roles and teacher/student ownership. Passwords are BCrypt-hashed. Refresh tokens are stored hashed and rotated. Audio is stored under `AUDIO_STORAGE_PATH` (default `./storage/audio`); deploy with persistent storage and access controls suitable for voice recordings.

## Speech recognition (local, default)

`SPEECH_ENGINE=local` (default) runs Korean ASR on the server with [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT) and OpenAI Whisper weights (MIT). No audio is sent to external services.

1. Install the CLI: `brew install whisper-cpp` (or build whisper.cpp and set `WHISPER_CLI_PATH`).
2. Download a model, e.g. `ggml-large-v3-turbo-q5_0.bin` (~574 MB) from `huggingface.co/ggerganov/whisper.cpp`, to `~/.cache/chatterland/whisper/` or set `WHISPER_MODEL_PATH`.
3. Measured on an Apple Silicon Mac (16 GB): ~2.6–3.3 s per request, ~880 MB RSS. `WHISPER_MAX_CONCURRENCY` (default 1) limits parallel inference.
4. Recommended: download the Silero VAD model `ggml-silero-v5.1.2.bin` (~0.9 MB, MIT) from `huggingface.co/ggml-org/whisper-vad` to the same folder (or set `WHISPER_VAD_MODEL_PATH`). The server then runs `whisper-vad-speech-segments` (installed with whisper-cpp) first and rejects recordings with no speech (422). Without it, Whisper turns background noise into text such as "감사합니다." (19 of 20 noise-only clips in our test). VAD is only used as a speech/no-speech gate; recognition still uses the full recording, because cutting audio with VAD clipped the first sound of short words (풀 → 툴).

Measured accuracy (2026-10-02, large-v3-turbo q5_0): 40 adult read sentences from Zeroth-Korean had a syllable error rate of 9.5%, and most of the large errors came from clips that were cut off or had extra speech. With Yuna TTS at four speaking rates plus real background noise, 25 of 40 single words were recognized exactly and 8 of 8 sentences. 포도, 파나마, 불 and 로봇 failed often. Recognition confidence did not separate right from wrong results. We have not tested children's speech. Whisper also "corrects" mispronunciations (숩속 → 숲속, 거부기 → 거북이), so the sentence match rate is not a pronunciation score.

Uploads must be 16-bit PCM WAV (the web app converts recordings in the browser). Results store the transcript and a syllable-based sentence match rate; pronunciation/rate/fluency scores are **not evaluated**. Speech-therapy learners are saved as `PENDING` for teacher review. Recognition is not validated on children's speech.

`SPEECH_ENGINE=external` keeps the legacy provider (`SPEECH_API_URL`, `SPEECH_API_KEY`); update the consent notice if you switch, because audio then leaves the server.

## Word / sentence assessment (automatic evidence, not a score)

Every local analysis stores an automatic assessment next to the transcript. It contains no pronunciation score.

- `analysisType`: `WORD` (one eojeol) or `SENTENCE`.
- `textMatchRate`: syllable match between the ASR text and the target. `matchRate` is kept with the same value for the existing API contract.
- `targetPhonemes` / `targetPositions`: target phonemes per exercise (`exercises.target_phonemes`, e.g. `ㄹ`, `ㅂ,ㅍ`) and where they occur. Positions are word/syllable index, onset/coda, and word-initial/medial/final, based on spelling (Hangul jamo). Pronunciation rules such as liaison and tensification are not applied.
- `phonemeCandidates`: substitution / omission / addition **candidates** from aligning the ASR text with the target by jamo. They are unconfirmed. Whisper can rewrite a mispronunciation as the correct word (숩속 → 숲속), or hear a slightly different sound (다디오 → 타디오, so the candidate is ㄹ→ㅌ, not ㄹ→ㄷ).
- `speechTiming`: speech duration, leading and trailing silence, pauses, and syllables per second, measured from the Silero VAD segments. The value is `UNAVAILABLE` without VAD.
- `repetition`: comparison with the last 5 completed analyses of the same item (same transcript count, recurring candidates).
- `assessmentStatus`: `NO_CANDIDATES`, `ERROR_CANDIDATES` or `HOLD`. `HOLD` is set, with `holdReasons`, when:
  - speech touches the end or start of the recording (`SPEECH_CUT_OFF_END` / `SPEECH_CUT_OFF_START`);
  - speech lasts less than 200 ms (`SPEECH_TOO_SHORT`);
  - more than 0.5% of samples are clipped (`CLIPPING`);
  - peak < 1500 (`LOW_VOLUME`);
  - digits or Latin letters appear in the transcript (`NON_HANGUL_TRANSCRIPT`);
  - the transcript is much longer or shorter than the target (`LENGTH_MISMATCH`).
- Teacher results are stored separately: `teacherJudgement`, `teacherNote`, and `teacherConfirmedErrors` (`PATCH .../review` with optional `confirmedErrors[{phoneme, errorType: SUBSTITUTION|OMISSION|DISTORTION|ADDITION, produced, position}]`). Automatic candidates are never overwritten.
- `analysisVersion` (`jamo-align-v1`), `engineName` and `modelName` are recorded per analysis.

A real phoneme-level pronunciation score needs a phoneme model validated on Korean children's speech. Options found (2026-10-02, not integrated):

- Azure Speech Pronunciation Assessment supports `ko-KR`. It is paid, and audio leaves the server, so the consent text would need to change.
- On Hugging Face, `slplab/wav2vec2-xls-r-300m_phoneme-mfa_korean_nia13-asia-9634_001` (Apache-2.0, ~1.3 GB, Korean phoneme CTC).
- On Hugging Face, `Miniijune/wav2vec2-xls-r-300m-Korean-children-pronunciation-jamo-based-semi-supervised_V2` (Apache-2.0, ~2.5 GB). Its model card does not state the training data.
- Validation data such as AI Hub "한국어 아동 음성" (dataSetSn=540) and "구음장애 음성인식" (dataSetSn=608) requires an application.

## AI chat (external)

AI replies require `AI_API_URL`, `AI_API_KEY` and optionally `AI_MODEL` (default `gpt-4o-mini`). The backend sends an OpenAI-compatible Chat Completions request (`POST`, `Authorization: Bearer <key>`, body `{model, messages}`), so `AI_API_URL` must be the full endpoint ending in `/chat/completions`, not a base URL. Only text is sent (voice messages are transcribed locally and deleted). Missing settings return 503 (`AI_API_URL 및 AI_API_KEY 환경변수 설정이 필요합니다.`); there are no fake replies.

- Local: put the values in `backend/.env` (loaded from the working directory by `spring.config.import`, so start the app from `backend/`, e.g. `./gradlew bootRun`), then restart the backend.
- Deployment: set them as environment variables or secrets of the backend process/container. Never put the key in the frontend (`NEXT_PUBLIC_*` values are bundled into the browser).

## Mail (password reset)

Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM`. Without them the reset endpoints return 503 (there is no bypass). For local development run [Mailpit](https://mailpit.axllent.org) (`brew install mailpit`, then `MAIL_HOST=127.0.0.1 MAIL_PORT=1025 MAIL_FROM=no-reply@chatterland.local`, inbox at http://127.0.0.1:8025).

## Voice retention

Recordings kept for teacher review expire after `AUDIO_RETENTION_MONTHS` (default 6). A scheduled job (`AUDIO_RETENTION_CRON`, default daily 03:30 Asia/Seoul) deletes expired files, records failures for retry, and keeps transcripts/reviews. Withdrawing voice consent deletes a student's recordings immediately. Back up `AUDIO_STORAGE_PATH` only under the same retention policy. Run one scheduler instance or rely on the idempotent DB updates when scaling out.

## PDF reports

Reports embed NanumGothic (SIL Open Font License 1.1, `src/main/resources/fonts/OFL.txt`) via Apache PDFBox, so Korean renders without system fonts.

## Tests

1. Start the isolated test database once: `scripts/test-db.sh start`. This runs a separate MariaDB on 127.0.0.1:3310 with DB `chatterland_test`. Data lives in `build/test-db`, and a random password is stored in `build/test-db/env`.
2. Run `sh gradlew unitTest` (no DB), `sh gradlew integrationTest` (test DB required), or `sh gradlew test` (both). Integration tests use `build/test-db/env` or `TEST_DB_URL` / `TEST_DB_USERNAME` / `TEST_DB_PASSWORD`. They never fall back to the `.env` development DB; if no test DB is configured or reachable, they fail and say why. Set `REQUIRE_SPEECH_MODEL=true` / `REQUIRE_MAILPIT=true` to turn a missing whisper model or Mailpit into a failure instead of a skip. `scripts/test-all.sh` in the repo root runs every stage and summarizes the results.
3. Speech tests run real whisper.cpp inference when the model exists, and are skipped otherwise. Mail tests use an in-memory GreenMail SMTP server.
4. Browser E2E tests are in `frontend/e2e`. Run them with `npm run test:e2e`; Playwright starts this backend against the test DB.
5. `scripts/test-db.sh reset` deletes all test data. `scripts/test-db.sh stop` stops the test DB.

The test plan, test cases and latest results are in `docs/testing/`.
