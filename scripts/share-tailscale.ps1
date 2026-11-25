# 通过 Tailscale 把本机的 FlightLog（http://localhost:8000）用 HTTPS 分享到自己的 tailnet，手机装 Tailscale 后即可访问。
# 只在自己的 tailnet 内可见（tailscale serve），不使用 Funnel，不对公网开放。
# 兼容 Windows PowerShell 5.1 和 PowerShell 7。

$tailscale = Get-Command tailscale -ErrorAction SilentlyContinue
if (-not $tailscale) {
    Write-Host "没有找到 tailscale 命令。请先安装 Tailscale：https://tailscale.com/download" -ForegroundColor Red
    exit 1
}

# 检查是否已登录：未登录时 tailscale status 返回非 0
$null = cmd /c "tailscale status >nul 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Tailscale 还没有登录或没有运行。请先执行 tailscale up 并完成登录。" -ForegroundColor Red
    exit 1
}

# 检查 FlightLog 是否在运行
$running = $false
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:8000/api/health" -UseBasicParsing -TimeoutSec 3
    $running = ($resp.StatusCode -eq 200)
} catch {
    $running = $false
}
if (-not $running) {
    Write-Host "FlightLog 没有在 http://localhost:8000 运行。请先执行 scripts\start-windows.ps1。" -ForegroundColor Red
    exit 1
}

# 后台分享本机 8000 端口（HTTPS 证书由 Tailscale 自动签发）
tailscale serve --bg 8000
if ($LASTEXITCODE -ne 0) {
    Write-Host "tailscale serve 失败。可能需要在 Tailscale 管理后台为这个 tailnet 开启 HTTPS（MagicDNS + HTTPS Certificates）。" -ForegroundColor Red
    exit $LASTEXITCODE
}

# 读出本机在 tailnet 里的域名，例如 my-pc.tail1234.ts.net
$dnsName = $null
try {
    $status = (tailscale status --json) | Out-String | ConvertFrom-Json
    $dnsName = $status.Self.DNSName.TrimEnd(".")
} catch {
    $dnsName = $null
}

Write-Host ""
if ($dnsName) {
    Write-Host "已分享。手机（已登录同一个 Tailscale 账号）打开：" -ForegroundColor Green
    Write-Host "  https://$dnsName" -ForegroundColor Green
} else {
    Write-Host "已分享。用 tailscale serve status 查看访问地址。" -ForegroundColor Green
}
Write-Host "停止分享：scripts\unshare-tailscale.ps1"
