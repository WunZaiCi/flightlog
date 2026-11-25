# 停止通过 Tailscale 分享 FlightLog（不影响本机 http://localhost:8000 的访问，也不删除任何数据）。
# 兼容 Windows PowerShell 5.1 和 PowerShell 7。

. (Join-Path $PSScriptRoot "find-tailscale.ps1")
$tailscale = Find-Tailscale
if (-not $tailscale) {
    Write-Host "没有找到 tailscale 命令，无需停止。" -ForegroundColor Yellow
    exit 0
}

& $tailscale serve reset
if ($LASTEXITCODE -eq 0) {
    Write-Host "已停止 Tailscale 分享。" -ForegroundColor Green
} else {
    Write-Host "tailscale serve reset 失败，请查看上面的输出。" -ForegroundColor Red
    exit $LASTEXITCODE
}
