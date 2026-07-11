# AI Agent Runtime 加固 — 验收测试计划

> **文档定位**：独立验收计划。实施者按此文档逐项验证，全部 PASS 则本次改动达标。
> **前置条件**：Qdrant 已启动、DeepSeek API Key 已配置、SiliconFlow API Key 已配置。测试环境 `postgrad_selector` 数据库已初始化。

---

## 一、流水式聊天并发控制（streaming-chat-concurrency）

### V1.1 并发消息拒绝

**目标**：同一用户的第二条消息在第一条流未结束时被拒绝。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 用户 A 发送聊天消息，触发流式回复（SSE 连接保持） | SSE 开始推送 token | — |
| 2 | 在 SSE 未结束前，用户 A 发送第二条消息 | 收到 HTTP 拒绝响应（非 200），消息未持久化 | `pass` / `fail` |
| 3 | 确认第二条消息未出现在对话历史中 | `GET /app/v2/chat/resume` 中无该消息 | `pass` / `fail` |

### V1.2 流结束后锁释放

**目标**：流正常结束后，下一条消息可以立即发送。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 用户 A 发送消息 → SSE 完整接收 → `onDone` 触发 | SSE 连接关闭 | — |
| 2 | 立即发送下一条消息 | HTTP 200，SSE 正常启动 | `pass` / `fail` |

### V1.3 流异常后锁释放

**目标**：流出错后，锁被释放，后续消息不受阻。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 模拟模型调用失败（如传非法参数触发 onError） | 流结束，`onError` 触发 | — |
| 2 | 发送下一条正常消息 | HTTP 200，正常回复 | `pass` / `fail` |

### V1.4 过期锁不误删新锁

**目标**：锁 TTL 过期后，旧运行的回调不删除新运行的锁。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 验证每个锁含有唯一 owner 值 | 查看 Redis `ai:v2:chat:lock:<userId>` value 为非空唯一字符串 | `pass` / `fail` |
| 2 | 验证锁删除使用 "仅当 value 匹配时删除" 语义 | 使用 Lua 脚本或 `SETNX` + value-check 方式释放 | `pass` / `fail` |

---

## 二、RAG 路由与证据范围（rag-search-testing）

### V2.1 通用知识问题走通用检索

**目标**：政策/考试/方法论类问题使用全量 RAG 检索。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 发送 "408 考什么科目？" | 回答包含四门科目，来源为通用知识文档 | `pass` / `fail` |
| 2 | 检查 `GET /postgrad/rag/traces` | trace 中 `retrievalRoute=general` | `pass` / `fail` |
| 3 | 发送 "什么是国家线？A 区和 B 区有什么区别？" | 回答含 A/B 区定义和分数差异 | `pass` / `fail` |

### V2.2 事实型问题使用作用域证据

**目标**：候选/学校/专业/录取分数/风险类问题仅使用带 Metadata Filter 的程序证据检索。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 生成一份草稿（如果还没有） | — | — |
| 2 | 发送 "为什么推荐北京大学-计算机技术？" 或 "这个方向有什么风险？" | 回答仅引用 scoped 程序证据，不含通用知识文档片段 | `pass` / `fail` |
| 3 | 检查 trace | trace 中 `retrievalRoute=scoped`，`metadataFilters` 非空 | `pass` / `fail` |

### V2.3 作用域证据不足时的兜底

**目标**：严格作用域检索无结果时，按配置的兜底策略处理（如放宽 year 范围），不混入通用 RAG。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 查询一个无匹配程序证据的草稿候选 | trace 中 `fallbackUsed=true`，检索范围仍限定在程序证据内（不跨到通用文档） | `pass` / `fail` |
| 2 | 检查 trace | `retrievalRoute=scoped`，`fallbackMode` 有值 | `pass` / `fail` |

### V2.4 事实型请求不注入通用 RAG 上下文

**目标**：分类为事实型的聊天不使用通用 RetrievalAugmentor。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 事实型请求中故意混入政策类关键词（如 "请解释计算机技术的录取趋势，408 考试需要多少分才能上"） | 请求被分类为事实型，AI 使用 scoped 证据回答专业部分，不注入通用 RAG 文档 | `pass` / `fail` |
| 2 | 检查 trace | 无 general 来源的 chunk 出现在 selected 列表 | `pass` / `fail` |

---

## 三、Agent 草稿操作确认（agent-action-confirmation）

### V3.1 写工具返回提案而非直接修改

**目标**：AI 调用草稿写工具时，不再直接 mutating，而是返回提案 + 确认令牌。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 在 AI 对话中说 "帮我移除草稿中的 XX 学校" | AI 返回提案（含拟移除学校信息、diff 摘要、确认提示），而非直接执行 | `pass` / `fail` |
| 2 | 检查草稿状态 | `GET /app/v2/draft` → 草稿未变化（学校仍在） | `pass` / `fail` |

