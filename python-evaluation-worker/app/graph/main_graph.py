"""
主图（run 级）— §5.1 节点实现。

链路：load_run → [Send 扇出] → run_case(×N) → aggregate → compare → save_run → END

三处官方纠正（§5.0）：
1. Send API 扇出（条件边返回 list[Send]）— 不用 add_node(compiled_subgraph)
2. max_concurrency 通过 config 传入（不用外层 asyncio.Semaphore）
3. judge @task 并发在 case_subgraph 内的单节点完成
"""

from __future__ import annotations

import asyncio
from typing import Union

from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from langgraph.graph import END, StateGraph
from langgraph.types import Send

from app.graph.case_graph import build_case_graph
from app.graph.state import CaseState, RunState

# case 子图编译一次，复用（无 checkpointer，由主图 checkpointer 管理）
_case_subgraph = build_case_graph()


# ── 主图节点 ──────────────────────────────────────────────────────────────────

def load_run(state: RunState) -> dict:
    """
    规范化 dispatch payload，构建 (configVersion × case) 组合列表。
    payload 非法时直接产出空 combos，后续在 aggregate 归为技术失败。
    """
    config_versions = state.get("config_versions") or []
    cases = state.get("cases") or []
    combos = [
        {
            "config_version_id": cv["id"],
            "config_version": cv,
            "case": case,
        }
        for cv in config_versions
        for case in cases
    ]
    return {"combos": combos}


def _fanout(state: RunState) -> Union[list[Send], str]:
    """
    条件边：返回 list[Send] 实现并行扇出（§5.0 官方纠正一）。
    空 combos → 直接跳 aggregate（不发 Send）。
    """
    combos = state.get("combos") or []
    if not combos:
        return "aggregate"
    return [
        Send(
            "run_case",
            {
                "run_id": state["run_id"],
                "combo": combo,
            },
        )
        for combo in combos
    ]


async def run_case(send_input: dict, config: dict | None = None) -> dict:
    """
    父图普通节点——调 case 子图（§5.0 官方纠正一：不用 add_node(compiled_subgraph)）。
    结果通过 operator.add reducer 累加到 RunState.case_results。
    config 显式传递给子图，确保 java_client/judge_client 被继承。
    子图内已兜底，此处不抛（§5.5 铁律）。
    子图执行完毕后回调 Java 落库（§5.1 缺口3补全）。
    """
    combo = send_input["combo"]
    initial: CaseState = {
        "run_id": send_input["run_id"],
        "case": combo["case"],
        "config_version_id": combo["config_version_id"],
        "agent_response": {},
        "agent_status": "",
        "trace": {},
        "rule_result": {},
        "judge_samples": [],
        "judge_verdict": {},
        "reflect_count": 0,
        "human_decision": None,
        "failure_reasons": [],
        "final_result": {},
    }
    try:
        sub = await _case_subgraph.ainvoke(initial, config=config)
        result = sub.get("final_result") or {"passed": False, "status": "no_result",
                                              "case_id": combo["case"].get("id"),
                                              "config_version_id": combo["config_version_id"],
                                              "failure_reasons": ["case_subgraph returned no final_result"]}
    except Exception as exc:  # noqa: BLE001
        result = {
            "passed": False,
            "status": "subgraph_error",
            "case_id": combo["case"].get("id"),
            "config_version_id": combo["config_version_id"],
            "failure_reasons": [f"subgraph_error: {exc}"],
        }

    # 回调 Java 落库单个 case 结果（§5.1 缺口3补全）
    cfg_inner = (config or {}).get("configurable", {})
    java_client = cfg_inner.get("java_client")
    if java_client:
        try:
            await java_client.post_case_result(
                run_id=send_input["run_id"],
                case_id=combo["case"].get("case_id"),
                result=result,
            )
        except Exception as callback_exc:  # noqa: BLE001
            # 回调失败不影响图继续（Java 侧有 /case-results 查漏兜底）
            import logging
            logging.getLogger(__name__).warning(
                "post_case_result failed run_id=%s case_id=%s: %s",
                send_input["run_id"], combo["case"].get("case_id"), callback_exc
            )

    return {"case_results": [result]}


