package overlay.engine.model

import kotlinx.serialization.Serializable

/** What fires a macro. Also used as the queued event type from the input hook to the engine. */
@Serializable
sealed interface TriggerSpec {
    @Serializable
    data class Keyboard(val combo: KeyCombo) : TriggerSpec

    @Serializable
    data class Mouse(val button: MouseButtonId, val modifiers: Set<ModifierKey> = emptySet()) : TriggerSpec
}
