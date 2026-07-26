"""
LangGraph state schemas — 独立 schema，父图不见 case 私有态（§5.4）。

RunState  : 主图（run 级），所有并行 run_case 通过 operator.add reducer 累加结果。
CaseState : case 子图私有，不渗进 RunState。
"""

from __future__ import annotations

import operator
from typing import Annotated, TypedDict


class RunState(TypedDict):
    run_id: str
    dataset_checksum: str
    config_versions: list[dict]
    cases: list[dict]
    combos: list[dict]                              # load_run 生成的 (configVersion × case) 组合
    case_results: Annotated[list, operator.add]     # reducer: 并行 run_case 累加，绝不覆盖
    aggregates: dict
    comparison: dict


class CaseState(TypedDict):
    run_id: str
    case: dict
    config_version_id: int
    # invoke_agent 输出
    agent_response: dict
    agent_status: str       # ok | timeout | http_error | invalid_output | unsupported_side_effect
    trace: dict             # 内联 trace（§4 端点3 返回值）
    # rule_check 输出
    rule_result: dict
    # judge 输出
    judge_samples: list
    judge_verdict: dict
    # 流程控制
    reflect_count: int
    human_decision: dict | None
    failure_reasons: list[str]
    # 铁律（§5.5）：finalize 必须填充此字段，绝不让 case 消失
    final_result: dict
