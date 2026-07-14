package overlay.settings

import kotlinx.serialization.Serializable
import overlay.engine.model.KeyCombo

@Serializable
data class AppSettings(
    val defaultSuppressOriginalInput: Boolean = false,
    val lastTargetWindowTitle: String? = null,
    val clickThroughEnabled: Boolean = true,
    val disableMacrosWhenDetached: Boolean = true,
    val mainWindowToggle: KeyCombo = KeyCombo(0x79),
    val mainWindowVisible: Boolean = true,
    val macroWindowVisible: Boolean = true,
    val macroButtonPanelVisible: Boolean = true,
)
