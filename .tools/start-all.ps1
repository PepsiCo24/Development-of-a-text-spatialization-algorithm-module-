$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Test-LocalPort([int]$Port) {
    return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1)
}

if (-not (Test-LocalPort 5432)) {
    & (Join-Path $projectRoot '.tools\pgsql\bin\pg_ctl.exe') -D (Join-Path $projectRoot '.tools\pgsql-data') -l (Join-Path $projectRoot '.tools\postgresql.log') -w start
}

& (Join-Path $projectRoot '.tools\migrate-db.ps1')

if (-not (Test-LocalPort 6333)) {
    Start-Process -FilePath 'E:\GeoTextRuntime\qdrant\qdrant.exe' -WorkingDirectory 'E:\GeoTextRuntime\qdrant' -WindowStyle Hidden
}

if (-not (Test-LocalPort 7687)) {
    $neoHome = Join-Path $projectRoot '.tools\neo4j-package\neo4j-community-5.26.28'
    $env:NEO4J_HOME = $neoHome
    $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk17\jdk-17.0.19+10'
    Start-Process -FilePath (Join-Path $neoHome 'bin\neo4j.bat') -ArgumentList 'console' -WorkingDirectory $neoHome -WindowStyle Hidden
}

foreach ($service in @(
    @{ Port = 8000; Script = '.tools\run-ai.ps1' },
    @{ Port = 8080; Script = '.tools\run-backend.ps1' },
    @{ Port = 5173; Script = '.tools\run-frontend.ps1' }
)) {
    if (-not (Test-LocalPort $service.Port)) {
        Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File',$service.Script -WorkingDirectory $projectRoot -WindowStyle Hidden
    }
}

Write-Host 'GeoText services are starting.'
Write-Host 'Frontend: http://127.0.0.1:5173'
Write-Host 'Backend:  http://127.0.0.1:8080/swagger-ui.html'
Write-Host 'AI API:   http://127.0.0.1:8000/docs'
Write-Host 'Neo4j:    http://127.0.0.1:7474'
Write-Host 'Qdrant:   http://127.0.0.1:6333/dashboard'
