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
    @{File=(Decode-Utf8 'MDEt6YSC5Lic5Y2X6ZOB6ZOc55+/6LCD5p+lLnR4dA==');Region=(Decode-Utf8 '6YSC5Lic5Y2X');Year=2024;Keyword=(Decode-Utf8 '6ZOB6ZOc55+/LOaWreijgizoirHlspflsqk=')},
    @{File=(Decode-Utf8 'MDIt5a6c5piM56O355+/5Zyw5bGC6LCD5p+lLnR4dA==');Region=(Decode-Utf8 '5a6c5piM');Year=2024;Keyword=(Decode-Utf8 '56O355+/LOmch+aXpuezuyzlr5Lmrabns7s=')},
    @{File=(Decode-Utf8 'MDMt5aSn5Ya255+/5Yy66ZK75a2U6K6w5b2VLnR4dA==');Region=(Decode-Utf8 '5aSn5Ya2');Year=2025;Keyword=(Decode-Utf8 '6ZK75a2ULOmTgeefv+S9kyzlk4HkvY0=')}
)
foreach($item in $metadata){
    $path=Join-Path $demoRoot $item.File
    $name=[System.IO.Path]::GetFileNameWithoutExtension($item.File)
    curl.exe -sS -X POST "$BaseUrl/api/documents" -H "Authorization: $($headers.Authorization)" -F "file=@$path;type=text/plain" -F "name=$name" -F "region=$($item.Region)" -F "year=$($item.Year)" -F "keyword=$($item.Keyword)"
}
