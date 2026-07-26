"""
case 子图（§5.2）— 独立 schema，父图不见私有态。

节点链路：
  invoke_agent → rule_check ─┬─(ok&有答案)→ judge_node → decide ─┬─(高方差)→ reflect → decide
                              └─(失败/无答案)→ finalize             ├─(边界/冲突)→ human_review → finalize
                                                                    └─(明确)→ finalize

三条铁律（§5.5）：
1. 错误即数据：invoke_agent 归一化所有异常为 agent_status，不抛
2. finalize 必定产出带 status 的 final_result，绝不让 case 消失
3. 每个 case 独立 state，不共享可变量
"""

from __future__ import annotations

import asyncio
import hashlib
import json
from typing import Literal

from langgraph.graph import StateGraph, END
from langgraph.types import interrupt

from app.clients.java_control_plane import JavaControlPlaneClient
from app.graph.state import CaseState

# 方差阈值: faithfulness 极差 > 0.34 触发 reflect（§5.3）
_VARIANCE_THRESHOLD = 0.34
# reflect 最大次数（§5.3 只 1 次）
_MAX_REFLECT = 1


# ── 节点实现 ──────────────────────────────────────────────────────────────────

async def invoke_agent(state: CaseState, config: dict | None = None) -> dict:
    """
    调 Java /internal/v1/evaluation/agent-invocations（§4 端点3）。
    网络重试 2 次；超时/5xx → agent_status=timeout/http_error，跳 finalize。
    错误即数据：不抛异常，归一化为 agent_status 字段。
    """
    cfg = (config or {}).get("configurable", {})
    java_client: JavaControlPlaneClient | None = cfg.get("java_client")
    case = state["case"]

    if java_client is None:
        # 测试桩：无 client 时返回占位响应
        return {
            "agent_status": "ok",
            "agent_response": {"answer": "stub answer", "route": case.get("expected_route", "UNKNOWN")},
            "trace": {},
        }

    try:
        resp = await java_client.invoke_agent(
            evaluation_run_id=state["run_id"],
            case_id=case["id"],
            subject_user_id=cfg.get("subject_user_id", 1),
            agent_config_version_id=state["config_version_id"],
            query=case["query"],
            business_prompt_text=cfg.get("business_prompt_text", ""),
        )
        status = "ok" if resp.status == "SUCCEEDED" else resp.status.lower()
        return {
            "agent_status": status,
            "agent_response": {
                "answer": resp.answer,
                "route": resp.route,
                "route_reason": resp.route_reason,
                "duration_ms": resp.duration_ms,
                "draft_changed": resp.draft_changed,
                "error_code": resp.error_code,
                "error_summary": resp.error_summary,
            },
            "trace": {
                "agent_run_id": resp.agent_run_id,
                "llm": resp.llm_traces,
                "tool": resp.tool_traces,
                "rag": resp.rag_traces,
            },
        }
    except Exception as exc:  # noqa: BLE001
        return {
            "agent_status": "http_error",
            "agent_response": {},
            "trace": {},
            "failure_reasons": state.get("failure_reasons", []) + [f"invoke_agent error: {exc}"],
        }


def rule_check(state: CaseState) -> dict:
    """
    确定性规则（§6.1）—— 纯函数，不依赖外部服务。
    Step 9 补全全部指标；此处先算 routeAccuracy 和 agentSuccess。
    """
    case = state["case"]
    agent_spec = case.get("agent") or {}
    resp = state.get("agent_response", {})
    trace = state.get("trace", {})

    agent_success = state.get("agent_status") == "ok"
    observed_route = resp.get("route")
    expected_route = case.get("expected_route")
    route_accuracy = (observed_route == expected_route) if expected_route else None

    rule = {
        "agent_success": agent_success,
        "route_accuracy": route_accuracy,
        "required_tool_hit": None,       # step 9 填充
        "forbidden_tool_called": None,   # step 9 填充
        "tool_success_rate": None,       # step 9 填充
        "citation_present": None,        # step 9 填充
        "expected_abstention": None,     # step 9 填充
        "trace_complete": bool(trace),
    }
    return {"rule_result": rule}


