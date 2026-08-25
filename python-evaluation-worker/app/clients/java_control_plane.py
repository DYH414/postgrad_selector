"""
Java 控制面 HTTP 客户端（§4 端点3: agent-invocations, 端点4: result 回调）。

网络重试策略：
- invoke_agent: 最多 2 次重试，超时 → agent_status=timeout，5xx → http_error
- post_case_result: 最多 3 次重试，带 Idempotency-Key
"""

from __future__ import annotations

import json
from dataclasses import dataclass

import httpx


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
    调用 Java 内部端点（/internal/v1/evaluation/...）。
    token 通过 X-Evaluation-Token header 传递。
    """

    def __init__(self, base_url: str, token: str, timeout_seconds: float = 150.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeout_seconds = timeout_seconds

    def _headers(self) -> dict:
        return {"X-Evaluation-Token": self.token, "Content-Type": "application/json"}

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
        内联返回 answer + route + 三类 trace。
        网络重试 2 次；超时→timeout，5xx→http_error。
        """
        payload = {
            "evaluationRunId": evaluation_run_id,
            "caseId": case_id,
            "subjectUserId": subject_user_id,
            "agentConfigVersionId": agent_config_version_id,
            "query": query,
            "businessPromptText": business_prompt_text,
        }
        url = f"{self.base_url}/internal/v1/evaluation/agent-invocations"
        last_exc: Exception | None = None
        for attempt in range(3):  # 最多 2 次重试
            try:
                async with httpx.AsyncClient(
                    timeout=self.timeout_seconds, trust_env=False
                ) as client:
                    resp = await client.post(url, json=payload, headers=self._headers())
                    resp.raise_for_status()
                    data = resp.json()
                    agent = data.get("agentRun", {})
                    return AgentInvocationResponse(
                        agent_run_id=agent.get("runId") or "",
                        status=agent.get("status") or "FAILED",
                        route=agent.get("route"),
                        route_reason=agent.get("routeReason"),
                        answer=agent.get("answer"),
                        duration_ms=agent.get("durationMs") or 0,
                        draft_changed=agent.get("draftChanged") or False,
                        error_code=agent.get("errorCode"),
                        error_summary=agent.get("errorSummary"),
                        llm_traces=data.get("llmTraces") or [],
                        tool_traces=data.get("toolTraces") or [],
                        rag_traces=data.get("ragTraces") or [],
                    )
            except httpx.TimeoutException as exc:
                last_exc = exc
                if attempt == 2:
                    raise _wrap_error("timeout", exc)
            except httpx.HTTPStatusError as exc:
                if 400 <= exc.response.status_code < 500:
                    raise _wrap_error("http_error", exc)
                last_exc = exc
                if attempt == 2:
                    raise _wrap_error("http_error", exc)
            except Exception as exc:
                last_exc = exc
                if attempt == 2:
                    raise _wrap_error("http_error", exc)
        raise _wrap_error("http_error", last_exc)  # unreachable

    async def post_case_result(
        self,
        run_id: str,
        case_id: int,
        result: dict,
    ) -> str:
        """
        PUT /internal/v1/evaluation/runs/{runId}/cases/{caseId}/result（§4 端点4）。
        Idempotency-Key 保证幂等；同 checksum → 200 REPLAYED，异 checksum → 409。
        返回 "SAVED" | "REPLAYED"。
        """
        url = f"{self.base_url}/internal/v1/evaluation/runs/{run_id}/cases/{case_id}/result"
        idempotency_key = f"result:{run_id}:{case_id}"
        headers = {**self._headers(), "Idempotency-Key": idempotency_key}
        payload = _to_java_case_result(result)
        last_exc: Exception | None = None
        for attempt in range(4):  # 最多 3 次重试
            try:
                async with httpx.AsyncClient(
                    timeout=self.timeout_seconds, trust_env=False
                ) as client:
                    resp = await client.put(url, json=payload, headers=headers)
                    if resp.status_code == 409:
                        return "CONFLICT"
                    resp.raise_for_status()
                    data = resp.json()
                    return data.get("outcome", "SAVED")
            except httpx.HTTPStatusError as exc:
                if exc.response.status_code == 409:
                    return "CONFLICT"
                if 400 <= exc.response.status_code < 500:
                    raise
                last_exc = exc
                if attempt == 3:
                    raise
            except Exception as exc:
                last_exc = exc
                if attempt == 3:
                    raise
        raise RuntimeError(f"post_case_result failed: {last_exc}")  # unreachable

    async def fail_run(
        self,
        run_id: str,
        error_code: str,
        error_summary: str,
    ) -> str:
        """Report a terminal worker failure so Java never leaves a run RUNNING."""
        url = f"{self.base_url}/internal/v1/evaluation/runs/{run_id}/failure"
        payload = {
            "errorCode": error_code,
            "errorSummary": (error_summary or error_code)[:500],
        }
        last_exc: Exception | None = None
        for attempt in range(3):
            try:
                async with httpx.AsyncClient(
                    timeout=self.timeout_seconds, trust_env=False
                ) as client:
                    resp = await client.put(url, json=payload, headers=self._headers())
                    resp.raise_for_status()
                    return resp.json().get("outcome", "FAILED")
            except Exception as exc:  # noqa: BLE001
                last_exc = exc
                if attempt == 2:
                    raise RuntimeError(f"fail_run callback failed: {exc}") from exc
        raise RuntimeError(f"fail_run callback failed: {last_exc}")


def _wrap_error(kind: str, cause: Exception | None) -> RuntimeError:
    return RuntimeError(f"agent_invocation_{kind}: {cause}")


def _to_java_case_result(result: dict) -> dict:
    """Translate the Python graph result into the Java callback DTO."""
    scalar_fields = {
        "agent_run_id": "agentRunId",
        "passed": "passed",
        "agent_success": "agentSuccess",
        "route_accuracy": "routeAccuracy",
        "required_tool_hit": "requiredToolHit",
        "forbidden_tool_called": "forbiddenToolCalled",
        "tool_success_rate": "toolSuccessRate",
        "citation_present": "citationPresent",
        "expected_abstention": "expectedAbstention",
        "trace_complete": "traceComplete",
        "faithfulness": "faithfulness",
        "answer_relevance": "answerRelevance",
        "citation_correctness": "citationCorrectness",
        "judge_variance": "judgeVariance",
        "judge_passed": "judgePassed",
        "latency_ms": "latencyMs",
        "input_tokens": "inputTokens",
        "output_tokens": "outputTokens",
        "result_checksum": "resultChecksum",
        "technical_error_code": "technicalErrorCode",
        "technical_error_summary": "technicalErrorSummary",
    }
    payload = {
        java_name: result.get(python_name)
        for python_name, java_name in scalar_fields.items()
    }
    payload["ruleResultJson"] = json.dumps(
        result.get("rule_result") or {}, ensure_ascii=False
    )
    payload["judgeResultJson"] = json.dumps(
        result.get("judge_result") or {}, ensure_ascii=False
    )
    payload["failureReasonsJson"] = json.dumps(
        result.get("failure_reasons") or [], ensure_ascii=False
    )
    return payload
