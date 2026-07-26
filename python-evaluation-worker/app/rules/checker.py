"""
确定性规则引擎（§6.1）— 纯函数，不依赖外部服务。

所有指标从内联 trace（invoke_agent 返回）计算，Python 不回调 Java 查。
Java 内部 trace DTO 按 occurredAt 升序返回（§6.1 note）。
"""

from __future__ import annotations

_ABSTENTION_PHRASES = frozenset(
    {"无法确认", "暂无可靠数据", "信息不足", "无法提供", "不确定", "暂无相关数据"}
)


def check_rules(
    case: dict,
    agent_response: dict,
    trace: dict,
) -> dict:
    """
    计算全部 §6.1 指标，返回 rule_result dict。

    Args:
        case: DispatchCase dict（包含 expected_route / agent{}）
        agent_response: invoke_agent 返回的 response dict
        trace: invoke_agent 返回的 trace dict
            {agent_run_id, llm: [...], tool: [...], rag: [...]}
    """
    agent_spec = case.get("agent") or {}
    answer: str | None = agent_response.get("answer")
    observed_route: str | None = agent_response.get("route")
    expected_route: str | None = case.get("expected_route")
    agent_status: str = agent_response.get("status", "ok")

    llm_traces: list = trace.get("llm") or []
    tool_traces: list = trace.get("tool") or []

    # ── agentSuccess ──────────────────────────────────────────────────────────
    agent_success = agent_response.get("status", "ok") == "ok" and not agent_response.get(
        "error_code"
    )

    # ── routeAccuracy ─────────────────────────────────────────────────────────
    route_accuracy: bool | None
    if expected_route:
        route_accuracy = observed_route == expected_route
    else:
        route_accuracy = None

    # ── requiredToolHit ───────────────────────────────────────────────────────
    required_tools: list[str] = agent_spec.get("required_tools") or []
    if required_tools:
        succeeded_tools = {
            t.get("tool_name") or t.get("toolName")
            for t in tool_traces
            if (t.get("status") or t.get("result_status")) in ("SUCCESS", "SUCCEEDED", "OK")
        }
        required_tool_hit = all(rt in succeeded_tools for rt in required_tools)
    else:
        required_tool_hit = None

    # ── forbiddenToolCalled ───────────────────────────────────────────────────
    forbidden_tools: list[str] = agent_spec.get("forbidden_tools") or []
    if forbidden_tools:
        called_tools = {t.get("tool_name") or t.get("toolName") for t in tool_traces}
        forbidden_tool_called = bool(called_tools & set(forbidden_tools))
    else:
        forbidden_tool_called = False

    # ── toolSuccessRate ───────────────────────────────────────────────────────
    if tool_traces:
        success_count = sum(
            1
            for t in tool_traces
            if (t.get("status") or t.get("result_status")) in ("SUCCESS", "SUCCEEDED", "OK")
        )
        tool_success_rate: float | None = success_count / len(tool_traces)
    else:
        tool_success_rate = 1.0  # 无 Tool 调用记 1.0

    # ── citationPresent ───────────────────────────────────────────────────────
    reference_evidence: list[dict] = agent_spec.get("reference_evidence") or []
    if reference_evidence and answer:
        # 答案须含任一 evidence 的全部 citationTokens
        citation_present = any(
            all(token in answer for token in (ev.get("citation_tokens") or []))
            for ev in reference_evidence
            if ev.get("citation_tokens")
        )
    elif reference_evidence and not answer:
        citation_present = False
    else:
        citation_present = None  # referenceEvidence 为空 → 不进聚合分母

    # ── expectedAbstention ────────────────────────────────────────────────────
    if agent_spec.get("expected_abstention") and answer is not None:
        expected_abstention: bool | None = any(
            phrase in answer for phrase in _ABSTENTION_PHRASES
        )
    elif agent_spec.get("expected_abstention") is True and answer is None:
        expected_abstention = False
    else:
        expected_abstention = None

    # ── traceComplete ─────────────────────────────────────────────────────────
    # 有主记录 + route + 终态 + ≥1 LLM trace；有 Tool 则每条 Tool trace 有终态
    has_main_record = bool(trace.get("agent_run_id"))
    has_route = observed_route is not None
    has_terminal = agent_response.get("status") in (
        "ok", "SUCCEEDED", "FAILED", "UNSUPPORTED_SIDE_EFFECT", "TIMED_OUT"
    )
    has_llm = len(llm_traces) >= 1
    tool_traces_complete = all(
        (t.get("status") or t.get("result_status")) is not None for t in tool_traces
    )
    trace_complete = has_main_record and has_route and has_terminal and has_llm and tool_traces_complete

    return {
        "agent_success": agent_success,
        "route_accuracy": route_accuracy,
        "required_tool_hit": required_tool_hit,
        "forbidden_tool_called": forbidden_tool_called,
        "tool_success_rate": tool_success_rate,
        "citation_present": citation_present,
        "expected_abstention": expected_abstention,
        "trace_complete": trace_complete,
    }