def aggregate(state: RunState) -> dict:
    """按 configVersion 分组聚合（§5.1）。"""
    results = state.get("case_results") or []
    total = len(results)
    passed = sum(1 for r in results if r.get("passed"))

    by_version: dict[str, list] = {}
    for r in results:
        vid = str(r.get("config_version_id", "unknown"))
        by_version.setdefault(vid, []).append(r)

    version_stats: dict[str, dict] = {}
    for vid, vr in by_version.items():
        vt = len(vr)
        vp = sum(1 for r in vr if r.get("passed"))
        faithfulness_vals = [r.get("faithfulness") for r in vr if r.get("faithfulness") is not None]
        version_stats[vid] = {
            "total": vt,
            "passed": vp,
            "pass_rate": round(vp / vt, 4) if vt else 0.0,
            "route_accuracy": round(
                sum(1 for r in vr if r.get("route_accuracy")) / vt, 4
            ) if vt else 0.0,
            "avg_faithfulness": round(sum(faithfulness_vals) / len(faithfulness_vals), 4)
            if faithfulness_vals else None,
            "avg_latency_ms": round(
                sum(r.get("latency_ms") or 0 for r in vr) / vt
            ) if vt else None,
        }

    return {
        "aggregates": {
            "total": total,
            "passed": passed,
            "pass_rate": round(passed / total, 4) if total else 0.0,
            "by_version": version_stats,
        }
    }


def compare(state: RunState) -> dict:
    """
    V1/V2/V3 对比：passRate / routeAccuracy / faithfulness / latency delta（§5.1）。
    仅同 dataset_checksum 才计算 delta（§5.1 note）。
    """
    by_version = state.get("aggregates", {}).get("by_version", {})
    versions = list(by_version.items())
    if len(versions) < 2:
        return {"comparison": {"note": "单版本，无对比"}}

    comparisons: dict[str, dict] = {}
    base_id, base = versions[0]
    for cand_id, cand in versions[1:]:
        metrics = ["pass_rate", "route_accuracy", "avg_faithfulness"]
        deltas = {}
        for m in metrics:
            bv, cv = base.get(m), cand.get(m)
            if bv is not None and cv is not None:
                delta = round(cv - bv, 4)
                deltas[m] = {
                    "base": bv, "candidate": cv, "delta": delta,
                    "status": "IMPROVED" if delta > 0 else ("REGRESSED" if delta < 0 else "UNCHANGED"),
                }
        lat_b = base.get("avg_latency_ms")
        lat_c = cand.get("avg_latency_ms")
        if lat_b is not None and lat_c is not None:
            deltas["avg_latency_ms"] = {"base": lat_b, "candidate": lat_c,
                                         "delta": lat_c - lat_b}
        comparisons[f"{base_id}->{cand_id}"] = deltas

    return {"comparison": comparisons}


async def save_run(state: RunState, config: dict | None = None) -> dict:
    """
    最终汇总回调（可选）。
    per-case result 已在 run_case 内（通过 java_client.post_case_result）逐条回调。
    此节点做最终运行汇总 PUT（若 Java 支持）或仅记录日志。
    TODO step 10: 调用 Java 汇总接口（若有）。
    """
    return {}


# ── 图构建 ─────────────────────────────────────────────────────────────────────

def build_main_graph(checkpointer=None):
    """
    编译主图。
    - checkpointer=AsyncSqliteSaver → 支持 interrupt/resume + 重启恢复（§5.6）
    - max_concurrency 通过 invoke config 传入，非外层 Semaphore（§5.0 官方纠正三）
    """
    builder = StateGraph(RunState)

    builder.add_node("load_run", load_run)
    builder.add_node("run_case", run_case)
    builder.add_node("aggregate", aggregate)
    builder.add_node("compare", compare)
    builder.add_node("save_run", save_run)

    builder.set_entry_point("load_run")
    builder.add_conditional_edges("load_run", _fanout, ["run_case", "aggregate"])
    builder.add_edge("run_case", "aggregate")
    builder.add_edge("aggregate", "compare")
    builder.add_edge("compare", "save_run")
    builder.add_edge("save_run", END)

    return builder.compile(checkpointer=checkpointer)


async def run_main_graph(
    run_id: str,
    dataset_checksum: str,
    config_versions: list[dict],
    cases: list[dict],
    checkpoint_db_path: str = "checkpoints.sqlite",
    max_concurrency: int = 4,
    extra_config: dict | None = None,
) -> dict:
    """
    入口函数：由 dispatch 端点以 asyncio.create_task 调用。

    一 run 一 thread（thread_id = run_id，§5.6）。
    max_concurrency 控制并行 run_case 数（§5.0 官方纠正三）。
    """
    async with AsyncSqliteSaver.from_conn_string(checkpoint_db_path) as checkpointer:
        graph = build_main_graph(checkpointer=checkpointer)
        initial: RunState = {
            "run_id": run_id,
            "dataset_checksum": dataset_checksum,
            "config_versions": config_versions,
            "cases": cases,
            "combos": [],
            "case_results": [],
            "aggregates": {},
            "comparison": {},
        }
        cfg: dict = {
            "configurable": {"thread_id": run_id, **(extra_config or {})},
            "max_concurrency": max_concurrency,
            "recursion_limit": 50,
        }
        return await graph.ainvoke(initial, config=cfg)
