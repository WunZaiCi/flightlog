# 构建并启动 FlightLog（Docker Desktop）。兼容 Windows PowerShell 5.1 和 PowerShell 7。
# 用法：
#   scripts\start-windows.ps1
#   scripts\start-windows.ps1 -NpmRegistry https://registry.npmmirror.com   # 中国大陆网络
param(
    [string]$NpmRegistry = ""
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# 检查 Docker Desktop 是否在运行（不重定向原生命令的 stderr，避免 PS 5.1 把它当成错误）
$null = cmd /c "docker info >nul 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker 没有运行。请先启动 Docker Desktop，等它显示 Engine running 后再执行本脚本。" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path (Join-Path $root "data"))) {
    New-Item -ItemType Directory -Path (Join-Path $root "data") | Out-Null
}
if (-not (Test-Path (Join-Path $root ".env"))) {
    Write-Host "提示：没有找到 .env，AI 解析和航班号查询会被禁用（手动录入照常可用）。可复制 .env.example 为 .env 后填写。" -ForegroundColor Yellow
}

if ($NpmRegistry -ne "") {
    $env:NPM_REGISTRY = $NpmRegistry
}

Write-Host "正在构建并启动容器..."
docker compose up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Host "启动失败，请查看上面的输出。" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "等待服务就绪..."
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8000/api/health" -UseBasicParsing -TimeoutSec 2
        if ($resp.StatusCode -eq 200) { $ready = $true; break }
    } catch {
        Start-Sleep -Seconds 2
    }
}

if ($ready) {
    Write-Host "FlightLog 已启动：http://localhost:8000" -ForegroundColor Green
} else {
    Write-Host "容器已启动，但服务还没有就绪。可以用 docker compose logs -f 查看日志。" -ForegroundColor Yellow
}
