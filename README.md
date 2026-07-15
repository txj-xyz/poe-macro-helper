# Macro Overlay

<img src="src/main/resources/icons/macro-overlay.png" alt="Macro Overlay icon" width="128">

Macro Overlay is a Windows-only Kotlin and ImGui application for creating
keyboard, mouse, and chat macros that can be attached to a selected game or
desktop window. The overlay stays inside the selected window, supports
click-through transparent areas, and remains available from the Windows
notification area.

## Features

- Key presses, key combinations, function keys, and mouse actions
- Chat messages with Local, Global, Trade, and Party channel prefixes
- Target-window picker and target-only macro execution
- Clickable in-game macro button panel
- Import and export of macro profiles
- Per-user persistent settings and profiles
- Configurable recovery hotkey for the main controls
- Windows tray icon with **Show Main Controls** and **Exit** actions
- Transparent custom icon in debug and packaged builds

## Requirements

- Windows 10 or Windows 11, x64
- JDK 21 for development and packaging
- PowerShell 5.1 or newer
- No separate Gradle installation; the repository includes the Gradle wrapper

Creating an installable `.exe` or `.msi` also requires WiX Toolset 3.x. The
JDK 21 `jpackage` tool looks for `candle.exe` and `light.exe` on `PATH`.

## Run in debug mode

From the project root:

```powershell
.\gradlew.bat --console=plain run
```

The debug launch uses the same transparent artwork for the tray and native
GLFW window icons as the packaged application.

## Build and test

```powershell
.\gradlew.bat --console=plain clean build
```

The JVM artifacts are written under `build\libs` and `build\distributions`.
The JAR is not a standalone Windows executable; it requires its dependency
JARs and a compatible Java runtime.

## Build the portable Windows application

The portable build contains `MacroOverlay.exe`, all dependency JARs, and a
trimmed Java runtime. Friends do not need Java installed, but the executable
must remain beside its `app` and `runtime` directories.

If JDK 21's `jpackage.exe` is on `PATH` or `JAVA_HOME` points to JDK 21:

```powershell
.\packaging\build-windows-release.ps1
```

You can also pass the executable explicitly:

```powershell
.\packaging\build-windows-release.ps1 `
  -JpackagePath "$env:USERPROFILE\.jdks\temurin-21.0.11\bin\jpackage.exe"
```

Outputs:

- `build\release\MacroOverlay\MacroOverlay.exe` — unpacked portable app
- `build\release\MacroOverlay-Windows-x64-<version>.zip` — file to share

Share the ZIP, not `MacroOverlay.exe` by itself. The script also prints the
ZIP's SHA-256 checksum.

## Build a Windows installer

First install WiX Toolset 3.x and add its `bin` directory to the system or
current PowerShell `PATH`. Confirm both tools are visible:

```powershell
candle.exe -?
light.exe -?
```

Build an interactive Windows installer:

```powershell
.\packaging\build-windows-installer.ps1 -PackageType exe
```

Or build an MSI:

```powershell
.\packaging\build-windows-installer.ps1 -PackageType msi
```

Pass `-JpackagePath` in the same way as the portable script when `jpackage`
is not discoverable automatically. Installers are written to
`build\release\installers` and include Start menu and desktop shortcuts.

The generated packages are unsigned. Windows SmartScreen may warn users until
the installer and launcher are signed with a trusted code-signing certificate.

## GitHub Actions releases

The `Windows Build and Release` workflow in
`.github\workflows\windows-release.yml` builds and tests the project on a
GitHub-hosted Windows runner. Every run uploads these workflow artifacts:

- A portable Windows ZIP
- A single-file Windows `.exe` installer
- A `SHA256SUMS.txt` checksum file

Pull requests and pushes build the artifacts without publishing a GitHub
Release. A pushed version tag publishes the same files on the repository's
Releases page.

To publish version `1.0.1`:

1. Set `version = "1.0.1"` in `build.gradle.kts` and commit the change.
2. Create and push a matching tag:

```powershell
git tag v1.0.1
git push origin v1.0.1
```

The workflow rejects a tag when it does not match the Gradle version. You can
also run it manually from **Actions > Windows Build and Release > Run
workflow**. Leave `release_tag` empty to create workflow artifacts only, or
enter an existing matching tag such as `v1.0.1` to publish a GitHub Release.

## Versioning releases

Update `version` in `build.gradle.kts` before creating a new release:

```kotlin
version = "1.0.1"
```

Do not change the upgrade UUID in the installer script. Keeping it stable lets
Windows recognize future versions as upgrades of the same application.

## User data

Settings and profiles are stored outside the installation directory:

```text
%APPDATA%\MacroOverlay\settings.json
%APPDATA%\MacroOverlay\profiles\
```

Uninstalling or replacing the application does not automatically remove these
per-user files.
