$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$serviceRoot = Join-Path $projectRoot 'ai-service'
$python = if (Test-Path (Join-Path $serviceRoot '.venv\Scripts\python.exe')) {
    Join-Path $serviceRoot '.venv\Scripts\python.exe'
} else {
    'python'
}
& $python -m uvicorn app.main:app --app-dir $serviceRoot --host 0.0.0.0 --port 8000 --reload

