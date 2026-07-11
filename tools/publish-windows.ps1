param(
    [string]$Configuration = "Release",
    [string]$RuntimeIdentifier = "win-x64",
    [string]$OutputPath = "",
    [switch]$FrameworkDependent,
    [switch]$CreateZip
)

$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = Split-Path -Parent $scriptDirectory
$projectPath = Join-Path $repositoryRoot "src\ClickAssistant.App\ClickAssistant.App.csproj"

# 读取统一版本号
$version = "0.0.0"
$versionFile = Join-Path $repositoryRoot "VERSION"
if (Test-Path $versionFile) {
    $versionContent = Get-Content $versionFile -Raw
    if ($versionContent -match 'PROJECT_VERSION=(\S+)') {
        $version = $matches[1]
    }
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $repositoryRoot "dist\ClickAssistant-$RuntimeIdentifier"
}

$localDotnet = Join-Path $env:USERPROFILE ".dotnet\dotnet.exe"
$dotnetCommand = if (Test-Path $localDotnet) { $localDotnet } else { "dotnet" }

$publishArguments = @(
    "publish",
    $projectPath,
    "--configuration",
    $Configuration,
    "--runtime",
    $RuntimeIdentifier,
    "--output",
    $OutputPath,
    "-p:Version=$version",
    "-p:PublishSingleFile=false"
)

if ($FrameworkDependent) {
    $publishArguments += "--no-self-contained"
} else {
    $publishArguments += "--self-contained"
    $publishArguments += "true"
}

Write-Host "========================================"
Write-Host "Click Assistant - Windows 发布"
Write-Host "========================================"
Write-Host "Version:  $version"
Write-Host "Project:  $projectPath"
Write-Host "Runtime:  $RuntimeIdentifier"
Write-Host "Output:   $OutputPath"

& $dotnetCommand @publishArguments

if ($LASTEXITCODE -ne 0) {
    throw "Publish failed with exit code $LASTEXITCODE."
}

Write-Host ""
Write-Host "Publish completed."
Write-Host "Executable: $(Join-Path $OutputPath 'ClickAssistantApp.exe')"

# Create the optional release ZIP package.
if ($CreateZip) {
    $zipPath = Join-Path $repositoryRoot "dist\ClickAssistant-windows-$version-$RuntimeIdentifier.zip"
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }
    Compress-Archive -Path "$OutputPath\*" -DestinationPath $zipPath -CompressionLevel Optimal
    Write-Host "ZIP archive: $zipPath"
}
