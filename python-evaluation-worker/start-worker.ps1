[CmdletBinding()]
param(
    [int]$Port = 8100,
    [string]$HostAddress = '127.0.0.1'
)

$ErrorActionPreference = 'Stop'
$workerRoot = $PSScriptRoot
$python = Join-Path $workerRoot 'venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $python)) {
    throw 'Locked worker environment is missing. Create python-evaluation-worker/venv from requirements.txt.'
}

& $python -m pip check
if ($LASTEXITCODE -ne 0) {
    throw 'Python dependency integrity check failed.'
}

$expected = @(
    'langgraph==0.2.62',
    'langgraph-checkpoint-sqlite==2.0.6',
    'aiosqlite==0.20.0'
)
$actual = & $python -c "import importlib.metadata as m; print('langgraph=='+m.version('langgraph')); print('langgraph-checkpoint-sqlite=='+m.version('langgraph-checkpoint-sqlite')); print('aiosqlite=='+m.version('aiosqlite'))"
if ($LASTEXITCODE -ne 0 -or (Compare-Object -ReferenceObject $expected -DifferenceObject @($actual))) {
    throw 'Python worker dependency versions drifted from requirements.txt. Rebuild python-evaluation-worker/venv.'
}

Set-Location -LiteralPath $workerRoot
& $python -m uvicorn app.main:app --host $HostAddress --port $Port
