package overlay.settings

import java.io.File

object AppPaths {
    val appDataDir: File by lazy {
        File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MacroOverlay").apply { mkdirs() }
    }

    val settingsFile: File get() = File(appDataDir, "settings.json")

    val profilesDir: File by lazy { File(appDataDir, "profiles").apply { mkdirs() } }

    val sharedDir: File by lazy { File(appDataDir, "shared").apply { mkdirs() } }
}
