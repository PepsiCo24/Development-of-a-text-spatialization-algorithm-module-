param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body (@{username=$Username;password=$Password}|ConvertTo-Json)
$headers = @{Authorization="Bearer $($login.data.accessToken)"}
$demoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\demo-data")
$metadata = @(
    @{File="01-鄂东南铁铜矿调查.txt";Region="鄂东南";Year=2024;Keyword="铁铜矿,断裂,花岗岩"},
    @{File="02-宜昌磷矿地层调查.txt";Region="宜昌";Year=2024;Keyword="磷矿,震旦系,寒武系"},
    @{File="03-大冶矿区钻孔记录.txt";Region="大冶";Year=2025;Keyword="钻孔,铁矿体,品位"}
)
foreach($item in $metadata){
    $path=Join-Path $demoRoot $item.File
    $name=[System.IO.Path]::GetFileNameWithoutExtension($item.File)
    curl.exe -sS -X POST "$BaseUrl/api/documents" -H "Authorization: $($headers.Authorization)" -F "file=@$path;type=text/plain" -F "name=$name" -F "region=$($item.Region)" -F "year=$($item.Year)" -F "keyword=$($item.Keyword)"
}
