# RAG 冻结集、消融与拒答评测设计

## 目标

在不改变在线推荐和检索行为的前提下，补齐一套可复现的离线 RAG 评测：

1. 冻结当前 18 条测试用例，避免测试集参与调参。
2. 在相同候选池上比较 Vector Only 与 Vector + BGE Rerank。
3. 将 No-answer 从边缘用例中独立出来，衡量错误回答风险。
4. 将数据集、索引、模型和代码版本与每次结果绑定，形成可追溯报告。

## 边界

- 保留 63 条开发集用于检索参数、Chunk 和 Metadata 策略调优。
- 当前 18 条 test 用例冻结为 `frozen-v1`，不在本次扩充为 20 条；新增独立 `safety-v1` 的 4 条 No-answer 用例。
- 评测只调用已有 `RagRetrievalService`，不经过 LLM、Agent 或用户会话。
- 本次不改变线上默认 TopK、路由和回答文案；拒答指标首先评测检索层是否错误给出证据。

## 数据集与版本

冻结集从 Java 静态数据迁移为资源文件，例如：

```text
src/test/resources/rag-eval/frozen-v1.jsonl
src/test/resources/rag-eval/frozen-v1.manifest.json
```

每条 JSONL 用例包含：

```json
{
  "id": "trend-test-01",
  "query": "深大计算机科学与技术近三年录取稳定？",
  "category": "trend",
  "expectedMetadata": {"school_id": "15", "program_id": "2501"},
  "relevantConditions": [],
  "relevantSources": [],
  "isNoAnswer": false
}
```

manifest 记录数据集版本、case 数、SHA-256、创建时间和说明。评测报告还必须记录 Git commit、Qdrant Alias 及其实际 Collection、Embedding 模型、Rerank 模型、向量召回池大小和最终 TopK。

冻结规则：任何基于 `frozen-v1` 失败用例的参数、Prompt、Chunk 或 Metadata 调整，都必须先在 dev 集完成并创建新冻结版本；不得原地修改 `frozen-v1`。

## 实验矩阵

同一条 query 在相同索引版本、Metadata Filter、模型和参数下执行两次：

```text
共同前置：Metadata Filter -> Vector Recall Top20

vector_only：Vector Top20 的前 5 条作为最终结果
vector_rerank：对 Vector Top20 执行 BGE Rerank，重排后取前 5 条
```

`recallTopN=20` 与 `finalTopK=5` 必须分离。不能只召回 5 条再重排 5 条，否则 Rerank 无法展示重排序价值。

每种模式输出：

- MRR、Hit@1、Hit@3、Hit@5、平均首个相关结果排名。
- 单 case 的 vector rank、rerank rank、改善/持平/退化标记。
- 端到端、Embedding、Vector Search 和 Rerank 阶段耗时；总延迟与 Rerank 阶段延迟的 P50/P95。

## 指标口径

对于存在相关证据的用例：

```text
RR_i = 1 / 第一个相关 Chunk 的排名；无相关 Chunk 时为 0
MRR = sum(RR_i) / 正样本数

Hit@K = 前 K 条至少出现一个相关 Chunk 的正样本数 / 正样本数
```

No-answer 用例不参与 MRR、Hit@K 的分母，避免“正确空结果”拉低检索排序指标。

`nDCG@5` 使用二值 relevance：

```text
DCG@5 = sum(rel_i / log2(i + 1)), i 从 1 开始
IDCG@5 = 前 min(相关证据总数, 5) 位全相关时的 DCG
nDCG@5 = DCG@5 / IDCG@5；无相关证据的 No-answer 不计入该指标
```

现有累积相关数参与 DCG 的逻辑需要改为逐位 `rel_i`，并使用正确的 IDCG，保证 nDCG 落在 [0, 1]。

Filter Accuracy 仅统计带 `expectedMetadata` 且系统返回候选 Chunk 的正样本：

```text
Filter Accuracy = 所有返回 Chunk 均符合预期 Metadata 的 case 数 / 被统计 case 数
```

空结果不能自动计作 Filter Accuracy 正确；空结果由召回和拒答指标处理。

## No-answer 与错误回答

No-answer 用例不混入 `frozen-v1`。新增独立 `safety-v1`，固定 4 条并覆盖：不存在学校、库外学校、库外年份、无法唯一定位意图。`frozen-v1` 只用于正样本排序、Filter 与 Rerank 消融；`safety-v1` 只用于拒答安全指标，两个集合都不得在调参过程中原地修改。

检索层的“可回答”定义为：最终选中 Chunk 中至少有一个满足有效 Metadata 约束并达到既有最小分数阈值的 Chunk；否则为“应拒答”。

```text
No-answer Accuracy = 正确拒答数 / No-answer case 数

False Positive Rate = 错误返回可作为答案的证据数 / No-answer case 数
```

报告同时保留每条 No-answer 的返回 Chunk、分数、过滤条件和拒答判定原因，便于检查错误相似召回。

## 结果产物与验收

每次运行生成不可覆盖的 JSON 报告，例如：

```text
target/rag-eval/2026-07-13T20-00-00-frozen-v1.json
```

报告包含 run metadata、全局指标、分类指标、两种模式对比、延迟分位数、逐 case 明细和失败原因。管理端可后续读取摘要，但命令行/测试报告是第一可信来源。

验收条件：

1. 18 条 `frozen-v1` 与 4 条 `safety-v1` 可独立加载，manifest 的 hash 校验通过。
2. 同一次运行能对 frozen-v1 输出 Vector Only 与 Vector + Rerank 的完整对比。
3. 报告将 No-answer 与正样本分开计数，并输出 No-answer Accuracy 与 False Positive Rate。
4. nDCG@5 始终在 0 到 1 之间。
5. 报告能够定位到确切 Git commit、Alias/Collection、模型和参数。
