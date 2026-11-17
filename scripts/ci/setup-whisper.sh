#!/usr/bin/env bash
# whisper.cpp 실행 파일과 음성 모델(기본 large-v3-turbo q5_0, CI는 small · Silero VAD)을 준비한다. 모델은 SHA-256으로 검증한다.
# macOS: Homebrew whisper-cpp / Linux(CI): whisper.cpp v1.9.4 소스 빌드(cmake). 모델 위치: ~/.cache/chatterland/whisper
set -euo pipefail
WHISPER_VERSION="${WHISPER_VERSION:-v1.9.4}"
MODEL_DIR="${WHISPER_MODEL_DIR:-$HOME/.cache/chatterland/whisper}"
mkdir -p "$MODEL_DIR"

if ! command -v whisper-cli >/dev/null 2>&1 || ! command -v whisper-vad-speech-segments >/dev/null 2>&1; then
  if [[ "$(uname)" == "Darwin" ]]; then
    brew install whisper-cpp
  else
    SRC="${WHISPER_SRC_DIR:-$HOME/.cache/chatterland/whisper.cpp}"
    [ -d "$SRC/.git" ] || git clone --depth 1 --branch "$WHISPER_VERSION" https://github.com/ggml-org/whisper.cpp.git "$SRC"
    # 정적 링크: 실행 파일만 복사해도 빌드 디렉터리의 공유 라이브러리에 의존하지 않도록 한다.
    # GGML_NATIVE=OFF: 빌드한 러너의 CPU 전용 명령어를 쓰지 않는다(캐시된 빌드를 다른 CPU의 러너에서 실행해도 동작). Dockerfile과 같다.
    BUILD="$SRC/build-portable"
    cmake -S "$SRC" -B "$BUILD" -DCMAKE_BUILD_TYPE=Release -DWHISPER_BUILD_EXAMPLES=ON -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF >/dev/null
    cmake --build "$BUILD" -j"$(nproc)" --target whisper-cli whisper-vad-speech-segments >/dev/null
    mkdir -p "$HOME/.local/bin"
    cp "$BUILD/bin/whisper-cli" "$BUILD/bin/whisper-vad-speech-segments" "$HOME/.local/bin/"
    echo "$HOME/.local/bin" >> "${GITHUB_PATH:-/dev/null}"
    export PATH="$HOME/.local/bin:$PATH"
  fi
fi

sha256_check() { # "해시  파일" 을 표준입력으로 받는다(Linux sha256sum, macOS shasum)
  if command -v sha256sum >/dev/null 2>&1; then sha256sum -c - >/dev/null 2>&1; else shasum -a 256 -c - >/dev/null 2>&1; fi
}
download() { # 파일명 URL sha256
  local file="$MODEL_DIR/$1"
  if [ -f "$file" ] && echo "$3  $file" | sha256_check; then echo "모델 확인: $1"; return; fi
  echo "모델 내려받는 중: $1"
  curl -fsSL --retry 3 -o "$file.part" "$2"
  echo "$3  $file.part" | sha256_check || { echo "체크섬 불일치: $1" >&2; rm -f "$file.part"; exit 1; }
  mv "$file.part" "$file"
}
# 음성 모델: 기본은 large-v3-turbo q5_0(개발 Mac, GPU). CI는 WHISPER_MODEL_FILE=ggml-small.bin(운영 Cloud Run과 같은 모델, CPU에서 4~5배 빠름).
case "${WHISPER_MODEL_FILE:-ggml-large-v3-turbo-q5_0.bin}" in
  ggml-large-v3-turbo-q5_0.bin) download ggml-large-v3-turbo-q5_0.bin "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin" \
    394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2 ;;
  ggml-small.bin) download ggml-small.bin "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin" \
    1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b ;;
  *) echo "알 수 없는 모델: $WHISPER_MODEL_FILE (ggml-large-v3-turbo-q5_0.bin | ggml-small.bin)" >&2; exit 2 ;;
esac
download ggml-silero-v5.1.2.bin "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin" \
  29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf
# 실행 파일이 이 러너에서 실제로 도는지 확인한다(깨진 빌드면 테스트가 아니라 여기서 실패).
whisper-cli --help >/dev/null 2>&1 || { echo "whisper-cli를 실행할 수 없습니다(빌드 확인 필요)" >&2; exit 1; }
echo "whisper 준비 완료: $(command -v whisper-cli), 모델 $MODEL_DIR"
