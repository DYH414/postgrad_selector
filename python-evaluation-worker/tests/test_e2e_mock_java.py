"""
Step 10 验证: mock Java E2E + 同 checksum V1/V2 delta 正确（§9 步骤10）。
"""

import pytest
from unittest.mock import AsyncMock, MagicMock

from langgraph.checkpoint.memory import MemorySaver

from app.clients.java_control_plane import AgentInvocationResponse, JavaControlPlaneClient
from app.clients.judge_client import JudgeClient
from app.graph.main_graph import build_main_graph
from app.graph.state import RunState


def _make_java_mock() -> tuple[MagicMock, MagicMock]:
    """
    V1 返回错误路由（KNOWLEDGE_RAG）— 必失败。
    V2 返回正确路由（PROGRAM_FACTS）— 规则通过，交给 judge。
    """
    mock_java = AsyncMock(spec=JavaControlPlaneClient)

    async def _invoke(evaluation_run_id, case_id, subject_user_id,
                      agent_config_version_id, query, business_prompt_text):
        if agent_config_version_id == 1:
            # V1: 错误路由 → route_accuracy=False → not passed（规则直接拦）
            return AgentInvocationResponse(
                agent_run_id=f"v1-run-{case_id}",
                status="SUCCEEDED",
                route="KNOWLEDGE_RAG",          # 错，期望 PROGRAM_FACTS
                route_reason="v1 route reason",
                answer="V1 answer text",
                duration_ms=400,
                draft_changed=False,
                error_code=None, error_summary=None,
                llm_traces=[{"type": "llm", "status": "SUCCEEDED"}],
                tool_traces=[], rag_traces=[],
            )
        else:
            # V2: 正确路由 → route_accuracy=True → 进 judge
            return AgentInvocationResponse(
                agent_run_id=f"v2-run-{case_id}",
                status="SUCCEEDED",
                route="PROGRAM_FACTS",           # 正确
                route_reason="v2 route reason",
                answer="深圳大学2025年录取345分（V2正确答案）",
                duration_ms=350,
                draft_changed=False,
                error_code=None, error_summary=None,
                llm_traces=[{"type": "llm", "status": "SUCCEEDED"}],
                tool_traces=[], rag_traces=[],
            )

    mock_java.invoke_agent.side_effect = _invoke
    mock_java.post_case_result.return_value = "SAVED"
    return mock_java


def _make_judge_mock_passing() -> MagicMock:
    """Judge 固定返回通过（三项均 0.9，无 unsupported claims）。"""
    from app.judge.parser import JudgeOutput
    mock_judge = AsyncMock(spec=JudgeClient)
    mock_judge.judge.return_value = JudgeOutput(
        faithfulness=0.9,
        answer_relevance=0.9,
        citation_correctness=None,
        passed=True,
        reasons=[],
        unsupported_claims=[],
    )
    return mock_judge


def _base_state(run_id: str = "run-e2e-001", n_cases: int = 2) -> RunState:
    return {
        "run_id": run_id,
        "dataset_checksum": "frozen-v3-checksum",
        "config_versions": [
            {"id": 1, "version_name": "v1", "business_prompt_text": "v1 prompt"},
            {"id": 2, "version_name": "v2", "business_prompt_text": "v2 prompt"},
        ],
        "cases": [
            {"id": i + 1, "case_key": f"c{i+1}",
             "query": f"q{i+1}?", "expected_route": "PROGRAM_FACTS"}
            for i in range(n_cases)
        ],
        "combos": [],
        "case_results": [],
        "aggregates": {},
        "comparison": {},
    }


# ── E2E: V1 失败 / V2 通过 ────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_e2e_v1_fails_v2_passes_with_mock_java():
    """
    V1 路由错 → 全部不通过；V2 路由对 → judge 通过 → 全部通过。
    compare delta: pass_rate 从 0 → 1，状态 IMPROVED。
    """
    graph = build_main_graph(checkpointer=MemorySaver())
    initial = _base_state(run_id="run-v1v2-001", n_cases=2)
    cfg = {
        "configurable": {
            "thread_id": "run-v1v2-001",
            "java_client": _make_java_mock(),
            "judge_client": _make_judge_mock_passing(),
        },
        "max_concurrency": 2,
    }
    result = await graph.ainvoke(initial, config=cfg)

    # 2 versions × 2 cases = 4 results
    assert len(result["case_results"]) == 4

    by_v = result["aggregates"]["by_version"]
    assert by_v["1"]["pass_rate"] == pytest.approx(0.0), f"V1 should fail: {by_v['1']}"
    assert by_v["2"]["pass_rate"] == pytest.approx(1.0), f"V2 should pass: {by_v['2']}"

    comp = result["comparison"]
    assert "1->2" in comp
    delta = comp["1->2"]["pass_rate"]["delta"]
    assert delta == pytest.approx(1.0), f"Expected delta=1.0, got {delta}"
    assert comp["1->2"]["pass_rate"]["status"] == "IMPROVED"


