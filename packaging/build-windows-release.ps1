param(
    [string]$JpackagePath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$releaseRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "build\release"))
$appImage = [System.IO.Path]::GetFullPath((Join-Path $releaseRoot "MacroOverlay"))
$expectedPrefix = $releaseRoot.TrimEnd('\') + '\'
if (-not $appImage.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to package outside the project release directory: $appImage"
}

if ([string]::IsNullOrWhiteSpace($JpackagePath)) {
    $jpackageCommand = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($null -ne $jpackageCommand) {
        $JpackagePath = $jpackageCommand.Source
    } elseif (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $JpackagePath = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    }
}
if ([string]::IsNullOrWhiteSpace($JpackagePath) -or -not (Test-Path -LiteralPath $JpackagePath)) {
    throw "jpackage.exe was not found. Install JDK 21 or pass -JpackagePath explicitly."
}

$versionMatch = Select-String -LiteralPath (Join-Path $projectRoot "build.gradle.kts") -Pattern '^\s*version\s*=\s*"([^"]+)"'
if ($null -eq $versionMatch -or $versionMatch.Matches.Count -eq 0) {
    throw "Could not read the application version from build.gradle.kts."
}
$appVersion = $versionMatch.Matches[0].Groups[1].Value
$distributionLib = Join-Path $projectRoot "build\install\poe-chat-helper\lib"
$mainJar = "poe-chat-helper-$appVersion.jar"
$iconPath = Join-Path $PSScriptRoot "macro-overlay.ico"
$readmePath = Join-Path $PSScriptRoot "README.txt"
$zipPath = Join-Path $releaseRoot "MacroOverlay-Windows-x64-$appVersion.zip"

Push-Location $projectRoot
try {
    & ".\gradlew.bat" --console=plain clean build installDist
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }

    New-Item -ItemType Directory -Force -Path $releaseRoot | Out-Null
    if (Test-Path -LiteralPath $appImage) {
        Remove-Item -LiteralPath $appImage -Recurse -Force
    }

    $jpackageArguments = @(
        "--type", "app-image",
        "--name", "MacroOverlay",
        "--app-version", $appVersion,
        "--vendor", "Macro Overlay",
        "--description", "Borderless ImGui macro overlay for Windows",
        "--input", $distributionLib,
        "--main-jar", $mainJar,
        "--main-class", "overlay.MainKt",
        "--dest", $releaseRoot,
        "--icon", $iconPath,
        "--add-modules", "java.base,java.desktop,java.logging,jdk.unsupported",
        "--java-options", "-Dfile.encoding=UTF-8"
    )
    & $JpackagePath @jpackageArguments
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE." }

    Copy-Item -LiteralPath $readmePath -Destination (Join-Path $appImage "README.txt") -Force
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -LiteralPath $appImage -DestinationPath $zipPath -CompressionLevel Optimal

    $hash = Get-FileHash -LiteralPath $zipPath -Algorithm SHA256
    Write-Host "Release: $zipPath"
    Write-Host "SHA256:  $($hash.Hash)"
} finally {
    Pop-Location
}
