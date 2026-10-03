#!/usr/bin/env bash
# 테스트 전용 MariaDB 인스턴스(포트 3310)를 backend/build/test-db에 만들고 관리한다.
# 개발용 DB(.env의 DB_URL)와 완전히 분리되며, 비밀번호는 무작위로 만들어 build/test-db/env에만 저장한다.
# 사용: scripts/test-db.sh start | stop | reset | env
set -euo pipefail
cd "$(dirname "$0")/.."
DIR="$PWD/build/test-db"; PORT="${TEST_DB_PORT:-3310}"; SOCK="$DIR/mariadb.sock"
BIN="$(brew --prefix mariadb 2>/dev/null)/bin"
[ -x "$BIN/mariadbd" ] || { echo "MariaDB 서버가 필요합니다: brew install mariadb" >&2; exit 1; }

init() {
  [ -d "$DIR/data/mysql" ] && return
  mkdir -p "$DIR"
  "$BIN/mariadb-install-db" --datadir="$DIR/data" --auth-root-authentication-method=socket --skip-test-db > "$DIR/install.log" 2>&1
}

start() {
  init
  if [ -S "$SOCK" ] && "$BIN/mariadb" --socket="$SOCK" -e "SELECT 1" >/dev/null 2>&1; then echo "테스트 DB 실행 중 (127.0.0.1:$PORT)"; else
    nohup "$BIN/mariadbd" --no-defaults --datadir="$DIR/data" --port="$PORT" --bind-address=127.0.0.1 --socket="$SOCK" \
      --pid-file="$DIR/mariadb.pid" --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci > "$DIR/server.log" 2>&1 &
    for _ in $(seq 1 30); do "$BIN/mariadb" --socket="$SOCK" -e "SELECT 1" >/dev/null 2>&1 && break; sleep 1; done
  fi
  if [ ! -f "$DIR/env" ]; then
    PASS="$(openssl rand -hex 16)"
    "$BIN/mariadb" --socket="$SOCK" <<SQL
CREATE DATABASE IF NOT EXISTS chatterland_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'chatterland_test'@'127.0.0.1' IDENTIFIED BY '$PASS';
CREATE USER IF NOT EXISTS 'chatterland_test'@'localhost' IDENTIFIED BY '$PASS';
GRANT ALL PRIVILEGES ON chatterland_test.* TO 'chatterland_test'@'127.0.0.1';
GRANT ALL PRIVILEGES ON chatterland_test.* TO 'chatterland_test'@'localhost';
SQL
    umask 077
    printf 'TEST_DB_URL=jdbc:mariadb://127.0.0.1:%s/chatterland_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul\nTEST_DB_USERNAME=chatterland_test\nTEST_DB_PASSWORD=%s\n' "$PORT" "$PASS" > "$DIR/env"
  fi
  echo "테스트 DB 준비 완료 (127.0.0.1:$PORT/chatterland_test)"
}

stop() { [ -f "$DIR/mariadb.pid" ] && kill "$(cat "$DIR/mariadb.pid")" 2>/dev/null || true; echo "테스트 DB 중지"; }

reset() {
  start >/dev/null
  "$BIN/mariadb" --socket="$SOCK" -e "DROP DATABASE IF EXISTS chatterland_test; CREATE DATABASE chatterland_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON chatterland_test.* TO 'chatterland_test'@'127.0.0.1'; GRANT ALL PRIVILEGES ON chatterland_test.* TO 'chatterland_test'@'localhost';"
  echo "테스트 DB 초기화 완료(모든 테스트 데이터 삭제)"
}

case "${1:-}" in
  start) start ;; stop) stop ;; reset) reset ;;
  env) start >/dev/null; cat "$DIR/env" ;;
  *) echo "사용: $0 start|stop|reset|env" >&2; exit 2 ;;
esac
