$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$serviceRoot = Join-Path $projectRoot 'ai-service'
$python = if (Test-Path (Join-Path $serviceRoot '.venv\Scripts\python.exe')) {
    Join-Path $serviceRoot '.venv\Scripts\python.exe'
} else {
    'python'
}
$ocrRuntime = Join-Path $env:USERPROFILE '.geotext-ai-packages'
if (Test-Path $ocrRuntime) { $env:PYTHONPATH = "$ocrRuntime;$env:PYTHONPATH" }
$env:PADDLE_PDX_MODEL_SOURCE = 'huggingface'
$env:PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK = 'True'
$env:PADDLE_PDX_ENABLE_MKLDNN_BYDEFAULT = 'False'
$env:FLAGS_use_mkldnn = '0'
& $python -m uvicorn app.main:app --app-dir $serviceRoot --host 0.0.0.0 --port 8000 --reload

