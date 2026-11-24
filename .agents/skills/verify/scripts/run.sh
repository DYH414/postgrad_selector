#!/usr/bin/env bash
set -euo pipefail

scripts=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd "$scripts/../../../.." && pwd)
jar="$repo/ruoyi-admin/target/ruoyi-admin.jar"
command=${1:-}
run=${2:-}
if [[ ! "$run" =~ ^/tmp/postgrad-verify\.[[:alnum:]]{8}$ || ! -d "$run" || -L "$run" || ! -O "$run" ]]; then
  echo 'Pass an owned directory created by mktemp -d /tmp/postgrad-verify.XXXXXXXX.' >&2
  exit 1
fi
state="$run/state"
evidence="$run/evidence"
api=http://127.0.0.1:18080
admin=http://127.0.0.1:18081
ui=http://127.0.0.1:18082
socket="$state/mysql.sock"

process_start() {
  local stat fields
  read -r stat 2>/dev/null < "/proc/$1/stat" || return 1
  stat=${stat##*) }
  read -ra fields <<< "$stat"
  [[ ${fields[0]} != Z ]] || return 1
  printf '%s\n' "${fields[19]}"
}

owned_process() {
  local name=$1 pid started
  [[ -r "$state/$name.pid" ]] || return 1
  read -r pid started < "$state/$name.pid"
  [[ "$pid" =~ ^[0-9]+$ && "$(process_start "$pid")" == "$started" ]] || return 1
  printf '%s\n' "$pid"
}

start() {
  local name=$1 pid started
  shift
  nohup "$@" > "$evidence/$name.log" 2>&1 < /dev/null &
  pid=$!
  started=$(process_start "$pid")
  printf '%s %s\n' "$pid" "$started" > "$state/$name.pid"
}

listener() {
  local pid
  pid=$(owned_process "$1") || return 1
  ss -H -ltnp "sport = :$2" | grep -Fq "pid=$pid,"
}

http() {
  curl --noproxy '*' -fsS --max-time 10 "$1"
}

services_ready() {
  local service port
  while read -r service port; do
    listener "$service" "$port" || { echo "$service does not own port $port" >&2; return 1; }
  done <<'PORTS'
db 13306
redis 16379
api 18080
admin 18081
ui 18082
PORTS
  sha256sum --status -c "$evidence/build.sha256" || { echo 'Backend JAR changed after launch' >&2; return 1; }
  grep -Fq '[RagInit]' "$evidence/api.log" || { echo 'API startup runner is pending' >&2; return 1; }
  [[ "$(redis-cli -h 127.0.0.1 -p 16379 ping)" == PONG ]] || return 1
  http "$admin/dev-api/captchaImage" | python3 -c '
import json, sys
payload = json.load(sys.stdin)
assert payload["code"] == 200 and payload["captchaEnabled"] is False
' || return 1
  http "$ui/" | grep -Fq '408考研择校' || return 1
  http "$ui/dev-api/app/recommendation/options" | python3 -c '
import json, sys
payload = json.load(sys.stdin)
assert payload["code"] == 200, payload
data = payload["data"]
assert [item["code"] for item in data["examCombos"]] == ["11408", "22408"], data
assert data["regions"] == ["验证省"], data
assert data["defaultProfile"]["estimatedScore"] == 300, data
' || return 1
}

doctor() {
  services_ready || return 1
  node "$scripts/student-account.cjs" doctor "$ui" || return 1
  printf 'READY applicant %s, admin %s, API %s. Fixture options and student session verified.\n' "$ui" "$admin" "$api"
}

case "$command" in
  launch)
    [[ ! -e "$state" && ! -e "$evidence" ]] || { echo 'Use a fresh run directory.' >&2; exit 1; }
    for port in 13306 16379 18080 18081 18082; do
      [[ -z "$(ss -H -ltn "sport = :$port")" ]] || { echo "Port $port is already in use." >&2; exit 1; }
    done
    test -r "${POSTGRAD_TEST_ACCOUNTS:?Set POSTGRAD_TEST_ACCOUNTS to the local credentials file}"
    test -f "$jar"
    test -d "$repo/user-ui/node_modules/vite"
    test -f "$repo/ruoyi-ui/node_modules/@vue/cli-service/bin/vue-cli-service.js"
    mkdir -p "$state/"{db,redis,logs,uploads} "$evidence"
    sha256sum "$jar" > "$evidence/build.sha256"
    git -C "$repo" rev-parse HEAD > "$evidence/source-revision.txt"
    mariadb-install-db --no-defaults --datadir="$state/db" --skip-test-db > "$evidence/db-init.log" 2>&1
    start db /usr/sbin/mariadbd --no-defaults --datadir="$state/db" --socket="$socket" \
      --pid-file="$state/mariadb.pid" --bind-address=127.0.0.1 --port=13306
    for attempt in {1..40}; do
      if mariadb-admin --no-defaults --socket="$socket" ping >/dev/null 2>&1; then break; fi
      sleep 0.25
    done
    for script in 00_platform_bootstrap 10_postgrad_core_schema 20_ai_runtime_schema 30_postgrad_menu_permissions; do
      printf 'Importing %s.sql\n' "$script" >> "$evidence/schema.log"
      if [[ "$script" == 00_platform_bootstrap ]]; then
        mariadb --no-defaults --socket="$socket" < "$repo/sql/$script.sql" >> "$evidence/schema.log" 2>&1
      else
        mariadb --no-defaults --socket="$socket" postgrad_selector < "$repo/sql/$script.sql" >> "$evidence/schema.log" 2>&1
      fi
    done
    mariadb --no-defaults --socket="$socket" <<'SQL'
