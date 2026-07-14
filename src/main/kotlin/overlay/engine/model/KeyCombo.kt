package overlay.engine.model

import kotlinx.serialization.Serializable

/** [vk] is a Win32 virtual-key code (see WinUser VK_* constants). */
@Serializable
data class KeyCombo(val vk: Int, val modifiers: Set<ModifierKey> = emptySet())
