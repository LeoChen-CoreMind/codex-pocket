$ErrorActionPreference = "Stop"

$source = $PSScriptRoot
$extensionRoots = @(
    @{ Name = "Visual Studio Code"; Path = (Join-Path $env:USERPROFILE ".vscode\extensions") },
    @{ Name = "Cursor"; Path = (Join-Path $env:USERPROFILE ".cursor\extensions") },
    @{ Name = "Antigravity IDE"; Path = (Join-Path $env:USERPROFILE ".antigravity-ide\extensions") }
)

foreach ($editor in $extensionRoots) {
    $extensionsRoot = $editor.Path
    $parent = Split-Path -Parent $extensionsRoot
    if ($editor.Name -ne "Visual Studio Code" -and -not (Test-Path -LiteralPath $parent)) {
        continue
    }

    New-Item -ItemType Directory -Path $extensionsRoot -Force | Out-Null
    $target = Join-Path $extensionsRoot "leochen.codex-pocket-companion-0.1.0"
    if (Test-Path -LiteralPath $target) {
        $item = Get-Item -LiteralPath $target -Force
        if ($item.LinkType -eq "Junction" -and $item.Target -contains $source) {
            Write-Output "Codex Pocket Companion is already installed for $($editor.Name): $target"
            continue
        }
        throw "Extension target already exists and is not managed by this project: $target"
    }

    New-Item -ItemType Junction -Path $target -Target $source | Out-Null
    Write-Output "Codex Pocket Companion installed for $($editor.Name): $target"
}

Write-Output "Reload each editor window once to activate the Companion."
