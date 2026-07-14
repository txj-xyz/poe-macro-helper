package overlay.settings

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import overlay.engine.model.MacroProfile
import java.io.File

object ProfileStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private fun fileFor(profileId: String) = File(AppPaths.profilesDir, "$profileId.json")

    fun save(profile: MacroProfile) {
        fileFor(profile.id).writeText(json.encodeToString(MacroProfile.serializer(), profile))
    }

    fun load(profileId: String): MacroProfile? {
        val file = fileFor(profileId)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<MacroProfile>(file.readText()) }.getOrNull()
    }

    fun listProfileIds(): List<String> =
        AppPaths.profilesDir.listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun encode(profile: MacroProfile): String = json.encodeToString(MacroProfile.serializer(), profile)

    fun decode(text: String): MacroProfile = json.decodeFromString<MacroProfile>(text)
}
