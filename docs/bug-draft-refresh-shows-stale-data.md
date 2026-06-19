# 缺陷报告：AI 推荐 v2 — 点击"生成草稿"后刷新，右侧仍显示上一次的草稿

| 字段 | 值 |
|---|---|
| 模块 | `ruoyi-postgrad/recommend` + `user-ui/ai-recommend-v2` |
| 严重程度 | 中（数据正确性 OK，用户体验差，存在 5-7s 错误窗口） |
| 优先级 | P2（建议在本迭代修复） |
| 报告人 | Mavis (自动诊断) |
| 报告日期 | 2026-06-19 |
| 涉及文件 | `DraftServiceImpl.java`、`AiRecommendV2Workspace.vue` |

---

## 1. 问题描述

用户在前端点击「生成 AI 推荐草稿」后立即刷新页面，右侧候选栏仍然显示**上一次生成**的草稿内容。预期行为：要么显示空草稿 + 加载状态，要么显示"正在生成"占位。

后端业务实际仍在跑（异步任务未中断），业务完成后会写入新草稿，但**前端因为不轮询，看不到这次更新**，导致用户长时间面对"刚点了生成，怎么还是上次的？"的困惑。

---

## 2. 复现步骤

复现率：中高（取决于用户点击与刷新的相对时序）。

1. 完成一次完整的草稿生成（三档数据齐全，右侧显示 3 + 4 + 3 = 10 所学校）
2. 再次点击「生成 AI 推荐草稿」
3. 在 1 ~ 6 秒内按 F5 刷新页面
4. 观察右侧候选栏

**预期**：显示"正在生成"占位 / 空草稿 / 加载中
**实际**：显示上一次完整生成的草稿

5. 不再操作，等 30 秒后再次刷新

**预期**：显示新生成的草稿
**实际**：仍然显示上一次的（因为前端没轮询，不知道新草稿已就绪）

---

## 3. 根因分析

### 3.1 后端：`generateDraft` 入口未清空草稿 Redis

**位置**：`ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/service/impl/DraftServiceImpl.java:116-120`

```java
try {
    // 1. 终结旧对话 + 清除 Redis 聊天记忆（新草稿 = 新上下文）
    aiChatMapper.finalizeActiveConversations(userId);
    redisTemplate.delete("ai:v2:chat:msg:" + userId);
    redisTemplate.delete("ai:v2:chat:system:" + userId);
    // ❌ 缺失：redisTemplate.delete(draftKey(userId));
```

入口清理了三类相关状态——对话（DB）、聊天消息（Redis）、系统提示（Redis）——**唯独漏了候选草稿（Redis key `ai:v2:draft:<userId>`）**。

### 3.2 第一次 `saveDraft` 发生在第一档 AI 选择完成之后

**位置**：`DraftServiceImpl.java:228-230`

```java
// 每档完成后立即增量持久化（支持刷新恢复）
DraftVO partial = buildPartialDraft(resultTiers, allBlocked, basis, wsSummary);
saveDraft(userId, partial);
```

`saveDraft` 之前还有以下耗时操作：

| 阶段 | 大致耗时 |
|---|---|
| 构建候选池 `candidatePoolService.buildPool` | 1-2 s |
| 构建 Universe `universeService.buildUniverse` | < 1 s |
| 构建 Workspace `workspaceService.buildWorkspace` | < 1 s |
| 三档并行调 LLM 中**最快的一档**完成 | 3-5 s |

合计 **5-7 秒**的窗口期内，Redis 里的 `ai:v2:draft:<userId>` 仍是上一次的完整数据。

### 3.3 前端 `isIncomplete` 检测不到

**位置**：`user-ui/src/views/ai-recommend-v2/AiRecommendV2Workspace.vue:605-608`

```javascript
function isIncomplete(d) {
  if (!d || !d.tiers) return false
  return d.tiers.some(t => t.insufficient && t.insufficientReason && t.insufficientReason.includes('正在'))
}
```

三个条件都得满足：
1. 草稿不为空
2. 至少一档 `insufficient = true`
3. `insufficientReason` 文本**含"正在"**

但**上一次的完整草稿三档 `insufficient = false`**——`isIncomplete` 直接返回 `false`，**`startDraftPolling` 不启动**。

