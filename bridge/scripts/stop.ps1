$ErrorActionPreference = "Stop"

$stateDirectory = Join-Path $env:LOCALAPPDATA "CodexMobileBridge"
$pidFile = Join-Path $stateDirectory "bridge.pid"
$configFile = Join-Path $stateDirectory "bridge-config.json"
$bridgePort = 47831
if (Test-Path -LiteralPath $configFile) {
    try {
        $bridgeConfig = Get-Content -Raw -LiteralPath $configFile | ConvertFrom-Json
        if ($bridgeConfig.port -and [int]$bridgeConfig.port -ge 1024 -and [int]$bridgeConfig.port -le 65535) {
            $bridgePort = [int]$bridgeConfig.port
        }
    } catch {}
}
if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Output "Codex bridge is not running"
    exit 0
}

$bridgePid = [int](Get-Content -Raw -LiteralPath $pidFile)
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $bridgePid" -ErrorAction SilentlyContinue
if ($process -and $process.Name -eq "node.exe" -and $process.CommandLine -match "dist/index\.js") {
    try {
        $tokenFile = Join-Path $stateDirectory "bridge.token"
        $headers = @{}
        if (Test-Path -LiteralPath $tokenFile) {
            $headers.Authorization = "Bearer $((Get-Content -Raw -LiteralPath $tokenFile).Trim())"
        }
        Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$bridgePort/internal/shutdown" -Headers $headers `
            -ContentType "application/json" -Body "{}" -TimeoutSec 3 | Out-Null
    } catch {}
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Milliseconds 250
        if (-not (Get-Process -Id $bridgePid -ErrorAction SilentlyContinue)) { break }
    }
    if (Get-Process -Id $bridgePid -ErrorAction SilentlyContinue) {
        $children = Get-CimInstance Win32_Process | Where-Object ParentProcessId -eq $bridgePid
        foreach ($child in $children) { Stop-Process -Id $child.ProcessId -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $bridgePid -Force
    }
}
Remove-Item -LiteralPath $pidFile -Force
Write-Output "Codex bridge stopped"
