#!/bin/sh
# GCP Cloud SQL 초기화 스크립트(chatterland_cloudsql.sql)를 다시 만든다.
#
# 방법: 로컬 MySQL에 임시 DB를 만들어 backend/src/main/resources/schema.sql + data.sql 을 적용하고
#       (백엔드가 시작할 때 실행하는 것과 같은 파일), 그 최종 상태를 덤프한 뒤 임시 DB를 지운다.
#       덤프는 기존 데이터를 지우지 않도록 바꾼다: DROP TABLE 없음, CREATE TABLE IF NOT EXISTS, INSERT IGNORE.
#
# 사용: MYSQL_USER=<계정> MYSQL_PWD=<비밀번호> sh backend/scripts/gcp/build_cloudsql.sh
#       (MYSQL_HOST 기본 127.0.0.1, MYSQL_PORT 기본 3306. 비밀번호는 환경변수로만 받고 출력하지 않는다)
#       MySQL 클라이언트(mysql·mysqldump)가 필요하다. PATH의 것이 MariaDB라면 MYSQL_BIN=/opt/homebrew/opt/mysql/bin 처럼 지정한다.
#       실패하면 기존 chatterland_cloudsql.sql 은 바뀌지 않는다.
set -eu
set -o pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
RES="$HERE/../../src/main/resources"
OUT="$HERE/chatterland_cloudsql.sql"
HOST=${MYSQL_HOST:-127.0.0.1}
PORT=${MYSQL_PORT:-3306}
: "${MYSQL_USER:?MYSQL_USER 가 필요합니다}"
: "${MYSQL_PWD?MYSQL_PWD 가 필요합니다(빈 값 가능)}"
export MYSQL_PWD
BIN=${MYSQL_BIN:+$MYSQL_BIN/}
mysql() { "${BIN}mysql" "$@"; }
mysqldump() { "${BIN}mysqldump" "$@"; }
mysqldump --version | grep -qi mariadb && { echo "MariaDB mysqldump 입니다. MYSQL_BIN 으로 MySQL 클라이언트를 지정하세요." >&2; exit 1; }
SCRATCH=chatterland_gcp_build_$$
TMP="$OUT.tmp.$$"
CONN="-h $HOST -P $PORT -u $MYSQL_USER"
# 사용자 데이터가 없는 기본 데이터 테이블만 행을 덤프한다.
SEED_TABLES="centers practice_categories exercises exercise_items knowledge_documents knowledge_chunks"

cleanup() { rm -f "$TMP"; mysql $CONN -e "DROP DATABASE IF EXISTS \`$SCRATCH\`" 2>/dev/null || true; }
trap cleanup EXIT

mysql $CONN -e "CREATE DATABASE \`$SCRATCH\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mysql $CONN --default-character-set=utf8mb4 "$SCRATCH" < "$RES/schema.sql" > /dev/null
mysql $CONN --default-character-set=utf8mb4 "$SCRATCH" < "$RES/data.sql" > /dev/null