CREATE USER 'postgrad_verify'@'127.0.0.1';
GRANT ALL ON postgrad_selector.* TO 'postgrad_verify'@'127.0.0.1';
SQL
    mariadb --no-defaults --default-character-set=utf8mb4 --socket="$socket" postgrad_selector < "$scripts/fixtures.sql"
    python3 "$scripts/admin-account.py" | mariadb --no-defaults --socket="$socket" postgrad_selector
    start redis redis-server --bind 127.0.0.1 --port 16379 --dir "$state/redis" --save '' --appendonly no
    sed "s|/home/ruoyi/logs|$state/logs|g" "$repo/ruoyi-admin/src/main/resources/logback.xml" > "$state/logback.xml"
    cd "$state"
    JWT_SECRET=$(node -p "require('node:crypto').randomBytes(64).toString('base64')")
    export JWT_SECRET
    start api env -u OPENAI_API_KEY -u ANTHROPIC_API_KEY -u DEEPSEEK_API_KEY -u SILICONFLOW_API_KEY -u QDRANT_API_KEY \
      -u MYSQL_PASSWORD -u REDIS_PASSWORD java "-Dlogback.configurationFile=$state/logback.xml" -jar "$jar" \
      --server.address=127.0.0.1 --server.port=18080 --spring.main.lazy-initialization=true \
      "--logging.config=$state/logback.xml" "--ruoyi.profile=$state/uploads" "--app.ai.rag.data-dir=$state/rag" \
      --app.ai.rag.qdrant.url=http://127.0.0.1:1 --app.ai.rag.qdrant.host=127.0.0.1 --app.ai.rag.qdrant.grpc-port=1 \
      --spring.datasource.druid.master.username=postgrad_verify \
      --spring.datasource.druid.master.url=jdbc:mysql://127.0.0.1:13306/postgrad_selector \
      --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=16379
    unset JWT_SECRET
    start ui env -u VITE_APP_BASE_API node "$scripts/vite.mjs" "$repo/user-ui" "$state/vite-cache"
    cd "$repo/ruoyi-ui"
    start admin env NODE_OPTIONS=--openssl-legacy-provider VUE_APP_BASE_API=/dev-api VUE_APP_API_TARGET="$api" \
      node node_modules/@vue/cli-service/bin/vue-cli-service.js serve --host 127.0.0.1 --port 18081 --no-open
    for attempt in {1..120}; do
      if services_ready > "$evidence/readiness.log" 2>&1; then
        node "$scripts/student-account.cjs" register "$ui" | tee "$evidence/fixture-account.log"
        doctor | tee "$evidence/readiness.log"
        exit 0
      fi
      sleep 1
    done
    cat "$evidence/readiness.log" >&2
    printf 'Launch failed. Logs are in %s. Run cleanup.\n' "$evidence" >&2
    exit 1
    ;;
  doctor)
    doctor
    ;;
  drive)
    doctor | tee "$evidence/doctor.log"
    unset POSTGRAD_VERIFY_URL
    node "$scripts/screening.cjs" "$run" 2>&1 | tee "$evidence/screening.log"
    node "$scripts/student.cjs" "$evidence" 2>&1 | tee "$evidence/student.log"
    node "$scripts/admin.cjs" "$admin" "$evidence" 2>&1 | tee "$evidence/admin-workspace.log"
    ;;
  cleanup)
    for service in admin ui api redis db; do
      if pid=$(owned_process "$service"); then
        kill -TERM "$pid"
        for attempt in {1..50}; do
          if ! owned_process "$service" >/dev/null; then break; fi
          sleep 0.2
        done
        if owned_process "$service" >/dev/null; then
          printf '%s PID %s did not exit. State remains in %s.\n' "$service" "$pid" "$state" >&2
          exit 1
        fi
      fi
    done
    rm -rf -- "$state"
    printf 'Stopped verification processes. Evidence remains in %s.\n' "$evidence"
    ;;
  *)
    echo "Usage: $0 {launch|doctor|drive|cleanup} RUN_DIR" >&2
    exit 2
    ;;
esac
