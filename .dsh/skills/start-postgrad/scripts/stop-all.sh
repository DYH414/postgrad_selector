#!/bin/bash
RT="$HOME/postgrad-runtime"
source "$RT/env.sh"
echo "Stopping backend..."
pkill -f '/opt/postgrad/ruoyi-admin.jar' 2>/dev/null && sleep 2
echo "Stopping Qdrant..."
pkill -f "$RT/qdrant-bin" 2>/dev/null
echo "Stopping Redis..."
redis-cli -a "$REDIS_PASSWORD" shutdown nosave >/dev/null 2>&1 || true
echo "Stopping MySQL..."
/www/server/mysql/bin/mysqladmin --socket="$RT/mysql/run/mysqld.sock" -uroot -p"$MYSQL_PASSWORD" shutdown 2>/dev/null || true
echo "Done."