设计者原本的意图是：「我用'正在'这个关键字告诉前端这是占位符」。但**前提是后端在生成前先写一个占位符**——而 `generateDraft` 入口没做这一步。

### 3.4 整体时序

```
T0  用户点"生成"
T1  后端：
      - 加锁 (5min TTL)
      - 终结旧对话 ✓
      - 清聊天消息 ✓
      - 清系统提示 ✓
      - 清草稿 ✗  ← 缺这一步
      - 开始构建候选池 / Workspace / 跑 LLM
T2  用户在 T1.5 时按 F5
T3  前端 onMounted → loadDraftData → getDraft()
T4  Redis 返回：上一次生成的完整草稿 ← 完全合法的旧数据
T5  前端右侧显示：上一次草稿
T6  isIncomplete(旧草稿) = false → startDraftPolling 不启动
T7  后端业务在 5-7s 后完成第一档 → saveDraft 写新草稿
T8  前端：不知道，没轮询
T9  用户看到的还是上一次的草稿（除非手动再次刷新）
```

---

## 4. 影响范围

### 4.1 用户体验

- 用户点击"生成"后立即刷新 → 看到旧草稿 → 以为系统没响应
- 用户看到旧草稿可能误操作（基于旧数据做移除/替换）
- 部分用户会"刷新又刷新又刷新"才能看到新草稿

### 4.2 数据一致性（更隐蔽的问题）

三档是**并行跑**的，save 是**按 reach → steady → safe 顺序逐档持久化**。在中间窗口期，Redis 里可能是：

```json
{
  "tiers": [
    { "level": "reach", "candidates": [新生成的 3 所] },     ← 新的
    { "level": "steady", "candidates": [上一次的 4 所] },     ← 旧的
    { "level": "safe", "candidates": [上一次的 3 所] }        ← 旧的
  ]
}
```

如果用户在 T5 之后才刷新，**前端的"移除/替换"会作用在旧数据上**——比如用户想移除旧的稳档里某校，结果操作变成了"用旧稳档的 ID 去触发业务"，可能与新的 reach 档产生奇怪的联动（具体看 `DraftMutationServiceImpl.removeCandidate` 的实现，本次未深入排查）。

### 4.3 设计一致性问题

入口清理了三类状态却漏了草稿，**说明设计者认为"草稿不算上下文"**——但草稿本身就是 AI 推荐的核心产物，它的清理应该和 chat / system 一致。

---

## 5. 修复方案

### 5.1 推荐方案：入口写"正在生成"占位（最稳）

**位置**：`DraftServiceImpl.java:120` 之后插入

```java
// 2.5 写入"正在生成"占位草稿，让前端 isIncomplete 能识别并启动轮询
saveDraft(userId, buildGeneratingPlaceholder());
```

新增私有方法：

```java
/**
 * 构建"正在生成"占位草稿。
 * <p>三档都标 insufficient + 含"正在"关键字，触发前端 isIncomplete 轮询。</p>
 */
private DraftVO buildGeneratingPlaceholder() {
    DraftVO d = new DraftVO();
    List<TierCandidates> tiers = new ArrayList<>(3);
    tiers.add(placeholderTier("reach", "冲刺档", 3));
    tiers.add(placeholderTier("steady", "稳妥档", 4));
    tiers.add(placeholderTier("safe", "保底档", 3));
    d.setTiers(tiers);
    d.setRemovedCandidates(new ArrayList<>());
    d.setBlockedCandidates(new ArrayList<>());
    d.setGeneratedAt(LocalDateTime.now());
    return d;
}

private TierCandidates placeholderTier(String level, String label, int targetCount) {
    TierCandidates t = new TierCandidates();
    t.setLevel(level);
    t.setLabel(label);
    t.setTargetCount(targetCount);
    t.setCandidates(new ArrayList<>());
    t.setInsufficient(true);
    t.setInsufficientReason("AI 正在" + label + "挑选合适的学校...");
    return t;
}
```

**注意**：必须在 `try` 块内**加锁之后**调用，且 `buildProfileBasis` 还没构造（profile_basis 此时还是 null），所以 placeholder 里可以**不设 profileBasis**，等业务跑完再回填。

### 5.2 备选方案 A：直接删（最小改动）

```java
// 在第 120 行之后
redisTemplate.delete(draftKey(userId));
```

