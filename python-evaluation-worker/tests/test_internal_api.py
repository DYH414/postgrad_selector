"""
Step 7 验证: 健康启动 + dispatch 幂等 + token 鉴权。
(§9 步骤7验证标准)
"""

import pytest
from fastapi.testclient import TestClient

# conftest.py 已在此之前设置好所有必填环境变量
from app.main import app
from app.api.internal import _dispatched

TOKEN = "test-secret-token-step7"
AUTH = {"X-Evaluation-Token": TOKEN}

_DISPATCH_BODY = {
    "run_id": "run-step7-001",
    "dataset_checksum": "sha256abc",
    "config_versions": [
        {
            "id": 1,
            "version_name": "v1",
            "business_prompt_text": "你是一个考研择校助手。",
            "model": "deepseek-chat",
            "temperature": 0.7,
            "tool_catalog_checksum": "tools-hash-001",
        }
    ],
    "cases": [
        {
            "id": 42,
            "case_key": "agent-program-facts-01",
            "query": "深圳大学计算机专硕近两年录取分数怎样？",
            "expected_route": "PROGRAM_FACTS",
            "agent": {
                "required_tools": ["searchPrograms"],
                "forbidden_tools": ["addDraftCandidate"],
                "expected_abstention": False,
                "expected_claims": [],
                "forbidden_claims": [],
                "reference_evidence": [],
            },
        }
    ],
}


@pytest.fixture(autouse=True)
def clear_dispatch_store():
    """每个测试前后清空内存幂等表，防止用例互相干扰。"""
    _dispatched.clear()
    yield
    _dispatched.clear()


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


# ── 健康检查 ───────────────────────────────────────────────────────────────────

def test_health_returns_ok(client):
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok"}


# ── dispatch 正常路径 ──────────────────────────────────────────────────────────

def test_dispatch_returns_202(client):
    res = client.post("/runs", json=_DISPATCH_BODY, headers=AUTH)
    assert res.status_code == 202
    body = res.json()
    assert body["run_id"] == "run-step7-001"
    assert body["status"] == "accepted"


def test_dispatch_idempotent_same_run_id(client):
    """同一 run_id 分发两次，两次都返回 202，且幂等表里只有一条记录。"""
    res1 = client.post("/runs", json=_DISPATCH_BODY, headers=AUTH)
    res2 = client.post("/runs", json=_DISPATCH_BODY, headers=AUTH)
    assert res1.status_code == 202
    assert res2.status_code == 202
    matching = [k for k in _dispatched if "run-step7-001" in k]
    assert len(matching) == 1, "幂等表里不应有重复记录"


def test_dispatch_different_run_ids_both_accepted(client):
    """不同 run_id 各自独立入队。"""
    body2 = {**_DISPATCH_BODY, "run_id": "run-step7-002"}
    res1 = client.post("/runs", json=_DISPATCH_BODY, headers=AUTH)
    res2 = client.post("/runs", json=body2, headers=AUTH)
    assert res1.status_code == 202
    assert res2.status_code == 202
    assert len(_dispatched) == 2


# ── token 鉴权 ─────────────────────────────────────────────────────────────────

def test_dispatch_wrong_token_returns_401(client):
    res = client.post("/runs", json=_DISPATCH_BODY,
                      headers={"X-Evaluation-Token": "wrong-token"})
    assert res.status_code == 401


def test_dispatch_missing_token_returns_422(client):
    """缺少必填 Header → FastAPI 422 Unprocessable Entity。"""
    res = client.post("/runs", json=_DISPATCH_BODY)
    assert res.status_code == 422


# ── resume stub ────────────────────────────────────────────────────────────────

def test_resume_stub_returns_200(client):
    res = client.post(
        "/runs/run-step7-001/resume",
        json={"interrupt_id": "itr-001", "decision": {"passed": True}},
        headers=AUTH,
    )
    assert res.status_code == 200
    assert res.json()["status"] == "resumed"