async def judge_node(state: CaseState) -> dict:
    """
    LLM-as-judge，3 次并发采样取多数投票（§6.2）。
    @task 等价于 asyncio.gather（单节点内并发，不扇出图节点）。
    temp=0 是硬要求（§6.2 note）；桩实现返回固定结果。
    Step 9 替换为真实 Judge API 调用。
    """

    async def _single_sample(sample_idx: int) -> dict:
        # TODO step 9: 调用 judge_client.py，temp=0，strict Pydantic parse
        return {
            "faithfulness": 0.9,
            "answer_relevance": 0.85,
            "citation_correctness": 0.8,
            "passed": True,
            "reasons": [],
            "unsupported_claims": [],
        }

    # 3 次并发采样（asyncio.gather = 单节点内 @task 并发，§5.0 三处官方纠正之二）
    samples = await asyncio.gather(*[_single_sample(i) for i in range(3)])

    # 多数投票
    passed_votes = sum(1 for s in samples if s.get("passed"))
    judge_passed = passed_votes >= 2

    # 方差：faithfulness 极差（max - min）
    scores = [s.get("faithfulness", 0.0) for s in samples]
    variance = max(scores) - min(scores) if scores else 0.0

    # 三项均值
    verdict = {
        "passed": judge_passed,
        "faithfulness": sum(s.get("faithfulness", 0) for s in samples) / len(samples),
        "answer_relevance": sum(s.get("answer_relevance", 0) for s in samples) / len(samples),
        "citation_correctness": sum(s.get("citation_correctness", 0) for s in samples) / len(samples),
        "variance": variance,
        "valid_samples": len(samples),
        "passed_votes": passed_votes,
    }
    return {"judge_samples": list(samples), "judge_verdict": verdict}


def decide(state: CaseState) -> Literal["reflect", "human_review", "finalize"]:
    """
    条件边：根据 judge_verdict 决定下一跳（§5.3）。
    - 高方差且未 reflect → reflect
    - 有效采样 < 2 / reflect 后仍高方差 / 规则与判定冲突 → human_review
    - 否则 → finalize（绝大多数正常 case 走这条）
    """
    verdict = state.get("judge_verdict", {})
    rule = state.get("rule_result", {})
    variance = verdict.get("variance", 0.0)
    valid = verdict.get("valid_samples", 0)
    judge_passed = verdict.get("passed", False)
    reflect_count = state.get("reflect_count", 0)

    # 有效采样不足 → 人工
    if valid < 2:
        return "human_review"

    # 规则与判定冲突 → 人工（§5.3 条件边定义）
    forbidden_called = rule.get("forbidden_tool_called")
    if forbidden_called and judge_passed:
        return "human_review"

    # 高方差且未 reflect → reflect
    if variance > _VARIANCE_THRESHOLD and reflect_count < _MAX_REFLECT:
        return "reflect"

    # reflect 后仍高方差 → 人工
    if variance > _VARIANCE_THRESHOLD and reflect_count >= _MAX_REFLECT:
        return "human_review"

    return "finalize"


async def reflect(state: CaseState) -> dict:
    """
    追加一次聚焦分歧的重判，回 decide（§5.2）。
    TODO step 9: 传入高方差 samples，用特化 prompt 重判。
    """
    # 桩：保持当前 verdict，仅降低 variance 使其通过（步骤9替换）
    verdict = dict(state.get("judge_verdict", {}))
    verdict["variance"] = 0.0
    return {
        "judge_verdict": verdict,
        "reflect_count": state.get("reflect_count", 0) + 1,
    }


def human_review(state: CaseState) -> dict:
    """
    挂起图，等待人工裁定（§5.6 interrupt() + Command(resume=...)）。
    resume 后节点从头重跑，interrupt 之前无副作用（幂等原则）。
    """
    decision = interrupt({
        "run_id": state["run_id"],
        "case_id": state["case"].get("id"),
        "judge_verdict": state.get("judge_verdict"),
        "rule_result": state.get("rule_result"),
        "agent_response": state.get("agent_response"),
        "reason": "需要人工裁定：方差过高或规则/判定冲突",
    })
    return {"human_decision": decision}


