# RAG Frozen V3 Real Evaluation

## Environment

- Environment: local acceptance
- Run date: 2026-07-23
- Qdrant alias: `postgrad_rag_active`
- Qdrant collection: `postgrad_rag_1784793522427_dc4f89ae`
- Retrieval ground truth: reviewed evidence `content_hash`
- Recall Top N: 20
- Final Top K: 5
- Retrieval threshold: 0.5
- Generator and judge model: `deepseek-v4-flash`
- Git commit: `43368b6a0c1d219665919bdcdab1baee110e0f46`
- Worktree at run time: dirty

## Frozen Retrieval Set

- Positive cases: 36
- General knowledge: 12
- School admission guides: 24
- Dataset SHA-256: `54b47ec46f5994d77793768836b0b43e8be41abf75dfea8418a48933777b2e15`

| Metric | Vector only | Vector + BGE Rerank | Change |
| --- | ---: | ---: | ---: |
| MRR | 0.7963 | 0.8366 | +0.0403 |
| Hit@1 | 75.00% | 77.78% | +2.78 pp |
| Hit@3 | 83.33% | 88.89% | +5.56 pp |
| Hit@5 | 88.89% | 94.44% | +5.56 pp |
| nDCG@5 | 0.8192 | 0.8633 | +0.0441 |
| P50 latency | 160 ms | 405 ms | +245 ms |
| P95 latency | 261 ms | 530 ms | +269 ms |

Paired case comparison: 6 improved, 28 tied, and 2 regressed.

Reranked segment results:

- General knowledge: 12 cases, MRR 0.9583, Hit@5 100%.
- School admission guides: 24 cases, MRR 0.7757, Hit@5 91.67%.
- Metadata Filter Accuracy: 100% over 24 evaluated school cases.

MRR is the mean reciprocal rank of the first reviewed relevant chunk. Hit@K is
the proportion of positive cases with a reviewed relevant chunk in the first K
selected chunks. nDCG counts each exact relevance unit once and is normalized
to the range `[0, 1]`.

## Safety Set

- Cases: 12
- Dataset SHA-256: `692aa1c3e2ea4e34b476a1ff9d185e46ca4461d5818fb1f14ed3b6b3d1e6e5aa`
- Correct abstentions: 4/12
- No-answer Accuracy: 33.33%
- False Positive Rate: 66.67%
- Technical errors: 0

All four missing-corpus cases abstained correctly. The eight off-topic or
ambiguous cases still selected evidence, showing that the current retrieval
threshold is not an adequate query-scope or no-answer gate.

## LLM Answer Set

- Cases: 12
- Dataset SHA-256: `8b20b5b4450b8a88ca462f8d7d06f5344f59fb11112fa91b65d59dc0cf02ba23`
- Generated successfully: 12/12
- Fully judged: 12/12
- Judge technical errors: 0
- Strict two-judge consensus pass: 11/12, 91.67%
- Judge agreement: 91.67%
- Faithfulness mean: 2.000/2
- Answer relevance mean: 2.000/2
- Citation correctness mean: 1.917/2
- Abstention correctness mean: 2.000/2
- Per-case latency P50/P95: 13.652 s / 42.419 s

The only judge disagreement was an expected weather abstention. One judge
accepted the unsupported-question refusal without a citation; the other
required a citation even though no evidence should be cited.

The generator and both judges used the same configured model. These are model
judgments, not human labels, and should be used for regression screening rather
than presented as an absolute answer-quality score.

## Evidence Files

- Retrieval JSON: `ruoyi-admin/target/rag-eval/20260723T114545.512Z-frozen-v3.json`
- Retrieval Markdown: `ruoyi-admin/target/rag-eval/20260723T114545.512Z-frozen-v3.md`
- Answer JSON: `ruoyi-admin/target/rag-eval/20260723T114921.164Z-answer-v1.json`
- Answer Markdown: `ruoyi-admin/target/rag-eval/20260723T114921.164Z-answer-v1.md`

The JSON reports contain per-case queries, ranks, selected evidence, latency,
technical errors, generated answers, and both raw judge outputs.

## Resume Boundary

The deterministic frozen retrieval result is suitable for a resume when the
sample size and local frozen-test environment are stated. The LLM answer score
is better reserved for interview discussion until the judge is separated from
the generator or a human-reviewed subset is available. The current no-answer
result is a diagnosed weakness and should not be promoted as a quality claim.