### V3.2 确认令牌有效 → 执行操作

**目标**：用户携带有效确认令牌调用确认接口，操作执行成功。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 从提案响应中获取 `confirmationToken` | — | — |
| 2 | 调用 `POST /app/v2/draft/confirm`，传 token | HTTP 200，草稿真实变更 | `pass` / `fail` |
| 3 | 再次查草稿 | 学校已被移除 | `pass` / `fail` |

### V3.3 令牌一次性消费

**目标**：同一令牌重复调用拒绝。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 对已确认过的 token 再次调用 confirm | HTTP 4xx，草稿不变 | `pass` / `fail` |

### V3.4 跨用户令牌隔离

**目标**：用户 B 不能使用用户 A 的确认令牌。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 用户 A 生成提案 → 获取 token | — | — |
| 2 | 用户 B 使用用户 A 的 token 调用 confirm | HTTP 4xx，草稿不变 | `pass` / `fail` |

### V3.5 草稿版本冲突拒绝

**目标**：提案和确认之间草稿被修改，拒绝执行。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | AI 生成提案（基于版本 v1） | — | — |
| 2 | 通过 REST API 手动修改草稿（版本变为 v2） | — | — |
| 3 | 确认 v1 的 token | HTTP 4xx（冲突），草稿保持 v2 | `pass` / `fail` |

### V3.6 令牌过期拒绝

**目标**：超过有效期（默认建议 60 秒）的令牌被拒绝。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | AI 生成提案 | — | — |
| 2 | 等待超过 TTL | — | — |
| 3 | 确认 | HTTP 4xx（过期） | `pass` / `fail` |

---

## 四、Agent 运行关联标识（agent-run-correlation）

### V4.1 每次聊天请求生成 runId

**目标**：每个聊天请求有唯一 runId。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 发送聊天消息 | — | — |
| 2 | 检查 SSE 最后一个事件 | 事件中携带 `runId` 字段 | `pass` / `fail` |
| 3 | 连续发送两条消息 | 两个 runId 不同 | `pass` / `fail` |

### V4.2 Tool 调用关联 runId

**目标**：同一 runId 贯通 Tool 调用。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 发送需要 Tool 调用的消息（如"分析草稿中的学校"） | AI 调用 getDraftContext | — |
| 2 | 检查 trace | trace 中 `runId` = SSE 事件中的 `runId` | `pass` / `fail` |

### V4.3 RAG 检索引入 runId

**目标**：RAG trace 带 runId。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 在 RAG 管理后台执行 search-test | — | — |
| 2 | 检查 trace | trace 中 `runId` 字段存在且非空 | `pass` / `fail` |
| 3 | AI 对话触发 RAG 检索 | trace 中 runId 与 SSE 事件的 runId 一致 | `pass` / `fail` |

### V4.4 异常路径 runId 不丢失

**目标**：流失败时 runId 仍然出现在终端事件中。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 触发会导致流失败的场景 | — | — |
| 2 | 检查 `onError` 的 SSE 事件 | 事件携带 runId | `pass` / `fail` |
| 3 | 检查 trace | trace 中 runId 与 SSE 事件一致 | `pass` / `fail` |

---

## 五、可观测性扩展（rag-trace-observability）

### V5.1 Trace 包含 retrievalRoute

**目标**：每条 AI Chat RAG trace 明确标注路由类型。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | 通用知识查询 → 查看 trace | `retrievalRoute=general` | `pass` / `fail` |
| 2 | 程序事实查询 → 查看 trace | `retrievalRoute=scoped` | `pass` / `fail` |

### V5.2 Trace 包含 metadataFilters

**目标**：作用域检索的 trace 显示出应用的元数据过滤条件。

| 步骤 | 操作 | 预期结果 | 判定 |
|------|------|----------|------|
| 1 | scoped 检索 → 查看 trace | `metadataFilters` 字段非空（含 school_id, program_id 等） | `pass` / `fail` |

---

## 六、单元测试回归

**目标**：所有已有测试保持通过，新增测试覆盖核心契约。

### U1 后端单测

```bash
mvn test -pl ruoyi-postgrad -Dmaven.test.skip=false
```

| 判定项 | 预期 | 实际 |
|--------|------|------|
| 所有测试类通过 | 0 failure, 0 error | `pass` / `fail` |
| 至少存在以下新增测试类：`ChatLockTest` / `RagRoutingTest` / `ActionConfirmationTest` / `RunIdPropagationTest` | 全部存在 | `pass` / `fail` |

