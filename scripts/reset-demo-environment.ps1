param(
    [switch]$Execute,
    [string]$Database = 'geotext',
    [string]$DatabaseUser = 'postgres',
    [string]$DatabasePassword = 'postgres',
    [string]$Neo4jPassword = $env:NEO4J_PASSWORD
)

$ErrorActionPreference = 'Stop'
if (-not $Execute) {
    throw 'This command clears all business data. Re-run with -Execute after confirming the target workspace.'
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$uploadRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'backend\uploads\documents'))
$expectedSuffix = [System.IO.Path]::Combine('backend', 'uploads', 'documents')
if (-not $uploadRoot.EndsWith($expectedSuffix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unexpected upload root: $uploadRoot"
}

$psql = Join-Path $projectRoot '.tools\pgsql\bin\psql.exe'
if (-not (Test-Path -LiteralPath $psql)) { throw "psql not found: $psql" }
$env:PGPASSWORD = $DatabasePassword
$sql = @'
BEGIN;
TRUNCATE TABLE
  system_log,
  spatial_object,
  entity_relation,
  entity_attribute,
  entity,
  document_chunk,
  document,
  dictionary
RESTART IDENTITY CASCADE;
COMMIT;
'@
& $psql -h '127.0.0.1' -p 5432 -U $DatabaseUser -d $Database -v ON_ERROR_STOP=1 -c $sql
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL cleanup failed.' }

if (Test-Path -LiteralPath $uploadRoot) {
    Get-ChildItem -LiteralPath $uploadRoot -File -Recurse | ForEach-Object {
        $target = [System.IO.Path]::GetFullPath($_.FullName)
        if (-not $target.StartsWith($uploadRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to delete file outside upload root: $target"
        }
        Remove-Item -LiteralPath $target -Force
    }
}

try {
    Invoke-RestMethod -Method Delete -Uri 'http://127.0.0.1:6333/collections/geotext_chunks' -TimeoutSec 10 | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}

if (-not $Neo4jPassword) {
    $envFile = Join-Path $projectRoot 'ai-service\.env'
    if (Test-Path -LiteralPath $envFile) {
        $line = Get-Content -LiteralPath $envFile | Where-Object { $_ -like 'NEO4J_PASSWORD=*' } | Select-Object -First 1
        if ($line) { $Neo4jPassword = $line.Substring('NEO4J_PASSWORD='.Length) }
    }
}
$cypherShell = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools\neo4j-package') -Filter 'cypher-shell.bat' -Recurse -File | Select-Object -First 1
if ($cypherShell -and $Neo4jPassword) {
    $javaHome = Join-Path $projectRoot '.tools\jdk17\jdk-17.0.19+10'
    if (Test-Path -LiteralPath $javaHome) {
        $env:JAVA_HOME = $javaHome
        $env:Path = (Join-Path $javaHome 'bin') + [System.IO.Path]::PathSeparator + $env:Path
    }
    & $cypherShell.FullName -a 'bolt://127.0.0.1:7687' -u 'neo4j' -p $Neo4jPassword 'MATCH (n) DETACH DELETE n;'
    if ($LASTEXITCODE -ne 0) { throw 'Neo4j cleanup failed.' }
}

$seed = Join-Path $projectRoot 'database\seed-demo-dictionary.sql'
& $psql -h '127.0.0.1' -p 5432 -U $DatabaseUser -d $Database -v ON_ERROR_STOP=1 -f $seed
if ($LASTEXITCODE -ne 0) { throw 'Dictionary seed failed.' }

Write-Output 'Business data, logs, uploaded files, graph nodes and vector collection were cleared.'
Write-Output 'User accounts and LLM configuration were preserved. Standard dictionary entries were re-seeded.'
