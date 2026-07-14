package overlay.engine.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface MacroAction {
    @Serializable
    data class KeyPress(val combo: KeyCombo, val holdMs: Long = 40) : MacroAction

    @Serializable
    data class MouseClick(
        val button: MouseButtonId,
        val x: Int? = null,
        val y: Int? = null,
        val relativeToTargetWindow: Boolean = true,
    ) : MacroAction

    @Serializable
    data class MouseMove(
        val x: Int,
        val y: Int,
        val relativeToTargetWindow: Boolean = true,
    ) : MacroAction

    @Serializable
    data class MouseScroll(val ticks: Int) : MacroAction

    @Serializable
    data class ChatSend(
        val openChat: KeyCombo,
        val message: String,
        val channel: ChatChannel = ChatChannel.LOCAL,
        val submit: KeyCombo,
        val preDelayMs: Long = 50,
        val postTypeDelayMs: Long = 30,
    ) : MacroAction

    @Serializable
    data class Delay(val ms: Long) : MacroAction

    @Serializable
    data class Sequence(val steps: List<MacroAction>) : MacroAction
}
