"""
共享 Judge-spec 定义（§2 阶段治理：阶段1起共享一份定义，防漂移）。

硬约束（§6.2）：
- temp=0（不可更改，否则重试时分数漂移毁幂等checksum）
- passed=true 唯一规则：三项 ≥0.80 且 unsupportedClaims=[]
- 只判 faithfulness/answerRelevance/citationCorrectness/passed/reasons/unsupportedClaims
- 不判 Route/Tool 对错（那是规则引擎的职责）
"""

from __future__ import annotations

# ── Judge 系统提示（共享定义，禁止随意修改）──────────────────────────────────────

JUDGE_SYSTEM_PROMPT = """\
You are a strict factual evaluator for a graduate school selection assistant.
Evaluate the assistant's answer ONLY on faithfulness, relevance, and citation correctness.
Do NOT evaluate whether the routing decision or tool selection was correct — that is checked separately.

Scoring rules (all scores must be in [0.0, 1.0]):
- faithfulness: Does every factual claim in the answer have direct support in the provided evidence?
  - 1.0 = all claims supported; 0.0 = claims contradict or lack evidence
- answerRelevance: Does the answer directly address the user's question without unnecessary content?
  - 1.0 = fully relevant; 0.0 = completely off-topic
- citationCorrectness: If reference evidence was required, are the cited facts accurate and traceable?
  - 1.0 = all citations accurate; 0.0 = citations missing/wrong; null if no reference evidence provided

passed = true if and only if: faithfulness >= 0.80 AND answerRelevance >= 0.80 AND
         (citationCorrectness is null OR citationCorrectness >= 0.80) AND unsupportedClaims == []

Output ONLY valid JSON matching this schema exactly (no extra fields, no markdown):
{"faithfulness": <float>, "answerRelevance": <float>, "citationCorrectness": <float | null>,
 "passed": <bool>, "reasons": [<str>, ...], "unsupportedClaims": [<str>, ...]}
"""

JUDGE_USER_TEMPLATE = """\
## User question
{query}

## Reference evidence (retrieved chunks, may be empty)
{evidence}

## Assistant's answer
{answer}

## Required claims (natural language, check presence)
{expected_claims}

## Forbidden claims (natural language, must not appear)
{forbidden_claims}

Evaluate the answer above. Output only the JSON object.
"""


def build_judge_user_message(
    query: str,
    answer: str,
    evidence: list[dict],
    expected_claims: list[str],
    forbidden_claims: list[str],
) -> str:
    """格式化 judge 用户消息（同时用于 Java 和 Python——防漂移约束）。"""
    evidence_text = "\n".join(
        f"[{i+1}] {e.get('content', e.get('text', str(e)))}"
        for i, e in enumerate(evidence)
    ) or "(no evidence retrieved)"
    return JUDGE_USER_TEMPLATE.format(
        query=query,
        evidence=evidence_text,
        answer=answer or "(no answer)",
        expected_claims="\n".join(f"- {c}" for c in expected_claims) or "(none)",
        forbidden_claims="\n".join(f"- {c}" for c in forbidden_claims) or "(none)",
    )
