package overlay.share

import overlay.engine.model.MacroProfile
import overlay.settings.AppPaths
import overlay.settings.ProfileStore
import java.io.File
import java.util.UUID

private const val LOCAL_SCHEME = "local://"

/**
 * Local-only placeholder: writes the profile to a file under
 * %APPDATA%\MacroOverlay\shared\ keyed by a generated code, and "downloads"
 * by reading it back from the same folder. Nothing leaves this machine -
 * SharePanel labels this clearly. Swap for an HTTP-backed ShareProvider once
 * the Linux server details are available; SharePanel doesn't need to change.
 */
class LocalStubShareProvider : ShareProvider {
    override fun upload(profile: MacroProfile): ShareResult {
        return try {
            val code = UUID.randomUUID().toString()
            File(AppPaths.sharedDir, "$code.json").writeText(ProfileStore.encode(profile))
            ShareResult.Success("$LOCAL_SCHEME$code")
        } catch (e: Exception) {
            ShareResult.Failure(e.message ?: "Unknown error")
        }
    }

    override fun download(codeOrUrl: String): MacroProfile? {
        val code = codeOrUrl.trim().removePrefix(LOCAL_SCHEME)
        val file = File(AppPaths.sharedDir, "$code.json")
        if (!file.exists()) return null
        return runCatching { ProfileStore.decode(file.readText()) }.getOrNull()
    }
}
