from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """严格启动校验——任何必填项缺失则 ValidationError，进程拒绝启动。"""

    evaluation_internal_token: str
    java_control_plane_base_url: str
    judge_base_url: str
    judge_api_key: str
    judge_model: str  # 必须 ≠ 业务模型（由调用者保证，Config 层不重复校验）
    checkpoint_db_path: str = "checkpoints.sqlite"
    max_concurrency: int = 4

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


def get_settings() -> Settings:
    """FastAPI dependency——每次调用重新读取环境变量，方便测试时通过 dependency_overrides 替换。"""
    return Settings()
