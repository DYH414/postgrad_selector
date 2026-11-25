#!/bin/bash
# Minimal one-command start for the local 408 postgrad platform.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
RT="$HOME/postgrad-runtime"

[ -f "$RT/env.sh" ] || { echo "缺少 $RT/env.sh（本地密钥文件）"; exit 1; }
source "$RT/env.sh"
[ -d "$RT/mysql/data" ] || { echo "缺少 $RT/mysql/data，请按 SKILL.md 的“重建运行目录”初始化"; exit 1; }
[ -f /opt/postgrad/ruoyi-admin.jar ] || { echo "缺少 /opt/postgrad/ruoyi-admin.jar"; exit 1; }

SOCK="$RT/mysql/run/mysqld.sock"
MYSQLD=/www/server/mysql/bin/mysqld
MYSQLADMIN=/www/server/mysql/bin/mysqladmin

wait_port() {
  local port=$1 n=${2:-60}
  for _ in $(seq 1 "$n"); do
    (exec 3<>/dev/tcp/127.0.0.1/"$port") 2>/dev/null && return 0
    sleep 2
  done
  return 1
}

echo "[1/4] MySQL"
if "$MYSQLADMIN" --socket="$SOCK" -uroot -p"$MYSQL_PASSWORD" ping >/dev/null 2>&1; then
  echo "  already running"
else
  mkdir -p "$RT"/mysql/run "$RT"/logs
  setsid nohup "$MYSQLD" --no-defaults --basedir=/www/server/mysql \
    --datadir="$RT/mysql/data" --socket="$SOCK" --pid-file="$RT/mysql/run/mysqld.pid" \
    --port=3306 --bind-address=127.0.0.1 --mysqlx=0 --skip-log-bin \
    --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci \
    --max-connections=200 --innodb-buffer-pool-size=256M \
    --log-error="$RT/logs/mysql.log" >/dev/null 2>&1 &
  for _ in $(seq 1 30); do
    "$MYSQLADMIN" --socket="$SOCK" -uroot -p"$MYSQL_PASSWORD" ping >/dev/null 2>&1 && break
    sleep 1
  done
  "$MYSQLADMIN" --socket="$SOCK" -uroot -p"$MYSQL_PASSWORD" ping >/dev/null 2>&1 \
    && echo "  started" || { echo "  FAILED (see $RT/logs/mysql.log)"; exit 1; }
fi

echo "[2/4] Redis"
if redis-cli -a "$REDIS_PASSWORD" ping >/dev/null 2>&1; then
  echo "  already running"
else
  setsid nohup redis-server --bind 127.0.0.1 --port 6379 --requirepass "$REDIS_PASSWORD" \
    --dir "$RT/redis" --save '' --appendonly no --daemonize no \
    > "$RT/logs/redis.log" 2>&1 &
  for _ in $(seq 1 15); do
    redis-cli -a "$REDIS_PASSWORD" ping >/dev/null 2>&1 && break
    sleep 1
  done
  redis-cli -a "$REDIS_PASSWORD" ping >/dev/null 2>&1 \
    && echo "  started" || { echo "  FAILED (see $RT/logs/redis.log)"; exit 1; }
fi

echo "[3/4] Qdrant"
if curl -sf http://127.0.0.1:6333/healthz >/dev/null; then
  echo "  already running"
else
  mkdir -p "$RT"/qdrant/storage
  QDRANT__STORAGE__STORAGE_PATH="$RT/qdrant/storage" \
  QDRANT__SERVICE__HTTP_PORT=6333 QDRANT__SERVICE__GRPC_PORT=6334 \
  setsid nohup "$RT/qdrant-bin" > "$RT/logs/qdrant.log" 2>&1 &
  for _ in $(seq 1 30); do
    curl -sf http://127.0.0.1:6333/healthz >/dev/null && break
    sleep 1
  done
  curl -sf http://127.0.0.1:6333/healthz >/dev/null \
    && echo "  started" || { echo "  FAILED (see $RT/logs/qdrant.log)"; exit 1; }
fi

echo "[4/4] Backend"
if (exec 3<>/dev/tcp/127.0.0.1/8080) 2>/dev/null; then
  echo "  already running"
else
  setsid nohup bash "$HERE/start-backend.sh" >/dev/null 2>&1 &
  wait_port 8080 60 || { echo "  FAILED (see $RT/logs/backend.log)"; exit 1; }
  echo "  started"
fi

echo
echo "MySQL : 127.0.0.1:3306"
echo "Redis : 127.0.0.1:6379"
echo "Qdrant: http://127.0.0.1:6333"
echo "API   : http://127.0.0.1:8080"
curl -sf http://127.0.0.1:8080/captchaImage >/dev/null && echo "Backend: OK"
curl -s -o /dev/null -w 'Site   : http://408lab.site (%{http_code})\n' http://408lab.site/
USER_TITLE=$(curl -s http://408lab.site/ | sed -n 's:.*<title>\(.*\)</title>.*:\1:p' | head -1)
[ "$USER_TITLE" = "408考研择校" ] || echo "WARN   : 首页 title=$USER_TITLE，可能被停机维护页覆盖，见 SKILL.md"
ADMIN_TITLE=$(curl -s http://408lab.site/admin/ | sed -n 's:.*<title>\(.*\)</title>.*:\1:p' | head -1)
[ "$ADMIN_TITLE" = "408考研择校" ] || echo "WARN   : 管理端 title=$ADMIN_TITLE，可能被停机维护页覆盖，见 SKILL.md"
echo "Admin  : https://408lab.site/admin/"
