# 找到 tailscale.exe：先查 PATH，再看 Tailscale 服务的安装目录（装在非默认盘时不在 PATH 里）。
# 兼容 Windows PowerShell 5.1 和 PowerShell 7。由 share/unshare 脚本引用。

function Find-Tailscale {
    $cmd = Get-Command tailscale -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    try {
        $service = Get-CimInstance Win32_Service -Filter "Name='Tailscale'" -ErrorAction Stop
        if ($service -and $service.PathName) {
            $daemon = $service.PathName.Trim().Trim('"')
            $exe = Join-Path (Split-Path $daemon -Parent) "tailscale.exe"
            if (Test-Path $exe) { return $exe }
        }
    } catch {}

    foreach ($dir in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if ($dir) {
            $exe = Join-Path $dir "Tailscale\tailscale.exe"
            if (Test-Path $exe) { return $exe }
        }
    }
    return $null
}
