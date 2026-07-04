# RAG / Qdrant 运维说明

本文是本地开发和演示环境的 RAG 速查手册。

## 组件

| 组件 | 作用 | 默认值 |
|------|------|--------|
| Qdrant | RAG 向量库 | `http://localhost:6333` / gRPC `6334` |
| Collection | RAG 文档集合 | `postgrad_rag_main` |
| Embedding | 文档与查询向量化 | SiliconFlow `BAAI/bge-m3` |
| Rerank | 检索片段重排 | SiliconFlow `BAAI/bge-reranker-v2-m3` |
| 文档目录 | classpath 文档来源 | `rag-documents/` |

## 启动

```powershell
docker compose up -d qdrant
curl http://localhost:6333/collections
```

Qdrant Web UI:

```text
http://localhost:6333/dashboard
```

## 后端环境变量

```powershell
$env:SILICONFLOW_API_KEY="sk-..."
$env:QDRANT_URL="http://localhost:6333"
$env:QDRANT_HOST="localhost"
$env:QDRANT_GRPC_PORT="6334"
$env:QDRANT_COLLECTION="postgrad_rag_main"
```

`QDRANT_API_KEY` 仅在 Qdrant 开启鉴权时设置；本地 Docker 默认不需要。

## 判断 RAG 是否启用

日志出现以下内容表示索引完成：

```text
[RagQdrant] Collection postgrad_rag_main created or updated
[RagInit] Starting index: found ...
[RagInit] Index complete
```

接口检查：

```powershell
curl http://localhost:6333/collections
curl http://localhost:6333/collections/postgrad_rag_main
```

管理后台页面：

```text
/postgrad/rag
```

关键状态：

| 字段 | 期望 |
|------|------|
| `enabled` | `true` |
| `indexed` | `true` |
| `reachable` | `true` |
| `backend` | `qdrant` |
| `lastError` | 空 |

## 重新索引

后端接口：

```text
POST /postgrad/rag/reindex
```

权限：

```text
postgrad:rag:reindex
```

重新索引会先确保 collection 存在，再清空当前 collection 的 points，然后重新切片、向量化并写入 Qdrant。

## 常见问题

| 现象 | 处理 |
|------|------|
| `Embedding API key not configured` | 检查 `SILICONFLOW_API_KEY` 是否被后端进程读取 |
| `Qdrant unavailable` | 确认 `docker ps` 中 `postgrad-qdrant` 正在运行，并检查 `curl http://localhost:6333/collections` |
| IDEA Docker 插件报 API `1.24` 太旧 | 不用 IDEA 启动 Docker，直接命令行 `docker compose up -d qdrant` |
| Qdrant dashboard 无 collection | 后端还未完成 RAG 初始化，或 `SILICONFLOW_API_KEY` 缺失 |