**优点**：1 行修复。
**缺点**：5-7s 窗口期 getDraft 返回 `null`，前端拿到空草稿——比"显示旧草稿"好，但比"显示正在生成占位"差，少了用户感知。

### 5.3 备选方案 B：前端强制轮询（防御性）

**位置**：`AiRecommendV2Workspace.vue:637-642` 的 onMounted

```javascript
onMounted(async () => {
  await loadProfileData()
  await loadDraftData()
  await loadChatHistory()

  // 防御性轮询：3 次 × 1.5s，不依赖后端 isIncomplete
  for (let i = 0; i < 3; i++) {
    await new Promise(r => setTimeout(r, 1500))
    await loadDraftData()
  }

  startDraftPolling()
})
```

**优点**：纯前端修，零后端改动。
**缺点**：治标不治本，3 次 × 1.5s = 4.5s 之后还是依赖 startDraftPolling；如果后端业务 > 7s，前端还是会卡在旧数据。

### 5.4 建议

**5.1（推荐方案）+ 5.3（备选方案 B）一起做**：
- 后端写占位：根因修复，所有依赖 isIncomplete 的逻辑都生效
- 前端防御性轮询：兜底，未来类似问题不会再逃过

---

## 6. 测试建议

### 6.1 单元测试

```java
@Test
void generateDraft_writesPlaceholderBeforeAiRunning() {
    // 准备：mock draftService.generateDraft 调用回调
    // 断言：第一次 saveDraft 调用时，draft 包含三档 insufficient 占位
}
```

### 6.2 集成测试

- **场景 1**：模拟用户点生成后 1s 内刷新，断言 `GET /draft` 返回的 JSON 中至少一档 `insufficient=true` 且 `insufficientReason` 含"正在"
- **场景 2**：等待业务跑完，断言 `GET /draft` 返回完整新草稿，且所有 `insufficient=false`
- **场景 3**：手工构造一个"三档都是旧数据"的初始状态，调用 `generateDraft`，断言 100ms 内 Redis 中草稿变为占位状态

### 6.3 前端测试

- **场景 1**：onMounted 时如果草稿三档 `insufficient=true` 且 reason 含"正在"，2s 内必须启动 `startDraftPolling`
- **场景 2**：onMounted 时草稿为 `null`（被后端直接删了），应显示"暂无草稿，点击生成"占位
- **场景 3**：onMounted 时草稿为完整旧数据，**3 次 × 1.5s 防御性轮询** 期间即使后端没写新草稿，前端也只显示旧数据（不显示"正在生成"），等轮询结束后交给 `startDraftPolling` 接管

### 6.4 手工验证

1. 完整生成一次草稿（确认右侧显示 10 所）
2. 再次点击「生成 AI 推荐草稿」
3. 1 秒内按 F5
4. **期望**：右侧显示"正在生成"占位（三档都标 loading）
5. **期望**：左侧进度条继续推进 / 或弹"草稿已恢复"
6. 等业务跑完
7. **期望**：右侧显示新草稿，弹"草稿已恢复"提示

---

## 7. 附录

### 7.1 关键代码位置速查

| 文件 | 行号 | 内容 |
|---|---|---|
| `DraftServiceImpl.java` | 116-120 | 入口清理（缺清草稿） |
| `DraftServiceImpl.java` | 228-230 | 第一次 saveDraft 位置 |
| `DraftServiceImpl.java` | 443 | mergeSelection 中 insufficientReason 含"正在" |
| `AiRecommendV2Workspace.vue` | 605-608 | `isIncomplete` 检测逻辑 |
| `AiRecommendV2Workspace.vue` | 610-633 | `startDraftPolling` 轮询逻辑 |
| `AiRecommendV2Workspace.vue` | 637-642 | onMounted（建议增加防御性轮询） |

### 7.2 相关概念参考

- **异步任务 + EventSource**：`DraftGenerationTaskServiceImpl` 维护 `taskId + streamToken`，前端 SSE 订阅进度
- **生成锁**：`SETNX + TTL 5min + Lua 原子解锁`，防重复点击
- **增量持久化**：每档 AI 选择完成就 saveDraft，支持刷新恢复
- **`insufficient` 字段**：`TierCandidates` 中的布尔字段，配合 `insufficientReason` 描述原因
