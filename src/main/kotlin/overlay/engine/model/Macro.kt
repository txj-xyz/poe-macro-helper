package overlay.engine.model

import kotlinx.serialization.Serializable

@Serializable
data class Macro(
    val id: String,
    val name: String,
    val trigger: TriggerSpec,
    val action: MacroAction,
    val suppressOriginalInput: Boolean = false,
    val enabled: Boolean = true,
    val showInButtonPanel: Boolean = true,
)

@Serializable
data class MacroProfile(
    val id: String,
    val name: String,
    val macros: List<Macro> = emptyList(),
    val targetWindowHint: String? = null,
)
