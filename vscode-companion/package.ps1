param(
    [string] $OutputPath
)

$ErrorActionPreference = "Stop"

$sourceDirectory = $PSScriptRoot
$package = Get-Content -LiteralPath (Join-Path $sourceDirectory "package.json") -Raw -Encoding UTF8 |
    ConvertFrom-Json
if (-not $OutputPath) {
    $OutputPath = Join-Path $sourceDirectory "dist\codex-pocket-companion-$($package.version).vsix"
}
$OutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$stagingDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("codex-pocket-vsix-" + [Guid]::NewGuid().ToString("N"))
$extensionDirectory = Join-Path $stagingDirectory "extension"
New-Item -ItemType Directory -Path $extensionDirectory -Force | Out-Null

try {
    Copy-Item -LiteralPath (Join-Path $sourceDirectory "package.json") -Destination $extensionDirectory
    Copy-Item -LiteralPath (Join-Path $sourceDirectory "extension.js") -Destination $extensionDirectory

    $identityId = [System.Security.SecurityElement]::Escape([string] $package.name)
    $publisher = [System.Security.SecurityElement]::Escape([string] $package.publisher)
    $version = [System.Security.SecurityElement]::Escape([string] $package.version)
    $displayName = [System.Security.SecurityElement]::Escape([string] $package.displayName)
    $description = [System.Security.SecurityElement]::Escape([string] $package.description)
    $engine = [System.Security.SecurityElement]::Escape([string] $package.engines.vscode)
    $manifest = @"
<?xml version="1.0" encoding="utf-8"?>
<PackageManifest Version="2.0.0" xmlns="http://schemas.microsoft.com/developer/vsx-schema/2011">
  <Metadata>
    <Identity Language="en-US" Id="$identityId" Version="$version" Publisher="$publisher" />
    <DisplayName>$displayName</DisplayName>
    <Description xml:space="preserve">$description</Description>
    <Categories>Other</Categories>
    <Properties>
      <Property Id="Microsoft.VisualStudio.Code.Engine" Value="$engine" />
      <Property Id="Microsoft.VisualStudio.Code.ExtensionKind" Value="workspace" />
    </Properties>
  </Metadata>
  <Installation>
    <InstallationTarget Id="Microsoft.VisualStudio.Code" />
  </Installation>
  <Dependencies />
  <Assets>
    <Asset Type="Microsoft.VisualStudio.Code.Manifest" Path="extension/package.json" Addressable="true" />
  </Assets>
</PackageManifest>
"@
    Set-Content -LiteralPath (Join-Path $stagingDirectory "extension.vsixmanifest") -Value $manifest -Encoding UTF8

    $contentTypes = @"
<?xml version="1.0" encoding="utf-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="json" ContentType="application/json" />
  <Default Extension="js" ContentType="application/javascript" />
  <Default Extension="vsixmanifest" ContentType="text/xml" />
</Types>
"@
    Set-Content -LiteralPath (Join-Path $stagingDirectory "[Content_Types].xml") -Value $contentTypes -Encoding UTF8

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (Test-Path -LiteralPath $OutputPath) {
        Remove-Item -LiteralPath $OutputPath -Force
    }
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $stagingDirectory,
        $OutputPath,
        [System.IO.Compression.CompressionLevel]::Optimal,
        $false
    )
} finally {
    if (Test-Path -LiteralPath $stagingDirectory) {
        Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
    }
}

Write-Output $OutputPath
