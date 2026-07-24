param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

function Decode-Utf8([string]$Value) {
    [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Value))
}

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body (@{username=$Username;password=$Password}|ConvertTo-Json)
$headers = @{Authorization="Bearer $($login.data.accessToken)"}
$demoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\demo-data")
$metadata = @(
    @{Prefix='01-';Region=(Decode-Utf8 '5rmW5YyX55yB5aSn5Ya25biC');Year=2026;Keyword=(Decode-Utf8 '6ZOc57u/5bGxLOmTnOmTgeefvyzmlq3oo4Is54K557q/6Z2i')},
    @{Prefix='02-';Region=(Decode-Utf8 '5rmW5YyX55yB5aSn5Ya25biC');Year=2026;Keyword=(Decode-Utf8 'WkswMDEs6ZK75a2ULOmTgeefv+S9kyzlk4HkvY0=')},
    @{Prefix='03-';Region=(Decode-Utf8 '5rmW5YyX55yB5aSn5Ya25biC');Year=2026;Keyword=(Decode-Utf8 '55+95Y2h5bKpLOm7hOmTnOefvyzno4Hpk4Hnn78s5ZOB5L2N')},
    @{Prefix='04-';Region=(Decode-Utf8 '5rmW5YyX55yB5aSn5Ya25biC');Year=2026;Keyword=(Decode-Utf8 'RjHmlq3oo4IsT0NSLOWAvuWQkSzlgL7op5I=')}
)
foreach($item in $metadata){
    $file=Get-ChildItem -LiteralPath $demoRoot -File | Where-Object { $_.Name.StartsWith($item.Prefix) } | Select-Object -First 1
    if(-not $file){ throw "Missing demo file with prefix $($item.Prefix)" }
    $name=[System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    curl.exe -sS -X POST "$BaseUrl/api/documents" -H "Authorization: $($headers.Authorization)" -F "file=@$($file.FullName)" -F "name=$name" -F "region=$($item.Region)" -F "year=$($item.Year)" -F "keyword=$($item.Keyword)"
}
