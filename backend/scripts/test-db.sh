#!/usr/bin/env bash
# 테스트 전용 MySQL 8.4 인스턴스(포트 3310)를 backend/build/test-db에 만들고 관리한다.
# 개발용 DB(.env의 DB_URL)와 완전히 분리되며, 비밀번호는 무작위로 만들어 build/test-db/env에만 저장한다.
# 사용: scripts/test-db.sh start | stop | reset | env
set -euo pipefail
cd "$(dirname "$0")/.."
DIR="$PWD/build/test-db"; PORT="${TEST_DB_PORT:-3310}"; SOCK="$DIR/mysql.sock"
BIN="$(brew --prefix mysql@8.4 2>/dev/null)/bin"
[ -x "$BIN/mysqld" ] || { echo "MySQL 8.4 서버가 필요합니다: brew install mysql@8.4" >&2; exit 1; }
CLIENT=("$BIN/mysql" --no-defaults -uroot --socket="$SOCK")

init() {
  [ -f "$DIR/data/mysql.ibd" ] && return   # MySQL 데이터 디렉터리에만 있는 파일
  if [ -d "$DIR/data" ]; then
    # 이전 MariaDB 형식 테스트 데이터는 MySQL로 열 수 없다(테스트 데이터만 들어 있으므로 옆으로 옮긴다).
    mv "$DIR/data" "$DIR/data-old-$(date +%Y%m%d%H%M%S)"; rm -f "$DIR/env"
  fi
  mkdir -p "$DIR"
  "$BIN/mysqld" --no-defaults --initialize-insecure --datadir="$DIR/data" > "$DIR/install.log" 2>&1
}

start() {
  init
  if [ -S "$SOCK" ] && "${CLIENT[@]}" -e "SELECT 1" >/dev/null 2>&1; then echo "테스트 DB 실행 중 (127.0.0.1:$PORT)"; else
    nohup "$BIN/mysqld" --no-defaults --datadir="$DIR/data" --port="$PORT" --bind-address=127.0.0.1 --socket="$SOCK" --mysqlx=OFF \
      --pid-file="$DIR/mysql.pid" --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci \
      --default-time-zone=+09:00 --max-connections=300 > "$DIR/server.log" 2>&1 &
    for _ in $(seq 1 30); do "${CLIENT[@]}" -e "SELECT 1" >/dev/null 2>&1 && break; sleep 1; done
    "${CLIENT[@]}" -e "SELECT 1" >/dev/null 2>&1 || { echo "테스트 DB를 시작하지 못했습니다: $DIR/server.log 확인" >&2; exit 1; }
  fi
  if [ ! -f "$DIR/env" ]; then
    PASS="$(openssl rand -hex 16)"
    "${CLIENT[@]}" <<SQL
CREATE DATABASE IF NOT EXISTS chatterland_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'chatterland_test'@'127.0.0.1' IDENTIFIED BY '$PASS';
CREATE USER IF NOT EXISTS 'chatterland_test'@'localhost' IDENTIFIED BY '$PASS';
GRANT ALL PRIVILEGES ON chatterland_test.* TO 'chatterland_test'@'127.0.0.1';
GRANT ALL PRIVILEGES ON chatterland_test.* TO 'chatterland_test'@'localhost';
SQL
    umask 077
    printf 'TEST_DB_URL=jdbc:mysql://127.0.0.1:%s/chatterland_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useSSL=false\nTEST_DB_USERNAME=chatterland_test\nTEST_DB_PASSWORD=%s\n' "$PORT" "$PASS" > "$DIR/env"
  fi
  echo "테스트 DB 준비 완료 (127.0.0.1:$PORT/chatterland_test)"
}

stop() { [ -f "$DIR/mysql.pid" ] && kill "$(cat "$DIR/mysql.pid")" 2>/dev/null || true; echo "테스트 DB 중지"; }

reset() {
  start >/dev/null
  "${CLIENT[@]}" -e "DROP DATABASE IF EXISTS chatterland_test; CREATE DATABASE chatterland_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON chatterland_test.* TO 'chatterland_test'@'127.0.0.1'; GRANT ALL PRIVILEGES ON chatterland_test.* TO 'chatterland_test'@'localhost';"
  echo "테스트 DB 초기화 완료(모든 테스트 데이터 삭제)"
}

case "${1:-}" in
  start) start ;; stop) stop ;; reset) reset ;;
  env) start >/dev/null; cat "$DIR/env" ;;
  *) echo "사용: $0 start|stop|reset|env" >&2; exit 2 ;;
esac
