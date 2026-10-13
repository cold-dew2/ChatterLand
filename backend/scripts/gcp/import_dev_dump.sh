#!/usr/bin/env bash
# 개발 DB 전체 백업(mysqldump)을 비어 있는 대상 DB(Cloud SQL 등)에 복구하고 검증한다. 운영 반영은 승인 후에만 실행한다.
#
# 안전장치
#  - --confirm 없이는 아무것도 쓰지 않는다(점검만).
#  - 대상 DB의 어떤 테이블에라도 행이 있으면 중단한다(기존 데이터를 덮어쓰지 않음. 백업 덤프에는 DROP TABLE이 들어 있다).
#  - 복구 후 테이블별 행 수와 핵심 테이블 지문(fingerprint.sql)을 백업 시점 값과 비교한다.
#  - 비밀번호는 MYSQL_PWD 환경변수로만 받고 출력하지 않는다.
#
# 사용(예: Cloud SQL Auth Proxy를 127.0.0.1:3307로 띄운 뒤)
#   MYSQL_PWD=... bash import_dev_dump.sh --backup-dir ~/chatterland-backups/<시각> --host 127.0.0.1 --port 3307 \
#       --user <관리 계정> --database chatterland [--audio-from /Users/.../storage/audio --audio-to /mnt/audio] [--confirm]
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
BACKUP="" HOST=127.0.0.1 PORT=3306 DBUSER="" DB=chatterland AUDIO_FROM="" AUDIO_TO="" CONFIRM=0
while [ $# -gt 0 ]; do case "$1" in
  --backup-dir) BACKUP="$2"; shift 2;; --host) HOST="$2"; shift 2;; --port) PORT="$2"; shift 2;;
  --user) DBUSER="$2"; shift 2;; --database) DB="$2"; shift 2;;
  --audio-from) AUDIO_FROM="$2"; shift 2;; --audio-to) AUDIO_TO="$2"; shift 2;;
  --confirm) CONFIRM=1; shift;; *) echo "알 수 없는 옵션: $1" >&2; exit 2;; esac; done
: "${MYSQL_PWD?MYSQL_PWD 환경변수가 필요합니다}"; export MYSQL_PWD
[ -n "$DBUSER" ] || { echo "--user 가 필요합니다" >&2; exit 2; }
DUMP="$BACKUP/chatterland-dev-full.sql"; COUNTS="$BACKUP/row-counts-source.tsv"
[ -f "$DUMP" ] && [ -f "$COUNTS" ] || { echo "백업 파일이 없습니다: $BACKUP" >&2; exit 2; }
BIN=${MYSQL_BIN:+$MYSQL_BIN/}
q() { "${BIN}mysql" -h "$HOST" -P "$PORT" -u "$DBUSER" --default-character-set=utf8mb4 "$@"; }

echo "== 백업 무결성"
(cd "$BACKUP" && shasum -a 256 -c sha256.txt)

echo "== 대상 점검 ($HOST:$PORT/$DB)"
q -N -e "SELECT VERSION(), @@global.time_zone, @@character_set_server" | awk '{print "버전 "$1", 시간대 "$2", 문자셋 "$3}'
tz=$(q -N -e "SELECT @@global.time_zone")
[ "$tz" = "+09:00" ] || echo "경고: 대상 시간대가 $tz 입니다. Cloud SQL 플래그 default_time_zone=+09:00 이 필요합니다(오늘·기간 집계가 KST 기준)."
exists=$(q -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DB'")
[ "$exists" = "1" ] || { echo "대상 데이터베이스 $DB 가 없습니다. 먼저 만들어 주세요(utf8mb4)." >&2; exit 1; }
nonempty=$(q -N "$DB" -e "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB' AND TABLE_TYPE='BASE TABLE'" | while read -r t _; do
  n=$(q -N "$DB" -e "SELECT COUNT(*) FROM \`$t\`"); [ "$n" != "0" ] && echo "$t=$n"; done || true)
if [ -n "$nonempty" ]; then
  echo "중단: 대상 DB에 이미 데이터가 있습니다(덮어쓰지 않음):"; printf '  %s\n' "$nonempty"
  echo "기존 데이터 처리 방법을 정한 뒤 진행하세요(docs/deploy/gcp-cloud-run.md '기존 GCP 데이터')."; exit 1
fi
echo "대상 DB 비어 있음(테이블 $(q -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB'")개, 행 0)"
if [ "$CONFIRM" != "1" ]; then echo "점검만 했습니다. 실제 복구는 --confirm 을 붙여 실행하세요."; exit 0; fi

echo "== 복구"
q "$DB" < "$DUMP"
if [ -n "$AUDIO_FROM" ] && [ -n "$AUDIO_TO" ]; then
  echo "== 음성 파일 경로 변경(접두어만)"
  # --audio-from 은 DB에 저장된 절대 경로의 접두어여야 한다(.env의 상대 경로가 아님).
  q "$DB" -e "UPDATE speech_analyses SET audio_path=CONCAT('$AUDIO_TO', SUBSTRING(audio_path, CHAR_LENGTH('$AUDIO_FROM')+1)) WHERE audio_path LIKE CONCAT('$AUDIO_FROM','%')"
  left=$(q -N "$DB" -e "SELECT COUNT(*) FROM speech_analyses WHERE audio_path IS NOT NULL AND audio_path NOT LIKE CONCAT('$AUDIO_TO','%')")
  moved=$(q -N "$DB" -e "SELECT COUNT(*) FROM speech_analyses WHERE audio_path LIKE CONCAT('$AUDIO_TO','%')")
  echo "  새 경로 $moved건, 바뀌지 않은 경로 $left건"
  [ "$left" = "0" ] || { echo "검증 실패: 음성 경로 접두어가 맞지 않습니다(--audio-from 확인)." >&2; exit 1; }
fi

echo "== 검증: 테이블별 행 수"
fail=0
while IFS=$'\t' read -r t n; do m=$(q -N "$DB" -e "SELECT COUNT(*) FROM \`$t\`"); [ "$m" = "$n" ] || { echo "  불일치 $t: 원본 $n, 대상 $m"; fail=1; }; done < "$COUNTS"
[ "$fail" = 0 ] && echo "  $(wc -l < "$COUNTS" | tr -d ' ')개 테이블 행 수 일치"
echo "== 검증: 핵심 테이블 지문"
q -N "$DB" < "$HERE/fingerprint.sql" > "$BACKUP/fingerprint-target.tsv"
if [ -f "$BACKUP/fingerprint-source.tsv" ]; then
  # 음성 경로를 바꿨으면 speech_analyses는 경로 검사(위)로 확인하고 지문 비교에서는 뺀다.
  skip="^$"; [ -n "$AUDIO_FROM" ] && skip="^speech_analyses"
  if diff <(grep -v "$skip" "$BACKUP/fingerprint-source.tsv") <(grep -v "$skip" "$BACKUP/fingerprint-target.tsv"); then
    echo "  지문 일치($(grep -vc "$skip" "$BACKUP/fingerprint-target.tsv")개 테이블)"
  else fail=1; fi
fi
if [ "$fail" = 0 ]; then echo "완료: 복구·검증 통과"; else echo "검증 실패 — 운영 연결 전에 원인을 확인하세요." >&2; exit 1; fi