@pytest.mark.asyncio
async def test_e2e_same_checksum_compare_valid():
    """同 dataset_checksum 下，对比结果有 delta 字段（§5.1 仅同 checksum 算 delta）。"""
    graph = build_main_graph(checkpointer=MemorySaver())
    initial = _base_state(run_id="run-checksum-001", n_cases=1)
    cfg = {
        "configurable": {
            "thread_id": "run-checksum-001",
            "java_client": _make_java_mock(),
            "judge_client": _make_judge_mock_passing(),
        },
        "max_concurrency": 2,
    }
    result = await graph.ainvoke(initial, config=cfg)
    comp = result["comparison"]
    # 有对比结果，且含 pass_rate delta
    assert "1->2" in comp
    assert "pass_rate" in comp["1->2"]


@pytest.mark.asyncio
async def test_e2e_all_pass_no_regression():
    """两个版本都通过（同一 mock）→ delta=0，状态 UNCHANGED。"""
    # Both V1 and V2 return correct route
    mock_java = AsyncMock(spec=JavaControlPlaneClient)

    async def _both_pass(evaluation_run_id, case_id, subject_user_id,
                         agent_config_version_id, query, business_prompt_text):
        return AgentInvocationResponse(
            agent_run_id=f"run-{agent_config_version_id}-{case_id}",
            status="SUCCEEDED",
            route="PROGRAM_FACTS",   # 正确
            route_reason="good route",
            answer="good answer",
            duration_ms=300,
            draft_changed=False,
            error_code=None, error_summary=None,
            llm_traces=[{"type": "llm", "status": "SUCCEEDED"}],
            tool_traces=[], rag_traces=[],
        )

    mock_java.invoke_agent.side_effect = _both_pass
    mock_java.post_case_result.return_value = "SAVED"

    graph = build_main_graph(checkpointer=MemorySaver())
    initial = _base_state(run_id="run-both-pass-001", n_cases=1)
    cfg = {
        "configurable": {
            "thread_id": "run-both-pass-001",
            "java_client": mock_java,
            "judge_client": _make_judge_mock_passing(),
        },
        "max_concurrency": 2,
    }
    result = await graph.ainvoke(initial, config=cfg)
    comp = result["comparison"]
    assert comp["1->2"]["pass_rate"]["status"] == "UNCHANGED"
    assert comp["1->2"]["pass_rate"]["delta"] == pytest.approx(0.0)


@pytest.mark.asyncio
async def test_e2e_agent_failure_does_not_crash_run():
    """agent 返回 FAILED（http_error 归一化）→ case 不通过，但 run 完成（§5.5 铁律）。"""
    mock_java = AsyncMock(spec=JavaControlPlaneClient)
    mock_java.invoke_agent.side_effect = RuntimeError("simulated_http_error")
    mock_java.post_case_result.return_value = "SAVED"

    graph = build_main_graph(checkpointer=MemorySaver())
    initial = _base_state(run_id="run-fail-001", n_cases=1)
    # Only 1 version to simplify
    initial["config_versions"] = [{"id": 1, "version_name": "v1", "business_prompt_text": "v1"}]
    cfg = {
        "configurable": {
            "thread_id": "run-fail-001",
            "java_client": mock_java,
            "judge_client": _make_judge_mock_passing(),
        },
        "max_concurrency": 1,
    }
    result = await graph.ainvoke(initial, config=cfg)
    # run completes (iron rule §5.5), case marked as not passed
    assert len(result["case_results"]) == 1
    assert result["case_results"][0]["passed"] is False
    assert result["aggregates"]["pass_rate"] == pytest.approx(0.0)


# ── Java 客户端单元测试（mock httpx）─────────────────────────────────────────

@pytest.mark.asyncio
async def test_java_client_invoke_agent_success():
    """verify client parses Java response correctly."""
    import respx
    import httpx

    mock_response = {
        "agentRun": {
            "runId": "agent-run-1",
            "status": "SUCCEEDED",
            "route": "PROGRAM_FACTS",
            "routeReason": "matched program facts query",
            "answer": "test answer",
            "durationMs": 300,
            "draftChanged": False,
            "errorCode": None,
            "errorSummary": None,
        },
        "llmTraces": [{"type": "llm"}],
        "toolTraces": [],
        "ragTraces": [],
    }

    with respx.mock:
        respx.post("http://localhost:8080/internal/v1/evaluation/agent-invocations").mock(
            return_value=httpx.Response(200, json=mock_response)
        )
        client = JavaControlPlaneClient(
            base_url="http://localhost:8080",
            token="test-token",
        )
        resp = await client.invoke_agent(
            evaluation_run_id="run-1",
            case_id=1,
            subject_user_id=1,
            agent_config_version_id=1,
            query="test query",
            business_prompt_text="prompt",
        )
    assert resp.agent_run_id == "agent-run-1"
    assert resp.route == "PROGRAM_FACTS"
    assert resp.answer == "test answer"
    assert len(resp.llm_traces) == 1
