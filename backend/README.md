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

Uploads must be 16-bit PCM WAV (the web app converts recordings in the browser). Results store the transcript and a syllable-based sentence match rate; pronunciation/rate/fluency scores are **not evaluated**. Speech-therapy learners are saved as `PENDING` for teacher review. Recognition is not validated on children's speech.

`SPEECH_ENGINE=external` keeps the legacy provider (`SPEECH_API_URL`, `SPEECH_API_KEY`); update the consent notice if you switch, because audio then leaves the server.

## AI chat (external)

AI replies require `AI_API_URL`, `AI_API_KEY` and optionally `AI_MODEL` (OpenAI-compatible). Only text is sent (voice messages are transcribed locally and deleted). Missing settings return 503; no fake replies.

## Mail (password reset)

Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM`. Without them the reset endpoints return 503 (there is no bypass). For local development run [Mailpit](https://mailpit.axllent.org) (`brew install mailpit`, then `MAIL_HOST=127.0.0.1 MAIL_PORT=1025 MAIL_FROM=no-reply@chatterland.local`, inbox at http://127.0.0.1:8025).

## Voice retention

Recordings kept for teacher review expire after `AUDIO_RETENTION_MONTHS` (default 6). A scheduled job (`AUDIO_RETENTION_CRON`, default daily 03:30 Asia/Seoul) deletes expired files, records failures for retry, and keeps transcripts/reviews. Withdrawing voice consent deletes a student's recordings immediately. Back up `AUDIO_STORAGE_PATH` only under the same retention policy. Run one scheduler instance or rely on the idempotent DB updates when scaling out.

## PDF reports

Reports embed NanumGothic (SIL Open Font License 1.1, `src/main/resources/fonts/OFL.txt`) via Apache PDFBox, so Korean renders without system fonts.

## Tests

Run `sh gradlew test` with a reachable MySQL/MariaDB configured through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Speech tests run real whisper.cpp inference when the model exists (skipped otherwise); mail tests use an in-memory GreenMail SMTP server.
