# ChatterLand 백엔드 컨테이너(Cloud Run). 빌드: docker build -t chatterland-backend backend/
# - 앱: Gradle bootJar(Java 17). 테스트는 CI(ci.yml)에서 먼저 통과해야 배포 workflow가 실행된다.
# - 음성 인식: whisper.cpp v1.9.4(정적 빌드) + large-v3-turbo q5_0 모델·Silero VAD(CI와 같은 SHA-256 검증).
#   주의: CPU만 쓰면 2~3초 음성에 수십 초가 걸린다(개발 Mac은 GPU 사용). docs/deploy/gcp-cloud-run.md의 '음성 인식' 결정 필요.
# - 비밀값(DB·JWT·AI 키)은 이미지에 넣지 않는다. Cloud Run에서 Secret Manager로 주입한다.

# ── 1) 앱 빌드 ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS app
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" /app.jar

# ── 2) whisper.cpp 빌드 ─────────────────────────────────────
FROM debian:bookworm-slim AS whisper
ARG WHISPER_VERSION=v1.9.4
RUN apt-get update && apt-get install -y --no-install-recommends git cmake build-essential ca-certificates && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch "$WHISPER_VERSION" https://github.com/ggml-org/whisper.cpp.git /whisper \
 && cmake -S /whisper -B /whisper/build -DCMAKE_BUILD_TYPE=Release -DWHISPER_BUILD_EXAMPLES=ON -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF \
 && cmake --build /whisper/build -j"$(nproc)" --target whisper-cli whisper-vad-speech-segments

# ── 3) 모델(체크섬 검증) ────────────────────────────────────
FROM debian:bookworm-slim AS models
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /models
RUN curl -fsSL --retry 3 -o ggml-large-v3-turbo-q5_0.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin \
 && echo "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2  ggml-large-v3-turbo-q5_0.bin" | sha256sum -c - \
 && curl -fsSL --retry 3 -o ggml-silero-v5.1.2.bin https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin \
 && echo "29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf  ggml-silero-v5.1.2.bin" | sha256sum -c -

# ── 4) 실행 이미지 ──────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home /app app && mkdir -p /app /var/chatterland/audio && chown -R app /app /var/chatterland
COPY --from=whisper /whisper/build/bin/whisper-cli /whisper/build/bin/whisper-vad-speech-segments /usr/local/bin/
COPY --from=models /models /opt/whisper
COPY --from=app /app.jar /app/app.jar
ENV TZ=Asia/Seoul \
    WHISPER_CLI_PATH=/usr/local/bin/whisper-cli \
    WHISPER_VAD_CLI_PATH=/usr/local/bin/whisper-vad-speech-segments \
    WHISPER_MODEL_PATH=/opt/whisper/ggml-large-v3-turbo-q5_0.bin \
    WHISPER_VAD_MODEL_PATH=/opt/whisper/ggml-silero-v5.1.2.bin \
    AUDIO_STORAGE_PATH=/var/chatterland/audio \
    SQL_INIT_MODE=never \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
USER app
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