def finalize(state: CaseState) -> dict:
    """
    合成最终结果（§5.5 铁律：finalize 必定产出带 status 的 final_result）。

    passed = agentSuccess && routeAccuracy && requiredToolHit && !forbiddenToolCalled
             && traceComplete && judgePassed（§6.3）。
    citationPresent / expectedAbstention 仅在非 null 时并入。
    """
    rule = state.get("rule_result", {})
    verdict = state.get("judge_verdict", {})
    human_dec = state.get("human_decision")

    # 人工裁定优先
    if human_dec is not None:
        judge_passed = human_dec.get("passed", False)
        failure_reasons = state.get("failure_reasons", []) + (
            [] if judge_passed else [f"human_review: {human_dec.get('reason', '')}"]
        )
    else:
        judge_passed = verdict.get("passed", False)
        failure_reasons = list(state.get("failure_reasons", []))

    agent_status = state.get("agent_status", "")
    agent_success = rule.get("agent_success", False)
    route_accuracy = rule.get("route_accuracy")        # None = 无 expected_route
    required_tool_hit = rule.get("required_tool_hit")  # None = 无要求
    forbidden_called = rule.get("forbidden_tool_called", False) or False
    trace_complete = rule.get("trace_complete", False)
    citation_present = rule.get("citation_present")
    expected_abstention = rule.get("expected_abstention")

    # 核心 passed 逻辑（§6.3）
    passed = (
        agent_success
        and (route_accuracy is None or route_accuracy)
        and (required_tool_hit is None or required_tool_hit)
        and not forbidden_called
        and trace_complete
        and judge_passed
    )
    # 可选指标并入
    if citation_present is not None:
        passed = passed and citation_present
    if expected_abstention is not None:
        passed = passed and expected_abstention

    # 技术失败/UNSUPPORTED_SIDE_EFFECT 不通过
    if agent_status in ("http_error", "timeout", "invalid_output", "unsupported_side_effect"):
        passed = False
        if agent_status not in failure_reasons:
            failure_reasons.append(f"agent_status={agent_status}")

    final = {
        "run_id": state["run_id"],
        "case_id": state["case"].get("id"),
        "config_version_id": state.get("config_version_id"),
        "passed": passed,
        "agent_success": agent_success,
        "route_accuracy": route_accuracy,
        "required_tool_hit": required_tool_hit,
        "forbidden_tool_called": forbidden_called,
        "trace_complete": trace_complete,
        "faithfulness": verdict.get("faithfulness"),
        "answer_relevance": verdict.get("answer_relevance"),
        "citation_correctness": verdict.get("citation_correctness"),
        "judge_variance": verdict.get("variance"),
        "judge_passed": judge_passed,
        "rule_result": rule,
        "judge_result": verdict,
        "failure_reasons": failure_reasons,
        "latency_ms": state.get("agent_response", {}).get("duration_ms"),
        "agent_run_id": state.get("trace", {}).get("agent_run_id"),
        "result_checksum": _checksum(state),
        "status": "ok" if agent_status == "ok" else agent_status,
    }
    return {"final_result": final}


def _route_after_rule(state: CaseState) -> Literal["judge_node", "finalize"]:
    """rule_check 后：agentStatus==ok && 有答案 → judge；否则 → finalize。"""
    if state.get("agent_status") == "ok" and state.get("agent_response", {}).get("answer"):
        return "judge_node"
    return "finalize"


def _checksum(state: CaseState) -> str:
    """result_checksum: 基于关键字段的 SHA-256（§6.2 幂等要求）。"""
    key = json.dumps({
        "run_id": state["run_id"],
        "case_id": state["case"].get("id"),
        "config_version_id": state.get("config_version_id"),
        "verdict": state.get("judge_verdict", {}),
        "rule": state.get("rule_result", {}),
        "agent_status": state.get("agent_status"),
    }, sort_keys=True)
    return hashlib.sha256(key.encode()).hexdigest()


# ── 图构建 ─────────────────────────────────────────────────────────────────────

def build_case_graph() -> StateGraph:
    """编译并返回 case 子图（无 checkpointer——由主图的 checkpointer 管理）。"""
    builder = StateGraph(CaseState)

    builder.add_node("invoke_agent", invoke_agent)
    builder.add_node("rule_check", rule_check)
    builder.add_node("judge_node", judge_node)
    builder.add_node("decide", lambda s: {})  # 纯路由节点，不改 state
    builder.add_node("reflect", reflect)
    builder.add_node("human_review", human_review)
    builder.add_node("finalize", finalize)

    builder.set_entry_point("invoke_agent")
    builder.add_edge("invoke_agent", "rule_check")
    builder.add_conditional_edges("rule_check", _route_after_rule, ["judge_node", "finalize"])
    builder.add_edge("judge_node", "decide")
    builder.add_conditional_edges("decide", decide, ["reflect", "human_review", "finalize"])
    builder.add_edge("reflect", "decide")
    builder.add_edge("human_review", "finalize")
    builder.add_edge("finalize", END)

    return builder.compile()
