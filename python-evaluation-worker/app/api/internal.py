from __future__ import annotations

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
# Step 8 将替换为 LangGraph checkpoint 状态检测（通过 graph.get_state(config) 判断是否已存在）
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
) -> DispatchRunResponse:
    """
    Java → Python: 下发评测 Run（§4 端点2）。

    幂等：相同 run_id 再次 dispatch 直接返回 202，不重复入队。
    Step 8 接线：在此处 asyncio.create_task(run_main_graph(request))。
    """
    key = f"dispatch:{request.run_id}"
    if key not in _dispatched:
        _dispatched[key] = True
        # TODO step 8: asyncio.create_task(run_main_graph(request, get_settings()))
    return DispatchRunResponse(run_id=request.run_id, status="accepted")


@router.post("/{run_id}/resume", status_code=200, response_model=ResumeRunResponse)
async def resume_run(
    run_id: str,
    request: ResumeRunRequest,
    _: None = Depends(verify_token),
) -> ResumeRunResponse:
    """
    管理端 → Python: 人工评审裁定后恢复挂起图（§4 端点+）。

    Step 8 接线：graph.update_state + graph.stream(Command(resume=...))。
    """
    # TODO step 8: graph_runner.resume(run_id, request.interrupt_id, request.decision)
    return ResumeRunResponse(run_id=run_id, status="resumed")