### U2 新增关键测试场景

| 测试场景 | 验证内容 | 判定 |
|----------|---------|------|
| 并发消息在流激活期间被拒绝 | 单元/集成级别 | `pass` / `fail` |
| 事实型路由不注入通用 RAG | Mock RAG + 断言 assistant 构建参数 | `pass` / `fail` |
| 确认令牌成功/过期/重放/跨用户/版本冲突 | 5 个参数化或独立测试 | `pass` / `fail` |
| runId 从请求到 SSE 事件全程传递 | Mock 流 + 捕获事件 | `pass` / `fail` |
| 过期锁不误删新锁 | owner 值对比 | `pass` / `fail` |

---

## 七、接口测试

**目标**：关键 API 行为符合 spec。

| # | 端点 | 方法 | 测试点 | 判定 |
|---|------|------|--------|------|
| I1 | `/app/v2/chat/send` | POST | 正常消息正常回复（SSE） | `pass` / `fail` |
| I2 | `/app/v2/chat/send` | POST | SSE 最后一个事件含 `runId` | `pass` / `fail` |
| I3 | `/app/v2/chat/send` | POST | 重复请求被拒绝（并发场景） | `pass` / `fail` |
| I4 | `/app/v2/chat/send` | POST | 通用知识问题用 general 路由 | `pass` / `fail` |
| I5 | `/app/v2/chat/send` | POST | 候选解释问题用 scoped 路由 | `pass` / `fail` |
| I6 | `/app/v2/draft/confirm` | POST | 有效 token 执行成功 | `pass` / `fail` |
| I7 | `/app/v2/draft/confirm` | POST | 无效/过期/重放 token 被拒绝 | `pass` / `fail` |
| I8 | `/postgrad/rag/search-test` | POST | 正常返回 | `pass` / `fail` |
| I9 | `/postgrad/rag/traces` | GET | trace 含 runId + retrievalRoute | `pass` / `fail` |

---

## 八、性能与效果

### P1 聊天延迟

**目标**：RAG 路由、锁管理不显著增加首 token 延迟。

| 指标 | 基线（改动前） | 目标（改动后） | 判定 |
|------|--------------|--------------|------|
| 首 token 延迟（P50） | 实测值 | ≤ 基线 + 200ms | `pass` / `fail` |
| 流结束到锁释放间隔 | — | ≤ 500ms | `pass` / `fail` |
| 确认令牌执行延迟 | — | ≤ 200ms（Redis 原子操作） | `pass` / `fail` |

### P2 锁竞争

**目标**：异常路径下锁不漏、不死锁。

| 场景 | 预期 | 判定 |
|------|------|------|
| 连续正常对话 10 条 | 每条都成功，无拒绝 | `pass` / `fail` |
| 正常 → 异常 → 正常 | 异常后第一条正常消息不被拒绝 | `pass` / `fail` |
| 快速连发 5 条（压测并发） | 仅第一条执行，其余被拒绝，无死锁 | `pass` / `fail` |

### P3 RAG 路由准确性

**目标**：分类路由正确率 ≥ 95%（基于设计文档中的保守分类器预期）。

| 测试集 | 预期 | 判定 |
|--------|------|------|
| 10 条通用知识问题 | ≥ 9 条走 general 路由 | `pass` / `fail` |
| 10 条候选/专业问题 | ≥ 9 条走 scoped 路由 | `pass` / `fail` |

---

## 九、验收清单

| # | 能力域 | 验收项 | 通过标准 |
|---|--------|--------|---------|
| 1 | 并发控制 | V1.1–V1.4 全部通过 | 锁持有覆盖完整流、owner 安全释放、异常释放、并发拒绝 |
| 2 | RAG 路由 | V2.1–V2.4 全部通过 | 通用 vs 作用域路由正确、不跨域污染、兜底不混入通用 RAG |
| 3 | 操作确认 | V3.1–V3.6 全部通过 | 提案/确认两阶段、一次性/跨用户/版本冲突/过期均拒绝 |
| 4 | 关联标识 | V4.1–V4.4 全部通过 | runId 从请求到 SSE/Tool/RAG/Trace 全程贯通 |
| 5 | 可观测性 | V5.1–V5.2 全部通过 | trace 含 retrievalRoute + metadataFilters |
| 6 | 单元测试 | U1–U2 全部通过 | 无回归，新增 4 类关键测试 |
| 7 | 接口测试 | I1–I9 全部通过 | 所有端点行为符合 spec |
| 8 | 性能 | P1–P3 全部达标 | 延迟不恶化、无死锁、路由准确率 ≥ 95% |

---

**所有 8 个域全部 PASS → 验收通过，可以合并。**
