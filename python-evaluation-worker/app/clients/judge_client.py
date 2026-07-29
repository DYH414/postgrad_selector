"""
OpenAI 兼容 Judge 客户端（§6.2 temp=0 硬要求）。

单次调用 + 1次重试；超时/parse失败 → JudgeParseError 由调用方处理。
"""

from __future__ import annotations

import httpx

from app.judge.parser import JudgeOutput, JudgeParseError, parse_judge_output
from app.judge.prompt import JUDGE_SYSTEM_PROMPT, build_judge_user_message


class JudgeClient:
    """
    调用 OpenAI 兼容 Judge 端点，temp=0（§6.2 硬约束）。
    单次调用超时/invalid → JudgeParseError，由judge_node处理重试。
    """

    def __init__(
        self,
        base_url: str,
        api_key: str,
        model: str,
        timeout_seconds: float = 30.0,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._model = model
        self._timeout = timeout_seconds

    async def judge(
        self,
        query: str,
        answer: str,
        evidence: list[dict],
        expected_claims: list[str],
        forbidden_claims: list[str],
    ) -> JudgeOutput:
        """
        单次 Judge 调用（temp=0 不可更改）。
        抛 JudgeParseError（parse失败）或 httpx.HTTPError（网络错误）。
        调用方负责重试。
        """
        user_message = build_judge_user_message(
            query=query,
            answer=answer,
            evidence=evidence,
            expected_claims=expected_claims,
            forbidden_claims=forbidden_claims,
        )
        payload = {
            "model": self._model,
            "temperature": 0,           # § 6.2 硬约束：temp=0
            # SiliconFlow Qwen models otherwise spend the bounded output budget
            # on reasoning_content and may return an empty JSON content field.
            "enable_thinking": False,
            "max_tokens": 512,
            "messages": [
                {"role": "system", "content": JUDGE_SYSTEM_PROMPT},
                {"role": "user", "content": user_message},
            ],
        }
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            response = await client.post(
                f"{self._base_url}/chat/completions",
                json=payload,
                headers={"Authorization": f"Bearer {self._api_key}"},
            )
            response.raise_for_status()
            data = response.json()

        raw = data["choices"][0]["message"]["content"]
        return parse_judge_output(raw)
