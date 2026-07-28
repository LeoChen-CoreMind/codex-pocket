$ErrorActionPreference = "Stop"

try {
    $tokenFile = Join-Path $env:LOCALAPPDATA "CodexMobileBridge\bridge.token"
    $headers = @{}
    if (Test-Path -LiteralPath $tokenFile) {
        $headers.Authorization = "Bearer $((Get-Content -Raw -LiteralPath $tokenFile).Trim())"
    }
    Invoke-RestMethod -Uri "http://127.0.0.1:47831/api/status" -Headers $headers -TimeoutSec 5 | ConvertTo-Json -Depth 6
} catch {
    Write-Output '{"bridgeState":"offline"}'
    exit 1
}
