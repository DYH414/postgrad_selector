import os

# 在任何 app 模块导入之前设置环境变量，避免 pydantic-settings 报 ValidationError
os.environ.update({
    "EVALUATION_INTERNAL_TOKEN": "test-secret-token-step7",
    "JAVA_CONTROL_PLANE_BASE_URL": "http://localhost:8080",
    "JUDGE_BASE_URL": "http://localhost:9090",
    "JUDGE_API_KEY": "test-judge-key",
    "JUDGE_MODEL": "test-judge-model",
})
