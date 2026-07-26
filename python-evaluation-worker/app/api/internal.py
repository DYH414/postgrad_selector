from __future__ import annotations

import asyncio
import hmac

from fastapi import APIRouter, Depends, Header, HTTPException, status

from app.config import Settings, get_settings
from app.models.contracts import (
    DispatchRunRequest,
    DispatchRunResponse,
    ResumeRunRequest,
    ResumeRunResponse,
)

router = APIRouter()

# In-memory dispatch idempotency store: dispatch:{run_id} → True
_dispatched: dict[str, bool] = {}


def verify_token(
    x_evaluation_token: str = Header(...),
    settings: Settings = Depends(get_settings),
) -> None:
    """常量时间 token 比较，防止时序侧信道攻击。"""
    expected = settings.evaluation_internal_token.encode("utf-8")
    provided = x_evaluation_token.encode("utf-8")
    if not hmac.compare_digest(expected, provided):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unauthorized")


@router.post("", status_code=202, response_model=DispatchRunResponse)
async def dispatch_run(
    request: DispatchRunRequest,
    _: None = Depends(verify_token),
    settings: Settings = Depends(get_settings),
) -> DispatchRunResponse:
    """
    Java → Python: 下发评测 Run（§4 端点2）。
    幂等：相同 run_id 再次 dispatch 直接返回 202，不重复入队。
    """
    key = f"dispatch:{request.run_id}"
    if key not in _dispatched:
        _dispatched[key] = True
        # 接线主图：fire-and-forget（§5.6 一 run 一 thread）
        asyncio.create_task(
            _run_graph(request, settings),
            name=f"eval-run-{request.run_id}",
        )
    return DispatchRunResponse(run_id=request.run_id, status="accepted")


async def _run_graph(request: DispatchRunRequest, settings: Settings) -> None:
    """后台运行主图，异常不会影响 HTTP 响应。"""
    from app.graph.main_graph import run_main_graph  # 延迟导入，避免启动时循环
    try:
        await run_main_graph(
            run_id=request.run_id,
            dataset_checksum=request.dataset_checksum,
            config_versions=[cv.model_dump() for cv in request.config_versions],
            cases=[c.model_dump() for c in request.cases],
            checkpoint_db_path=settings.checkpoint_db_path,
            max_concurrency=settings.max_concurrency,
        )
    except Exception as exc:  # noqa: BLE001
        # 记录错误（生产应接入 logging/alerting），不重新抛出
        import logging
        logging.getLogger(__name__).exception("eval run %s failed: %s", request.run_id, exc)


@router.post("/{run_id}/resume", status_code=200, response_model=ResumeRunResponse)
async def resume_run(
    run_id: str,
    request: ResumeRunRequest,
    _: None = Depends(verify_token),
    settings: Settings = Depends(get_settings),
) -> ResumeRunResponse:
    """
    管理端 → Python: 人工评审裁定后恢复挂起图（§4 端点+）。
    立即返回 200；图恢复以 fire-and-forget 后台任务执行。
    """
    asyncio.create_task(
        _resume_graph(run_id, request, settings),
        name=f"eval-resume-{run_id}",
    )
    return ResumeRunResponse(run_id=run_id, status="resumed")


async def _resume_graph(run_id: str, request: ResumeRunRequest, settings: Settings) -> None:
    """后台恢复挂起图——Command(resume={interrupt_id: decision})（§5.6）。"""
    import logging
    from app.graph.main_graph import build_main_graph
    from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
    from langgraph.types import Command

    try:
        async with AsyncSqliteSaver.from_conn_string(settings.checkpoint_db_path) as checkpointer:
            graph = build_main_graph(checkpointer=checkpointer)
            cfg = {"configurable": {"thread_id": run_id}}
            await graph.ainvoke(
                Command(resume={request.interrupt_id: request.decision}),
                config=cfg,
            )
    except Exception as exc:  # noqa: BLE001
        logging.getLogger(__name__).exception("resume run %s failed: %s", run_id, exc)
