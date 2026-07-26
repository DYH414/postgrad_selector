"""
Strict Pydantic parse for Judge output（§6.2）。

解析失败或越界 → JudgeParseError；调用方重试 1 次，再失败写 JUDGE_INVALID_OUTPUT。
"""

from __future__ import annotations

import json
import re

from pydantic import BaseModel, Field, field_validator, model_validator


class JudgeOutput(BaseModel):
    """Judge 严格输出 schema — 拒额外字段/缺字段/越界（§6.2 Pydantic strict）。"""

    model_config = {"extra": "forbid", "populate_by_name": True}

    faithfulness: float = Field(ge=0.0, le=1.0)
    answer_relevance: float = Field(ge=0.0, le=1.0, alias="answerRelevance")
    citation_correctness: float | None = Field(default=None, alias="citationCorrectness")
    passed: bool
    reasons: list[str]
    unsupported_claims: list[str] = Field(alias="unsupportedClaims")

    @field_validator("citation_correctness", mode="before")
    @classmethod
    def validate_citation(cls, v: object) -> float | None:
        if v is None:
            return None
        val = float(v)
        if not (0.0 <= val <= 1.0):
            raise ValueError(f"citationCorrectness {val} out of [0,1]")
        return val

    @model_validator(mode="after")
    def validate_passed_consistency(self) -> "JudgeOutput":
        """passed=true 的唯一规则：三项≥0.80 且 unsupportedClaims=[]（§6.2）。"""
        if self.passed:
            if self.faithfulness < 0.80 or self.answer_relevance < 0.80:
                raise ValueError(
                    f"passed=true but faithfulness={self.faithfulness} or "
                    f"answerRelevance={self.answer_relevance} < 0.80"
                )
            if self.citation_correctness is not None and self.citation_correctness < 0.80:
                raise ValueError(
                    f"passed=true but citationCorrectness={self.citation_correctness} < 0.80"
                )
            if self.unsupported_claims:
                raise ValueError(
                    f"passed=true but unsupportedClaims={self.unsupported_claims} non-empty"
                )
        return self


class JudgeParseError(Exception):
    """Judge 输出解析失败（格式/越界/一致性检查不通过）。"""

    def __init__(self, reason: str, raw: str) -> None:
        super().__init__(reason)
        self.raw = raw


_JSON_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)\s*```", re.DOTALL)


def parse_judge_output(raw: str) -> JudgeOutput:
    """
    从 LLM 原始输出中提取并严格解析 JudgeOutput。

    - 先尝试直接 JSON parse
    - 若失败，尝试提取第一个 ``` ... ``` 代码块
    - 解析失败或 Pydantic 校验不通过 → JudgeParseError
    """
    text = raw.strip()

    # 优先尝试直接 parse
    candidate = text
    if not candidate.startswith("{"):
        match = _JSON_FENCE_RE.search(text)
        if match:
            candidate = match.group(1).strip()
        else:
            # 找第一个 { ... }
            start = text.find("{")
            end = text.rfind("}")
            if start != -1 and end != -1:
                candidate = text[start : end + 1]
            else:
                raise JudgeParseError("no JSON object found in output", raw)

    try:
        data = json.loads(candidate)
    except json.JSONDecodeError as exc:
        raise JudgeParseError(f"JSON decode error: {exc}", raw) from exc

    try:
        return JudgeOutput.model_validate(data)
    except Exception as exc:
        raise JudgeParseError(f"Pydantic validation error: {exc}", raw) from exc
