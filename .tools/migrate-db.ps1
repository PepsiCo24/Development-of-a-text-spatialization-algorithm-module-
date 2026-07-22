$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$psql = Join-Path $projectRoot '.tools\pgsql\bin\psql.exe'
if (-not (Test-Path $psql)) { throw 'PostgreSQL client was not found.' }
$env:PGPASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'postgres' }
$files = Get-ChildItem (Join-Path $projectRoot 'database\migrations') -Filter '*.sql' | Sort-Object Name
foreach ($file in $files) {
    & $psql -h '127.0.0.1' -p 5432 -U 'postgres' -d 'geotext' -v ON_ERROR_STOP=1 -f $file.FullName
    if ($LASTEXITCODE -ne 0) { throw "Migration failed: $($file.Name)" }
}
Write-Host "Applied $($files.Count) database migration scripts."
