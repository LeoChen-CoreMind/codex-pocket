$ErrorActionPreference = "Stop"

$controlDirectory = $PSScriptRoot
$rootDirectory = Split-Path -Parent $controlDirectory
$bridgeDirectory = Join-Path $rootDirectory "bridge"
$runtimeDirectory = Join-Path $controlDirectory "runtime"
$publishDirectory = Join-Path $controlDirectory "publish-static"
$esbuild = Join-Path $bridgeDirectory "node_modules\.bin\esbuild.cmd"
$proxyDirectory = Join-Path $bridgeDirectory "proxy"
$proxyOutput = Join-Path $runtimeDirectory "codex-proxy.exe"
$node = (Get-Command node.exe -ErrorAction Stop).Source
$go = (Get-Command go.exe -ErrorAction Stop).Source

New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null

Push-Location $bridgeDirectory
try {
    & npm.cmd run check
    if ($LASTEXITCODE -ne 0) { throw "Bridge type check failed" }

    & $esbuild "src\index.ts" --bundle --platform=node --format=cjs --target=node22 `
        "--outfile=$runtimeDirectory\bridge.cjs"
    if ($LASTEXITCODE -ne 0) { throw "Bridge bundle failed" }

    & $esbuild "src\app-server\json-parser.worker.ts" --bundle --platform=node --format=cjs --target=node22 `
        "--outfile=$runtimeDirectory\json-parser.worker.cjs"
    if ($LASTEXITCODE -ne 0) { throw "Bridge worker bundle failed" }
} finally {
    Pop-Location
}

Copy-Item -LiteralPath $node -Destination (Join-Path $runtimeDirectory "node.exe") -Force

Push-Location $proxyDirectory
try {
    & $go build -trimpath -ldflags "-s -w -H=windowsgui" -o $proxyOutput .
    if ($LASTEXITCODE -ne 0) { throw "Codex proxy build failed" }
} finally {
    Pop-Location
}

dotnet publish (Join-Path $controlDirectory "CodexPocketBridge.Control.csproj") `
    -c Release -r win-x64 --self-contained true -o $publishDirectory `
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:EnableCompressionInSingleFile=true -p:DebugType=None -p:DebugSymbols=false
if ($LASTEXITCODE -ne 0) { throw "Static controller publish failed" }

$output = Join-Path $publishDirectory "CodexPocketBridge.exe"
if (-not (Test-Path -LiteralPath $output -PathType Leaf)) { throw "Static executable was not produced" }
Write-Output "Static Codex Pocket Bridge: $output"
