$ErrorActionPreference = "Stop"

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run this script from an administrator PowerShell window."
}

$name = "Codex Mobile Bridge (Private LAN)"
Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule `
    -DisplayName $name `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort 47831 `
    -Profile Any `
    -RemoteAddress LocalSubnet | Out-Null

$ftpName = "Codex Mobile Bridge FTP (Private LAN)"
Get-NetFirewallRule -DisplayName $ftpName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule `
    -DisplayName $ftpName `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort 2121,50000-50100 `
    -Profile Any `
    -RemoteAddress LocalSubnet | Out-Null

Write-Output "Private-LAN firewall rules installed for Bridge and FTP"
