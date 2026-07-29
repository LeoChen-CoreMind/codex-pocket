param(
    [string[]] $EditorCli,
    [string] $ExtensionsDirectory
)

$ErrorActionPreference = "Stop"

$vsix = & (Join-Path $PSScriptRoot "package.ps1")
$cliCandidates = [System.Collections.Generic.List[string]]::new()
foreach ($configured in $EditorCli) {
    if (-not [string]::IsNullOrWhiteSpace($configured)) {
        $cliCandidates.Add([System.IO.Path]::GetFullPath($configured))
    }
}

if ($cliCandidates.Count -eq 0) {
    $installationRoots = @(
        (Join-Path $env:LOCALAPPDATA "Programs"),
        $env:ProgramFiles,
        ${env:ProgramFiles(x86)}
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) }

    foreach ($root in $installationRoots) {
        foreach ($installation in Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue) {
            $productPath = Join-Path $installation.FullName "resources\app\product.json"
            if (-not (Test-Path -LiteralPath $productPath -PathType Leaf)) { continue }
            try {
                $product = Get-Content -LiteralPath $productPath -Raw -Encoding UTF8 | ConvertFrom-Json
                $applicationName = [string] $product.applicationName
                if ([string]::IsNullOrWhiteSpace($applicationName)) { continue }
                foreach ($relativePath in @(
                    "bin\$applicationName.cmd",
                    "resources\app\bin\$applicationName.cmd"
                )) {
                    $candidate = Join-Path $installation.FullName $relativePath
                    if (Test-Path -LiteralPath $candidate -PathType Leaf) { $cliCandidates.Add($candidate) }
                }
            } catch {
                Write-Warning "Cannot analyze editor product metadata: $productPath"
            }
        }
    }
}

$installed = 0
foreach ($cli in $cliCandidates | Select-Object -Unique) {
    if (-not (Test-Path -LiteralPath $cli -PathType Leaf)) { continue }
    if ([string]::IsNullOrWhiteSpace($ExtensionsDirectory)) {
        & $cli --install-extension $vsix --force
    } else {
        & $cli --extensions-dir ([System.IO.Path]::GetFullPath($ExtensionsDirectory)) --install-extension $vsix --force
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Codex Pocket Companion installation failed through $cli with exit code $LASTEXITCODE"
    }
    $installed++
    Write-Output "Codex Pocket Companion installed or repaired through: $cli"
}

if ($installed -eq 0) {
    throw "No compatible editor CLI was discovered. Pass it explicitly with -EditorCli."
}
Write-Output "Reload each editor window once to activate the Companion."
