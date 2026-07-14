package overlay.settings

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val defaultSuppressOriginalInput: Boolean = false,
    val lastTargetWindowTitle: String? = null,
    val clickThroughEnabled: Boolean = true,
    val shareServerUrl: String = "",
    val disableMacrosWhenDetached: Boolean = true,
    val mainWindowVisible: Boolean = true,
    val macroWindowVisible: Boolean = true,
    val macroButtonPanelVisible: Boolean = true,
)
