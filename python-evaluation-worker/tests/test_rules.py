"""
§6.1 确定性规则引擎测试。
"""

import pytest
from app.rules.checker import check_rules


def _case(expected_route="PROGRAM_FACTS", agent_spec=None) -> dict:
    base = {"id": 1, "case_key": "c1", "query": "test?", "expected_route": expected_route}
    if agent_spec:
        base["agent"] = agent_spec
    return base


def _resp(route="PROGRAM_FACTS", answer="test answer", status="ok") -> dict:
    return {"route": route, "answer": answer, "status": status}


def _trace(llm=None, tool=None, rag=None, agent_run_id="run-1") -> dict:
    return {
        "agent_run_id": agent_run_id,
        "llm": llm or [{"type": "llm", "status": "SUCCEEDED"}],
        "tool": tool or [],
        "rag": rag or [],
    }


# ── agentSuccess ──────────────────────────────────────────────────────────────

def test_agent_success_ok():
    result = check_rules(_case(), _resp(status="ok"), _trace())
    assert result["agent_success"] is True


def test_agent_success_failed():
    result = check_rules(_case(), _resp(status="FAILED", answer=None), _trace())
    assert result["agent_success"] is False


def test_agent_success_with_error_code():
    result = check_rules(_case(), {"route": "X", "answer": None, "status": "ok", "error_code": "ERR"}, _trace())
    assert result["agent_success"] is False


# ── routeAccuracy ─────────────────────────────────────────────────────────────

def test_route_accuracy_match():
    result = check_rules(_case(expected_route="PROGRAM_FACTS"), _resp(route="PROGRAM_FACTS"), _trace())
    assert result["route_accuracy"] is True


def test_route_accuracy_mismatch():
    result = check_rules(_case(expected_route="PROGRAM_FACTS"), _resp(route="KNOWLEDGE_RAG"), _trace())
    assert result["route_accuracy"] is False


def test_route_accuracy_none_when_no_expected():
    result = check_rules(_case(expected_route=None), _resp(route="PROGRAM_FACTS"), _trace())
    assert result["route_accuracy"] is None


# ── requiredToolHit ───────────────────────────────────────────────────────────

def test_required_tool_hit_success():
    spec = {"required_tools": ["searchPrograms"], "forbidden_tools": []}
    tool_traces = [{"toolName": "searchPrograms", "status": "SUCCESS"}]
    result = check_rules(_case(agent_spec=spec), _resp(), _trace(tool=tool_traces))
    assert result["required_tool_hit"] is True


def test_required_tool_hit_missing():
    spec = {"required_tools": ["getProgramDetail"], "forbidden_tools": []}
    tool_traces = [{"toolName": "searchPrograms", "status": "SUCCESS"}]
    result = check_rules(_case(agent_spec=spec), _resp(), _trace(tool=tool_traces))
    assert result["required_tool_hit"] is False


def test_required_tool_hit_none_when_no_requirement():
    result = check_rules(_case(), _resp(), _trace())
    assert result["required_tool_hit"] is None


# ── forbiddenToolCalled ───────────────────────────────────────────────────────

def test_forbidden_tool_called_detected():
    spec = {"required_tools": [], "forbidden_tools": ["addDraftCandidate"]}
    tool_traces = [{"toolName": "addDraftCandidate", "status": "SUCCESS"}]
    result = check_rules(_case(agent_spec=spec), _resp(), _trace(tool=tool_traces))
    assert result["forbidden_tool_called"] is True


def test_forbidden_tool_not_called():
    spec = {"required_tools": [], "forbidden_tools": ["addDraftCandidate"]}
    tool_traces = [{"toolName": "searchPrograms", "status": "SUCCESS"}]
    result = check_rules(_case(agent_spec=spec), _resp(), _trace(tool=tool_traces))
    assert result["forbidden_tool_called"] is False


# ── toolSuccessRate ───────────────────────────────────────────────────────────

def test_tool_success_rate_all_ok():
    tool_traces = [
        {"toolName": "t1", "status": "SUCCESS"},
        {"toolName": "t2", "status": "SUCCESS"},
    ]
    result = check_rules(_case(), _resp(), _trace(tool=tool_traces))
    assert result["tool_success_rate"] == pytest.approx(1.0)


def test_tool_success_rate_partial():
    tool_traces = [
        {"toolName": "t1", "status": "SUCCESS"},
        {"toolName": "t2", "status": "FAILED"},
    ]
    result = check_rules(_case(), _resp(), _trace(tool=tool_traces))
    assert result["tool_success_rate"] == pytest.approx(0.5)


def test_tool_success_rate_no_tools():
    result = check_rules(_case(), _resp(), _trace(tool=[]))
    assert result["tool_success_rate"] == pytest.approx(1.0)


# ── citationPresent ───────────────────────────────────────────────────────────

def test_citation_present_tokens_found():
    spec = {
        "reference_evidence": [
            {"evidence_key": "e1", "citation_tokens": ["深圳大学", "2025"]}
        ]
    }
    answer = "深圳大学2025年录取分数345分"
    result = check_rules(_case(agent_spec=spec), _resp(answer=answer), _trace())
    assert result["citation_present"] is True


def test_citation_present_tokens_missing():
    spec = {
        "reference_evidence": [
            {"evidence_key": "e1", "citation_tokens": ["北京大学", "2025"]}
        ]
    }
    answer = "深圳大学2025年录取分数345分"
    result = check_rules(_case(agent_spec=spec), _resp(answer=answer), _trace())
    assert result["citation_present"] is False


def test_citation_present_null_no_evidence():
    result = check_rules(_case(), _resp(), _trace())
    assert result["citation_present"] is None


# ── expectedAbstention ────────────────────────────────────────────────────────

def test_expected_abstention_phrase_present():
    spec = {"expected_abstention": True}
    answer = "根据现有信息，无法确认该院校招生计划。"
    result = check_rules(_case(agent_spec=spec), _resp(answer=answer), _trace())
    assert result["expected_abstention"] is True


def test_expected_abstention_phrase_missing():
    spec = {"expected_abstention": True}
    answer = "该院校预计招生50人。"
    result = check_rules(_case(agent_spec=spec), _resp(answer=answer), _trace())
    assert result["expected_abstention"] is False


def test_expected_abstention_null_not_required():
    result = check_rules(_case(), _resp(), _trace())
    assert result["expected_abstention"] is None


# ── traceComplete ─────────────────────────────────────────────────────────────

def test_trace_complete_happy_path():
    result = check_rules(
        _case(), _resp(status="ok"),
        _trace(llm=[{"type": "llm", "status": "SUCCEEDED"}], tool=[])
    )
    assert result["trace_complete"] is True


def test_trace_complete_missing_llm():
    result = check_rules(_case(), _resp(), {"agent_run_id": "r1", "llm": [], "tool": [], "rag": []})
    assert result["trace_complete"] is False
