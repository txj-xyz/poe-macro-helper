package overlay.settings

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object SettingsStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun load(): AppSettings {
        val file = AppPaths.settingsFile
        if (!file.exists()) return AppSettings()
        return runCatching { json.decodeFromString<AppSettings>(file.readText()) }.getOrDefault(AppSettings())
    }

    fun save(settings: AppSettings) {
        AppPaths.settingsFile.writeText(json.encodeToString(AppSettings.serializer(), settings))
    }
}
