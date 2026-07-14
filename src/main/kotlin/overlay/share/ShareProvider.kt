package overlay.share

import overlay.engine.model.MacroProfile

sealed interface ShareResult {
    data class Success(val url: String) : ShareResult
    data class Failure(val message: String) : ShareResult
}

/**
 * Upload/download a macro profile to a shareable location. [LocalStubShareProvider]
 * is the only implementation for now (writes locally, no real server) - swap
 * in an HTTP-backed implementation later against the Linux server without
 * touching any UI code (see SharePanel).
 */
interface ShareProvider {
    fun upload(profile: MacroProfile): ShareResult
    fun download(codeOrUrl: String): MacroProfile?
}
