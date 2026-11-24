# 停止 FlightLog。数据库文件保留在 data/ 中，不会被删除。
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$null = cmd /c "docker info >nul 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker 没有运行，无需停止。" -ForegroundColor Yellow
    exit 0
}

# 不加 -v：不删除任何数据
docker compose down
if ($LASTEXITCODE -eq 0) {
    Write-Host "FlightLog 已停止。数据仍保存在 data\flightlog.db。" -ForegroundColor Green
}
