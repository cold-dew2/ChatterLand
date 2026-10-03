#!/usr/bin/env bash
# ChatterLand 테스트 실행기. 단계별로 나눠 실행하고 PASS / FAIL / SKIPPED / BLOCKED / NOT RUN 으로 요약한다.
#   scripts/test-all.sh                 # 전체(단위 → 통합 → 프런트 → E2E)
#   scripts/test-all.sh unit frontend   # 고른 단계만 (unit | integration | frontend | e2e)
#   REQUIRE=1 scripts/test-all.sh       # whisper 모델·Mailpit 누락을 SKIPPED가 아니라 FAIL로 처리(CI)
# 개발 DB는 사용하지 않는다. 통합·E2E는 테스트 전용 DB(backend/scripts/test-db.sh 또는 TEST_DB_* 환경변수)가 필요하다.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT/test-reports"; mkdir -p "$REPORT_DIR"
STAGES=("$@"); [ ${#STAGES[@]} -eq 0 ] && STAGES=(unit integration frontend e2e)
if [ "${REQUIRE:-0}" = "1" ]; then export REQUIRE_SPEECH_MODEL=true REQUIRE_MAILPIT=true; fi
# bash 3.2(macOS 기본)에서도 동작하도록 연관 배열 대신 STATUS_<단계> 변수를 쓴다.
set_result() { eval "STATUS_$1=\"\$2\""; eval "DETAIL_$1=\"\$3\""; }
get_status() { eval "printf '%s' \"\${STATUS_$1:-}\""; }
get_detail() { eval "printf '%s' \"\${DETAIL_$1:-}\""; }
wants() { for s in "${STAGES[@]}"; do [ "$s" = "$1" ] && return 0; done; return 1; }
port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && exec 3>&- 3<&-; }
blocked() { set_result "$1" "BLOCKED" "$2"; echo "  ⛔ $1 차단: $2"; }

junit_summary() { # dir → "tests skipped failures errors"
  python3 - "$1" <<'PY'
import glob,re,sys
t=s=f=e=0
for p in glob.glob(sys.argv[1]+'/*.xml'):
    m=re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"',open(p,encoding='utf-8').read())
    a=list(map(int,m.groups())); t+=a[0]; s+=a[1]; f+=a[2]; e+=a[3]
print(t,s,f,e)
PY
}
record() { # stage exitcode tests skipped failed
  local stage=$1 code=$2 tests=$3 skipped=$4 failed=$5
  local result="PASS"; if [ "$failed" -gt 0 ] || [ "$code" -ne 0 ]; then result="FAIL"; fi
  set_result "$stage" "$result" "tests=$tests passed=$((tests-skipped-failed)) failed=$failed skipped=$skipped (exit $code)"
}

echo "== 사전 조건 확인"
command -v java >/dev/null || echo "  ⚠ java 없음"
MODEL="${WHISPER_MODEL_PATH:-$HOME/.cache/chatterland/whisper/ggml-large-v3-turbo-q5_0.bin}"
[ -r "$MODEL" ] && echo "  ✓ whisper 모델" || echo "  ⚠ whisper 모델 없음($MODEL) → 음성 테스트 SKIPPED (REQUIRE=1이면 FAIL). scripts/ci/setup-whisper.sh"
command -v whisper-cli >/dev/null && echo "  ✓ whisper-cli" || echo "  ⚠ whisper-cli 없음"
port_open 1025 && echo "  ✓ Mailpit(1025)" || echo "  ⚠ Mailpit 미실행 → Mailpit 테스트 SKIPPED (REQUIRE=1이면 FAIL)"
if [ -z "${TEST_DB_URL:-}" ] && { wants integration || wants e2e; }; then
  "$ROOT/backend/scripts/test-db.sh" start >/dev/null 2>&1 && echo "  ✓ 테스트 DB(backend/scripts/test-db.sh)" || echo "  ⚠ 테스트 DB를 시작하지 못함"
fi

if wants unit; then
  echo "== 백엔드 단위 테스트"
  rm -rf "$ROOT/backend/build/test-results/unitTest"
  (cd "$ROOT/backend" && sh gradlew unitTest --console=plain > "$REPORT_DIR/backend-unit.log" 2>&1); code=$?
  read -r t s f e <<< "$(junit_summary "$ROOT/backend/build/test-results/unitTest")"
  if [ "$t" -eq 0 ] && [ $code -ne 0 ]; then blocked unit "빌드/컴파일 실패 — test-reports/backend-unit.log"; else record unit $code "$t" "$s" $((f+e)); fi
else set_result unit "NOT RUN" "선택하지 않음"; fi

if wants integration; then
  echo "== 백엔드 통합 테스트 (테스트 DB)"
  rm -rf "$ROOT/backend/build/test-results/integrationTest"
  (cd "$ROOT/backend" && sh gradlew integrationTest --console=plain > "$REPORT_DIR/backend-integration.log" 2>&1); code=$?
  read -r t s f e <<< "$(junit_summary "$ROOT/backend/build/test-results/integrationTest")"
  if [ "$t" -eq 0 ] && [ $code -ne 0 ]; then blocked integration "$(grep -m1 -E '테스트 DB|error:' "$REPORT_DIR/backend-integration.log" | sed 's/^[> ]*//')"; else record integration $code "$t" "$s" $((f+e)); fi
else set_result integration "NOT RUN" "선택하지 않음"; fi

if wants frontend; then
  echo "== 프런트엔드 (lint · tsc · vitest · build)"
  cd "$ROOT/frontend"
  [ -d node_modules ] || npm ci > "$REPORT_DIR/frontend-install.log" 2>&1
  npm run lint > "$REPORT_DIR/frontend-lint.log" 2>&1; lint=$?
  npx tsc --noEmit > "$REPORT_DIR/frontend-tsc.log" 2>&1; tsc=$?
  npx vitest run --reporter=json --outputFile="$REPORT_DIR/vitest.json" > "$REPORT_DIR/frontend-vitest.log" 2>&1; vit=$?
  npm run build > "$REPORT_DIR/frontend-build.log" 2>&1; build=$?
  read -r t f s < <(python3 -c "import json;d=json.load(open('$REPORT_DIR/vitest.json'));print(d['numTotalTests'],d['numFailedTests'],d['numPendingTests']+d.get('numTodoTests',0))" 2>/dev/null || echo "0 0 0")
  code=$(( lint || tsc || vit || build ))
  record frontend $code "$t" "$s" "$f"; set_result frontend "$(get_status frontend)" "$(get_detail frontend) lint=$lint tsc=$tsc build=$build"
  cd "$ROOT"
else set_result frontend "NOT RUN" "선택하지 않음"; fi

if wants e2e; then
  echo "== E2E (Playwright + Chrome)"
  if port_open 8080 && [ "${E2E_REUSE_BACKEND:-0}" != "1" ]; then blocked e2e "8080 포트 사용 중(다른 백엔드). 종료하거나 테스트 DB로 띄운 경우 E2E_REUSE_BACKEND=1"
  elif ! command -v whisper-cli >/dev/null || [ ! -r "$MODEL" ]; then blocked e2e "whisper-cli 또는 모델 없음(scripts/ci/setup-whisper.sh)"
  else
    (cd "$ROOT/frontend" && npx playwright test > "$REPORT_DIR/e2e.log" 2>&1); code=$?
    # 결과는 playwright.config.ts의 json 리포터 파일(test-results/e2e-results.json)에서 읽는다
    read -r t f s < <(python3 -c "
import json;d=json.load(open('$ROOT/frontend/test-results/e2e-results.json'));st=d['stats'];print(st['expected']+st['unexpected']+st['flaky']+st['skipped'],st['unexpected'],st['skipped'])" 2>/dev/null || echo "0 0 0")
    if [ "$t" -eq 0 ]; then blocked e2e "서버 시작 실패 — test-reports/e2e.log"; else record e2e $code "$t" "$s" "$f"; fi
  fi
else set_result e2e "NOT RUN" "선택하지 않음"; fi

{
  echo "# 테스트 요약 ($(date '+%Y-%m-%d %H:%M'))"; echo
  echo "| 단계 | 결과 | 상세 |"; echo "|---|---|---|"
  for stage in unit integration frontend e2e; do echo "| $stage | $(get_status $stage) | $(get_detail $stage) |"; done
} | tee "$REPORT_DIR/summary.md"
for stage in unit integration frontend e2e; do case "$(get_status $stage)" in FAIL|BLOCKED) exit 1 ;; esac; done
exit 0
