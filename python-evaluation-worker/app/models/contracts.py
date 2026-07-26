"""
Pydantic contracts for HTTP layer (全类型化，不用裸 dict — CLAUDE.md 原则6).

覆盖:
  - DispatchRunRequest / DispatchRunResponse   (§4 端点2 Java→Python)
  - ResumeRunRequest / ResumeRunResponse       (§4 端点+ 管理端→Python)
  - AgentCaseSpec / ReferenceEvidence / DispatchCase / ConfigVersion  (用例与配置版本)
"""

from __future__ import annotations

from pydantic import BaseModel, Field


# ── Case spec ────────────────────────────────────────────────────────────────

class ReferenceEvidence(BaseModel):
    evidence_key: str
    citation_tokens: list[str] = Field(default_factory=list)


class AgentCaseSpec(BaseModel):
    """§3.1 agent{} 嵌套结构，空集合写 [] 不省略。"""

    required_tools: list[str] = Field(default_factory=list)
    forbidden_tools: list[str] = Field(default_factory=list)
    expected_abstention: bool = False
    expected_claims: list[str] = Field(default_factory=list)
    forbidden_claims: list[str] = Field(default_factory=list)
    reference_evidence: list[ReferenceEvidence] = Field(default_factory=list)


class DispatchCase(BaseModel):
    id: int
    case_key: str
    query: str
    expected_route: str | None = None
    category: str | None = None
    difficulty: str | None = None
    agent: AgentCaseSpec | None = None


# ── Config version ────────────────────────────────────────────────────────────

class ConfigVersion(BaseModel):
    """§3.3 AgentConfigVersion 快照（从 Java 下发，不可变）。"""

    id: int
    version_name: str
    business_prompt_text: str
    routing_prompt_text: str | None = None
    model: str
    temperature: float
    tool_catalog_checksum: str


# ── Dispatch ──────────────────────────────────────────────────────────────────

class DispatchRunRequest(BaseModel):
    run_id: str
    dataset_checksum: str
    config_versions: list[ConfigVersion] = Field(min_length=1)
    cases: list[DispatchCase] = Field(min_length=1)


class DispatchRunResponse(BaseModel):
    run_id: str
    status: str  # "accepted"


# ── Resume (HITL) ─────────────────────────────────────────────────────────────

class ResumeRunRequest(BaseModel):
    """人工评审裁定，恢复 interrupt() 挂起的图（§5.6）。"""

    interrupt_id: str
    decision: dict  # {passed: bool, reason: str, ...}


class ResumeRunResponse(BaseModel):
    run_id: str
    status: str  # "resumed"
