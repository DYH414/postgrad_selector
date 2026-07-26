"""
Java 控制面 HTTP 客户端（§4 端点3: agent-invocations, 端点4: result 回调）。

Step 8 为桩实现——返回固定假数据，Step 10 替换为真实 httpx 调用。
所有方法签名与真实实现保持一致，方便 mock 替换。
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass
class AgentInvocationResponse:
    agent_run_id: str
    status: str          # SUCCEEDED | FAILED | UNSUPPORTED_SIDE_EFFECT | TIMED_OUT
    route: str | None
    route_reason: str | None
    answer: str | None
    duration_ms: int
    draft_changed: bool
    error_code: str | None
    error_summary: str | None
    llm_traces: list
    tool_traces: list
    rag_traces: list


class JavaControlPlaneClient:
    """
    调用 Java 内部端点。
    TODO step 10: 替换桩实现为真实 httpx 调用。
    """

    def __init__(self, base_url: str, token: str, timeout_seconds: float = 30.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeout_seconds = timeout_seconds

    async def invoke_agent(
        self,
        evaluation_run_id: str,
        case_id: int,
        subject_user_id: int,
        agent_config_version_id: int,
        query: str,
        business_prompt_text: str,
    ) -> AgentInvocationResponse:
        """
        POST /internal/v1/evaluation/agent-invocations（§4 端点3）。
        返回 answer + route + 内联三类 trace。
        """
        # TODO step 10: real httpx POST with retry (2 times), timeout handling
        return AgentInvocationResponse(
            agent_run_id=f"stub-agent-run-{case_id}",
            status="SUCCEEDED",
            route="PROGRAM_FACTS",
            route_reason="stub",
            answer="深圳大学计算机专硕2025年录取分数约345分。（桩数据）",
            duration_ms=500,
            draft_changed=False,
            error_code=None,
            error_summary=None,
            llm_traces=[],
            tool_traces=[],
            rag_traces=[],
        )

    async def post_case_result(
        self,
        run_id: str,
        case_id: int,
        result: dict,
    ) -> str:
        """
        PUT /internal/v1/evaluation/runs/{runId}/cases/{caseId}/result（§4 端点4）。
        返回 "SAVED" | "REPLAYED"。
        """
        # TODO step 10: real httpx PUT with idempotency key + retry
        return "SAVED"
