---
name: start-postgrad
description: 以最少步骤在本机启动 408 考研择校平台（MySQL、Redis、Qdrant、Spring Boot 后端，前端由 nginx 托管），并验证站点可用。
---

# 启动 408 考研择校平台

用于用户说“启动/重启这个项目”、`408lab.site` 打不开，或本机 `8080` 无响应时。

## 最短路径

在仓库根目录执行：

```bash
bash .dsh/skills/start-postgrad/scripts/start-all.sh
```

脚本是幂等的：已运行的服务会跳过，缺失的按 MySQL → Redis → Qdrant → 后端顺序拉起，最后输出地址与健康检查。正常情况下不需要 `mvn`、`npm` 或 `docker`。

启动后：

- 用户端：<http://408lab.site>（HTTPS 证书续签后可用 https）
- 管理端：<http://408lab.site/admin/>
- 本机 API：<http://127.0.0.1:8080>
- Qdrant：<http://127.0.0.1:6333/dashboard>

## 本机部署拓扑

| 组件 | 运行方式 | 位置 |
|---|---|---|
| MySQL 8.0 | 用户态 `mysqld`，127.0.0.1:3306 | `~/.postgrad-runtime/mysql/data` |
| Redis 7 | 用户态 `redis-server`，127.0.0.1:6379 | `~/.postgrad-runtime/redis` |
| Qdrant 1.13.4 | 用户态二进制，6333/6334 | `~/.postgrad-runtime/qdrant/storage` |
| 后端 | `/opt/postgrad/ruoyi-admin.jar` | 日志 `~/.postgrad-runtime/logs/backend.log` |
| 前端 | nginx 静态文件，已预构建 | `/www/wwwroot/408lab.site`（`/admin` 为管理端） |

- 后端启动脚本：`scripts/start-backend.sh`，停止脚本：`scripts/stop-all.sh`。
- 数据库、JWT、DeepSeek 等密钥只放在 `~/.postgrad-runtime/env.sh`（权限 600）。不要写入仓库、日志或聊天。
- 管理端账号是部署机本地凭据，不在本文件中记录。

## 验证（10 秒）

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/captchaImage
curl -s http://127.0.0.1:6333/healthz
curl -s -o /dev/null -w '%{http_code}\n' http://408lab.site/
curl -s -o /dev/null -w '%{http_code}\n' http://408lab.site/admin/
# 首页标题必须是 408考研择校；出现“服务已暂停”说明 index.html 被停机页覆盖
curl -s http://408lab.site/ | grep -o '<title>[^<]*</title>'
curl -s http://408lab.site/admin/ | grep -o '<title>[^<]*</title>'
# 证书有效期（过期时浏览器会拦截 HTTPS）
echo | openssl s_client -connect 127.0.0.1:443 -servername 408lab.site 2>/dev/null | openssl x509 -noout -enddate
```

期望依次为 `200`、`healthz check passed`、`200`、`200`。Qdrant 集合与别名：

```bash
curl -s http://127.0.0.1:6333/aliases
```

期望 `postgrad_rag_active` 指向一个 `postgrad_rag_*` 集合。

## 手动步骤（脚本不可用时）

```bash
RT="$HOME/postgrad-runtime"
source "$RT/env.sh"
```

1. MySQL：

```bash
/www/server/mysql/bin/mysqld --no-defaults --basedir=/www/server/mysql \
  --datadir="$RT/mysql/data" --socket="$RT/mysql/run/mysqld.sock" \
  --pid-file="$RT/mysql/run/mysqld.pid" --port=3306 --bind-address=127.0.0.1 \
  --mysqlx=0 --skip-log-bin --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci --innodb-buffer-pool-size=256M \
  --log-error="$RT/logs/mysql.log" >/dev/null 2>&1 &
```

2. Redis：

```bash
redis-server --bind 127.0.0.1 --port 6379 --requirepass "$REDIS_PASSWORD" \
  --dir "$RT/redis" --save '' --appendonly no --daemonize no \
  > "$RT/logs/redis.log" 2>&1 &
```

3. Qdrant：

```bash
QDRANT__STORAGE__STORAGE_PATH="$RT/qdrant/storage" \
QDRANT__SERVICE__HTTP_PORT=6333 QDRANT__SERVICE__GRPC_PORT=6334 \
  "$RT/qdrant-bin" > "$RT/logs/qdrant.log" 2>&1 &
