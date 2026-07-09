param(
    [string]$Configuration = "Release",
    [string]$RuntimeIdentifier = "win-x64",
    [string]$OutputPath = "",
    [switch]$FrameworkDependent
)

$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = Split-Path -Parent $scriptDirectory
$projectPath = Join-Path $repositoryRoot "src\ClickAssistant.App\ClickAssistant.App.csproj"

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
    "-p:PublishSingleFile=false"
)

if ($FrameworkDependent) {
    $publishArguments += "--no-self-contained"
} else {
    $publishArguments += "--self-contained"
    $publishArguments += "true"
}

Write-Host "Publishing Click Assistant..."
Write-Host "Project: $projectPath"
Write-Host "Runtime: $RuntimeIdentifier"
Write-Host "Output:  $OutputPath"

& $dotnetCommand @publishArguments

if ($LASTEXITCODE -ne 0) {
    throw "Publish failed with exit code $LASTEXITCODE."
}

Write-Host ""
Write-Host "Publish completed."
Write-Host "Executable: $(Join-Path $OutputPath 'ClickAssistant.App.exe')"
