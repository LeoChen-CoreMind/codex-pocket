$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$stateDir = Join-Path $env:LOCALAPPDATA "CodexMobileBridge"
$pidFile = Join-Path $stateDir "bridge.pid"
$stdout = Join-Path $stateDir "bridge.out.log"
$stderr = Join-Path $stateDir "bridge.err.log"
$tokenFile = Join-Path $stateDir "bridge.token"
$configFile = Join-Path $stateDir "bridge-config.json"
$bridgePort = 47831

New-Item -ItemType Directory -Path $stateDir -Force | Out-Null

if (-not (Test-Path -LiteralPath $tokenFile)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $token = ([BitConverter]::ToString($bytes) -replace "-", "").ToLowerInvariant()
    Set-Content -LiteralPath $tokenFile -Value $token -Encoding ASCII -NoNewline
    $acl = Get-Acl -LiteralPath $tokenFile
    $acl.SetAccessRuleProtection($true, $false)
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
        [Security.Principal.WindowsIdentity]::GetCurrent().Name,
        [Security.AccessControl.FileSystemRights]::FullControl,
        [Security.AccessControl.AccessControlType]::Allow
    ))
    Set-Acl -LiteralPath $tokenFile -AclObject $acl
}
$env:BRIDGE_HOST = "0.0.0.0"
$env:BRIDGE_API_TOKEN = (Get-Content -Raw -LiteralPath $tokenFile).Trim()
if (Test-Path -LiteralPath $configFile) {
    $bridgeConfig = Get-Content -Raw -LiteralPath $configFile | ConvertFrom-Json
    if ($bridgeConfig.port -and [int]$bridgeConfig.port -ge 1024 -and [int]$bridgeConfig.port -le 65535) {
        $bridgePort = [int]$bridgeConfig.port
    }
    if ($bridgeConfig.hostExecutable) {
        $hostExecutable = [IO.Path]::GetFullPath([string]$bridgeConfig.hostExecutable)
        if (-not (Test-Path -LiteralPath $hostExecutable -PathType Leaf)) {
            throw "Configured host executable does not exist: $hostExecutable"
        }

        $hostProcess = $null
        if ($bridgeConfig.hostProcessId) {
            $configuredProcess = Get-Process -Id ([int]$bridgeConfig.hostProcessId) -ErrorAction SilentlyContinue
            if ($configuredProcess -and $configuredProcess.Path -and
                [string]::Equals($configuredProcess.Path, $hostExecutable, [StringComparison]::OrdinalIgnoreCase)) {
                $hostProcess = $configuredProcess
            }
        }
        if (-not $hostProcess) {
            $hostProcess = Get-Process -ErrorAction SilentlyContinue |
                Where-Object {
                    $_.Path -and $_.MainWindowTitle -and
                    [string]::Equals($_.Path, $hostExecutable, [StringComparison]::OrdinalIgnoreCase)
                } |
                Sort-Object StartTime -Descending |
                Select-Object -First 1
        }
        if (-not $hostProcess) {
            throw "The selected VS Code-compatible program is not running: $hostExecutable"
        }
        $env:BRIDGE_HOST_PROCESS_ID = [string]$hostProcess.Id
        $env:BRIDGE_HOST_EXECUTABLE = $hostExecutable
    } elseif ($bridgeConfig.hostProcessId) {
        $env:BRIDGE_HOST_PROCESS_ID = [string]$bridgeConfig.hostProcessId
    }
    if ($bridgeConfig.ftpPort) { $env:BRIDGE_FTP_PORT = [string]$bridgeConfig.ftpPort }
    if ($bridgeConfig.ftpUsername) { $env:BRIDGE_FTP_USERNAME = [string]$bridgeConfig.ftpUsername }
    if ($bridgeConfig.ftpPassword) { $env:BRIDGE_FTP_PASSWORD = [string]$bridgeConfig.ftpPassword }
}
$env:BRIDGE_PORT = [string]$bridgePort

if (Test-Path -LiteralPath $pidFile) {
    $existingPid = [int](Get-Content -Raw -LiteralPath $pidFile)
    $existing = Get-CimInstance Win32_Process -Filter "ProcessId = $existingPid" -ErrorAction SilentlyContinue
    if ($existing -and $existing.Name -eq "node.exe" -and $existing.CommandLine -match "dist/index\.js") {
        Write-Output "Codex bridge is already running. PID=$existingPid"
        exit 0
    }
    Remove-Item -LiteralPath $pidFile -Force
}

$distEntry = Join-Path $root "dist\index.js"
$buildInputs = @(
    Get-ChildItem -LiteralPath (Join-Path $root "src") -Recurse -File
    Get-Item -LiteralPath (Join-Path $root "package.json")
    Get-Item -LiteralPath (Join-Path $root "package-lock.json")
    Get-Item -LiteralPath (Join-Path $root "tsconfig.json")
)
$needsBuild = -not (Test-Path -LiteralPath $distEntry)
if (-not $needsBuild) {
    $distTime = (Get-Item -LiteralPath $distEntry).LastWriteTimeUtc
    $needsBuild = $null -ne ($buildInputs | Where-Object { $_.LastWriteTimeUtc -gt $distTime } | Select-Object -First 1)
}
if ($needsBuild) {
    & npm.cmd --prefix $root run build
    if ($LASTEXITCODE -ne 0) { throw "Bridge build failed" }
}

$process = Start-Process `
    -FilePath "node.exe" `
    -ArgumentList @("dist/index.js") `
    -WorkingDirectory $root `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -PassThru

Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ASCII

$ready = $false
for ($attempt = 0; $attempt -lt 40; $attempt++) {
    Start-Sleep -Milliseconds 500
    if ($process.HasExited) { break }
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$bridgePort/health" -TimeoutSec 2
        if ($health.ok) {
            $ready = $true
            break
        }
    } catch {}
}

if (-not $ready) {
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $stderr) { Get-Content -Tail 100 -LiteralPath $stderr }
    throw "Codex bridge did not become ready"
}

$lanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.AddressState -eq "Preferred" -and
        ($_.IPAddress -like "192.168.*" -or $_.IPAddress -like "10.*" -or $_.IPAddress -match '^172\.(1[6-9]|2\d|3[01])\.')
    } |
    Sort-Object @{ Expression = {
        $virtual = $_.InterfaceAlias -match 'Virtual|VMware|vEthernet|Hyper-V|WSL|TAP|TUN|Clash|ZeroTier'
        $physical = $_.InterfaceAlias -match 'WLAN|Wi-?Fi|Ethernet'
        $addressScore = if ($_.IPAddress -like "192.168.*") { 30 } elseif ($_.IPAddress -like "10.*") { 20 } else { 10 }
        -($addressScore + $(if ($physical) { 100 } else { 0 }) - $(if ($virtual) { 100 } else { 0 }))
    } } |
    Select-Object -First 1 -ExpandProperty IPAddress
if (-not $lanIp) { $lanIp = "127.0.0.1" }
Write-Output "Codex bridge ready: http://$lanIp`:$bridgePort (PID=$($process.Id))"