```

4. 后端：

```bash
bash .dsh/skills/start-postgrad/scripts/start-backend.sh &
```

## 常见问题

- **不要用 `systemctl start mysql|redis`**：当前账号没有 sudo，会要求交互认证。使用上面的用户态实例。
- **MySQL 报 `Public Key Retrieval is not allowed`**：本机 JDBC URL 没有 `allowPublicKeyRetrieval`，所以 root 账号必须使用 `mysql_native_password` 插件。修复：
  `ALTER USER 'root'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY '<密码>';`
- **后端因 `/home/ruoyi/logs` 权限启动失败**：jar 内的 logback 配置写死该路径，必须通过 `-Dlogback.configurationFile=$RT/logback.xml` 覆盖（启动脚本已处理）。
- **Qdrant 存储复制时报 `Permission denied`**：个别 payload index 文件归属 root；集合通常仍能恢复。启动后核对 `/collections` 和 points 数即可。
- **端口冲突**：先 `ss -ltn` 或 `curl` 检查，脚本已做“已运行则跳过”，不要重复拉起。
- **80/443 不通或 HTTPS 证书过期**：nginx 与证书都由 root/宝塔管理，agent 无法重启或安装新证书；需要在宝塔面板续签证书并重载 nginx。可用 `http://408lab.site` 临时访问。
- **首页显示“服务已暂停”/标题是“408 考研择校 · 服务已暂停”**：说明 `/www/wwwroot/408lab.site/index.html`（或 `admin/index.html`）被停机维护页覆盖，后端和 assets 其实正常。用发布包里与当前 assets 匹配的 index 恢复：
  ```bash
  mkdir -p /tmp/pgdist /tmp/pgadmin
  tar xzf /opt/postgrad/releases/20260715-134545/user-ui-dist.tar.gz -C /tmp/pgdist
  tar xzf /opt/postgrad/releases/20260715-134545/ruoyi-ui-dist.tar.gz -C /tmp/pgadmin
  cp /tmp/pgdist/index.html /www/wwwroot/408lab.site/index.html
  cp /tmp/pgadmin/index.html /www/wwwroot/408lab.site/admin/index.html
  cp /tmp/pgadmin/index.html.gz /www/wwwroot/408lab.site/admin/index.html.gz
  ```
  恢复后刷新浏览器（必要时清缓存），标题应为 `408考研择校`。
- **首次或空库**：导入 `/opt/postgrad/postgrad_selector.sql`，再执行 `/opt/postgrad/releases/20260715-134545/*.sql` 和 `/opt/postgrad/add_recommendation_indexes.sql`（重复索引报错可忽略）。RAG 索引在 Qdrant 中，已有 `postgrad_rag_active` 别名时无需重建。

## 重建运行目录（仅当 `~/.postgrad-runtime` 丢失）

```bash
RT="$HOME/postgrad-runtime"
mkdir -p "$RT"/{logs,mysql/data,mysql/run,redis,qdrant/storage,uploadPath,rag-data}
/www/server/mysql/bin/mysqld --no-defaults --initialize-insecure \
  --basedir=/www/server/mysql --datadir="$RT/mysql/data"
```

初始化后设置 `mysql_native_password` 密码，创建 `postgrad_selector` 库（`utf8mb4`）并导入
`/opt/postgrad/postgrad_selector.sql`。Qdrant 二进制可从
`https://github.com/qdrant/qdrant/releases/download/v1.13.4/qdrant-x86_64-unknown-linux-musl.tar.gz`
获取（直连慢时用 `https://gh-proxy.com/` 前缀），存储从
`/opt/postgrad/qdrant/storage` 复制。重建 `logback.xml`（jar 内配置把日志目录写死为
`/home/ruoyi/logs`，需要改到可写目录）：

```bash
unzip -p /opt/postgrad/ruoyi-admin.jar BOOT-INF/classes/logback.xml > "$RT/logback.xml"
sed -i "s#/home/ruoyi/logs#$RT/logs#g" "$RT/logback.xml"
```

最后重新生成 `env.sh`，再执行 `bash .dsh/skills/start-postgrad/scripts/start-all.sh`。
