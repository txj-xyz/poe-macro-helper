param(
    [ValidateSet("exe", "msi")]
    [string]$PackageType = "exe",
    [string]$JpackagePath,
    [switch]$SkipPortableBuild
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$releaseRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "build\release"))
$installerRoot = [System.IO.Path]::GetFullPath((Join-Path $releaseRoot "installers"))
$appImage = [System.IO.Path]::GetFullPath((Join-Path $releaseRoot "MacroOverlay"))
$expectedPrefix = $releaseRoot.TrimEnd('\') + '\'
foreach ($path in @($installerRoot, $appImage)) {
    if (-not $path.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to package outside the project release directory: $path"
    }
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

$candle = Get-Command candle.exe -ErrorAction SilentlyContinue
$light = Get-Command light.exe -ErrorAction SilentlyContinue
if ($null -eq $candle -or $null -eq $light) {
    throw "WiX Toolset 3.x is required. Add candle.exe and light.exe to PATH, then retry."
}

$versionMatch = Select-String -LiteralPath (Join-Path $projectRoot "build.gradle.kts") -Pattern '^\s*version\s*=\s*"([^"]+)"'
if ($null -eq $versionMatch -or $versionMatch.Matches.Count -eq 0) {
    throw "Could not read the application version from build.gradle.kts."
}
$appVersion = $versionMatch.Matches[0].Groups[1].Value
$iconPath = Join-Path $PSScriptRoot "macro-overlay.ico"
$portableBuildScript = Join-Path $PSScriptRoot "build-windows-release.ps1"
$expectedInstaller = Join-Path $installerRoot "MacroOverlay-$appVersion.$PackageType"

if (-not $SkipPortableBuild) {
    & $portableBuildScript -JpackagePath $JpackagePath
}
if (-not (Test-Path -LiteralPath $appImage)) {
    throw "Portable application image was not found: $appImage. Build it first or omit -SkipPortableBuild."
}

New-Item -ItemType Directory -Force -Path $installerRoot | Out-Null
if (Test-Path -LiteralPath $expectedInstaller) {
    Remove-Item -LiteralPath $expectedInstaller -Force
}

$jpackageArguments = @(
    "--type", $PackageType,
    "--name", "MacroOverlay",
    "--app-version", $appVersion,
    "--vendor", "Macro Overlay",
    "--description", "Borderless ImGui macro overlay for Windows",
    "--app-image", $appImage,
    "--dest", $installerRoot,
    "--icon", $iconPath,
    "--win-per-user-install",
    "--win-menu",
    "--win-menu-group", "Macro Overlay",
    "--win-shortcut",
    "--win-upgrade-uuid", "919c4260-ae22-4052-b26d-5c215d1d9121"
)
if ($PackageType -eq "exe") {
    $jpackageArguments += "--win-dir-chooser"
}

& $JpackagePath @jpackageArguments
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE." }

$installer = Get-Item -LiteralPath $expectedInstaller -ErrorAction SilentlyContinue
if ($null -eq $installer) {
    $installer = Get-ChildItem -LiteralPath $installerRoot -Filter "*.$PackageType" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}
if ($null -eq $installer) {
    throw "jpackage completed but no $PackageType installer was found in $installerRoot."
}

$hash = Get-FileHash -LiteralPath $installer.FullName -Algorithm SHA256
Write-Host "Installer: $($installer.FullName)"
Write-Host "SHA256:   $($hash.Hash)"
