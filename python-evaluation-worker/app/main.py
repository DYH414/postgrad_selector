from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.internal import router as internal_router
from app.config import get_settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用启动时严格校验必填配置——任何缺失则进程拒绝启动。"""
    settings = get_settings()  # 缺必填项 → ValidationError
    if not settings.evaluation_internal_token.strip():
        raise RuntimeError("EVALUATION_INTERNAL_TOKEN must not be empty")
    yield


app = FastAPI(
    title="Evaluation Worker",
    version="0.1.0",
    description="Python FastAPI + LangGraph agent offline evaluation service",
    lifespan=lifespan,
)

app.include_router(internal_router, prefix="/runs", tags=["evaluation"])


@app.get("/health", tags=["meta"])
async def health() -> dict:
    return {"status": "ok"}
