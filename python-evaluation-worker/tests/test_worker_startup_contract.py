from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STARTER = ROOT / "python-evaluation-worker" / "start-worker.ps1"


def test_worker_starter_uses_only_locked_venv_and_checks_dependencies():
    assert STARTER.exists(), "the worker needs one canonical startup entrypoint"
    text = STARTER.read_text(encoding="utf-8")
    assert "venv\\Scripts\\python.exe" in text
    assert ".venv" not in text
    assert "pip check" in text
    assert "langgraph==0.2.62" in text
    assert "langgraph-checkpoint-sqlite==2.0.6" in text
    assert "aiosqlite==0.20.0" in text


def test_combined_launcher_delegates_to_canonical_worker_starter():
    text = (ROOT / "start-eval-services.ps1").read_text(encoding="utf-8")
    assert "start-worker.ps1" in text
