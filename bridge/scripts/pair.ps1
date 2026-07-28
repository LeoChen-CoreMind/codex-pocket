$ErrorActionPreference = "Stop"

$tokenFile = Join-Path $env:LOCALAPPDATA "CodexMobileBridge\bridge.token"
if (-not (Test-Path -LiteralPath $tokenFile)) {
    throw "Bridge token is missing. Run scripts\start.ps1 first."
}

$lanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.InterfaceAlias -eq "WLAN" -and $_.AddressState -eq "Preferred" } |
    Select-Object -First 1 -ExpandProperty IPAddress
if (-not $lanIp) { throw "No active WLAN IPv4 address found" }

[pscustomobject]@{
    ServerUrl = "http://$lanIp`:47831"
    Token = (Get-Content -Raw -LiteralPath $tokenFile).Trim()
} | Format-List