TABLES=$(mysql $CONN -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$SCRATCH' AND TABLE_TYPE='BASE TABLE'")
count() { mysql $CONN -N "$SCRATCH" -e "$1"; }
ACTIVE=$(count "SELECT COUNT(*) FROM exercises WHERE active=1")
ITEMS=$(count "SELECT COUNT(*) FROM exercise_items i JOIN exercises e USING(exercise_id) WHERE e.active=1")
DOCS=$(count "SELECT COUNT(*) FROM knowledge_documents")
CHUNKS=$(count "SELECT COUNT(*) FROM knowledge_chunks")
CENTERS=$(count "SELECT COUNT(*) FROM centers")
BY_CATEGORY=$(count "SELECT GROUP_CONCAT(CONCAT(c.category_id,' ',n) ORDER BY c.sort_order SEPARATOR ' / ') FROM practice_categories c JOIN (SELECT category_id, COUNT(*) n FROM exercises WHERE active=1 GROUP BY category_id) x USING(category_id)")

DUMP_OPTS="--single-transaction --skip-comments --skip-dump-date --no-tablespaces --set-gtid-purged=OFF --skip-add-locks --skip-disable-keys --default-character-set=utf8mb4"
{
  cat <<EOF
-- =============================================================================
-- 채터랜드 GCP Cloud SQL (MySQL 8.0 이상) 초기화 스크립트 — 자동 생성 파일, 직접 고치지 않는다.
-- 다시 만들기: MYSQL_USER=<계정> MYSQL_PWD=<비밀번호> sh backend/scripts/gcp/build_cloudsql.sh
--
-- 내용: 데이터베이스 chatterland + 테이블 $TABLES개 + 기본 데이터
--       센터 $CENTERS, 연습 영역 5, 활성 연습 콘텐츠 $ACTIVE개($BY_CATEGORY) · 문항 $ITEMS개,
--       AI 설명 근거 자료 문서 $DOCS · 조각 $CHUNKS.
--       사용자 계정·학습 기록·숙제·비밀번호·키는 들어 있지 않다.
-- 출처: backend/src/main/resources/schema.sql + data.sql 을 빈 DB에 적용한 최종 상태(백엔드와 같은 스키마).
--
-- 기존 데이터를 지우지 않는다: DROP 문이 없고 CREATE TABLE IF NOT EXISTS · INSERT IGNORE 만 쓴다.
-- 같은 ID의 기존 행은 덮어쓰지 않으며, 여러 번 실행해도 중복이 생기지 않는다.
-- 이전 버전 스키마로 이미 만든 DB에는 새 테이블(homework_exercises, notification_settings 등)만 추가되고
-- 기존 테이블의 새 컬럼은 추가되지 않는다. 그 경우 백엔드를 한 번 시작하면 schema.sql 이 컬럼을 맞춘다.
--
-- 가져오기 (예: 인스턴스 chatterland-db, 버킷 my-bucket)
--   gsutil cp chatterland_cloudsql.sql gs://my-bucket/
--   gcloud sql import sql chatterland-db gs://my-bucket/chatterland_cloudsql.sql
--   (파일 안에 CREATE DATABASE chatterland 가 있으므로 --database 는 지정하지 않아도 된다)
--
-- 애플리케이션 계정은 콘솔이나 gcloud 로 따로 만든다(비밀번호를 이 파일에 적지 않는다).
--   gcloud sql users create ctland --instance=chatterland-db --password=<비밀번호>
--   백엔드 환경변수: DB_URL=jdbc:mariadb://<호스트>:3306/chatterland?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
--                   DB_USERNAME / DB_PASSWORD
-- 맨 끝의 검증 쿼리는 결과만 보여 주며 데이터를 바꾸지 않는다.
-- =============================================================================

SET NAMES utf8mb4;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;
CREATE DATABASE IF NOT EXISTS \`chatterland\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE \`chatterland\`;

-- ── 테이블 ──────────────────────────────────────────────────────────────────
EOF
  mysqldump $CONN $DUMP_OPTS --no-data --skip-add-drop-table "$SCRATCH" \
    | sed -E 's/^CREATE TABLE `/CREATE TABLE IF NOT EXISTS `/; s/ AUTO_INCREMENT=[0-9]+//' \
    | grep -vE '^/\*!40101 SET (@saved_cs_client|character_set_client)|^/\*!(40|50)[0-9]{3} SET (NAMES|@OLD|TIME_ZONE|SQL_MODE|FOREIGN_KEY|UNIQUE|CHARACTER|COLLATION|SQL_NOTES)|^/\*!50503 SET character_set_client'
  echo
  echo "-- ── 기본 데이터(INSERT IGNORE: 이미 있는 행은 그대로 둔다) ─────────────────────────"
  mysqldump $CONN $DUMP_OPTS --no-create-info --insert-ignore --skip-extended-insert --complete-insert "$SCRATCH" $SEED_TABLES \
    | grep -E '^INSERT'
  cat <<EOF

SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET SQL_NOTES=@OLD_SQL_NOTES;

-- ── 검증(결과만 출력) ───────────────────────────────────────────────────────
-- 기대값: 활성 연습 콘텐츠 $ACTIVE, 영역별 $BY_CATEGORY, 활성 콘텐츠 문항 $ITEMS
SELECT COUNT(*) AS active_exercises, $ACTIVE AS expected FROM exercises WHERE active=1;
SELECT c.category_id, c.name, COUNT(e.exercise_id) AS active_exercises
  FROM practice_categories c LEFT JOIN exercises e ON e.category_id=c.category_id AND e.active=1
  GROUP BY c.category_id, c.name, c.sort_order ORDER BY c.sort_order;
SELECT COUNT(*) AS active_exercise_items, $ITEMS AS expected FROM exercise_items i JOIN exercises e USING(exercise_id) WHERE e.active=1;
SELECT t.table_name, IF(x.TABLE_NAME IS NULL, 'MISSING', 'OK') AS status
  FROM (SELECT 'homework_exercises' AS table_name UNION ALL SELECT 'notification_settings') t
  LEFT JOIN information_schema.TABLES x ON x.TABLE_SCHEMA=DATABASE() AND x.TABLE_NAME=t.table_name;
SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME IN ('homework_exercises','notification_settings') ORDER BY TABLE_NAME, CONSTRAINT_NAME;
-- 아래는 모두 0이어야 한다(관계가 끊긴 행, 중복 행)
SELECT
  (SELECT COUNT(*) FROM exercises e LEFT JOIN practice_categories c USING(category_id) WHERE c.category_id IS NULL) AS exercises_without_category,
  (SELECT COUNT(*) FROM exercise_items i LEFT JOIN exercises e USING(exercise_id) WHERE e.exercise_id IS NULL) AS items_without_exercise,
  (SELECT COUNT(*) FROM exercises e WHERE e.active=1 AND NOT EXISTS (SELECT 1 FROM exercise_items i WHERE i.exercise_id=e.exercise_id)) AS active_exercises_without_items,
  (SELECT COUNT(*) FROM (SELECT exercise_id, sort_order FROM exercise_items GROUP BY exercise_id, sort_order HAVING COUNT(*)>1) d) AS duplicate_items,
  (SELECT COUNT(*) FROM (SELECT category_id, title FROM exercises WHERE active=1 GROUP BY category_id, title HAVING COUNT(*)>1) d) AS duplicate_active_titles,
  (SELECT COUNT(*) FROM homework_exercises h LEFT JOIN exercises e USING(exercise_id) WHERE e.exercise_id IS NULL) AS homework_links_without_exercise,
  (SELECT COUNT(*) FROM knowledge_chunks k LEFT JOIN knowledge_documents d USING(document_id) WHERE d.document_id IS NULL) AS chunks_without_document;
EOF
} > "$TMP"
mv "$TMP" "$OUT"

echo "만듦: $OUT"
echo "테이블 $TABLES · 활성 콘텐츠 $ACTIVE ($BY_CATEGORY) · 문항 $ITEMS · 근거 문서 $DOCS/조각 $CHUNKS"
