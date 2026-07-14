package overlay.ui

import overlay.engine.model.KeyCombo
import overlay.engine.model.ModifierKey
import overlay.engine.model.MouseButtonId
import overlay.engine.model.TriggerSpec

/** Human-readable names for Win32 virtual-key codes and trigger/action display. */
object VkNames {
    private val named: Map<Int, String> = buildMap {
        put(0x08, "Backspace"); put(0x09, "Tab"); put(0x0D, "Enter"); put(0x1B, "Esc")
        put(0x20, "Space"); put(0x2E, "Delete"); put(0x2D, "Insert")
        put(0x24, "Home"); put(0x23, "End"); put(0x21, "Page Up"); put(0x22, "Page Down")
        put(0x25, "Left"); put(0x26, "Up"); put(0x27, "Right"); put(0x28, "Down")
        put(0x14, "Caps Lock"); put(0x90, "Num Lock"); put(0x91, "Scroll Lock")
        for (n in 0..9) put(0x30 + n, ('0' + n).toString())
        for (c in 'A'..'Z') put(0x41 + (c - 'A'), c.toString())
        for (n in 0..9) put(0x60 + n, "Numpad $n")
        for (n in 1..24) put(0x70 + (n - 1), "F$n")
    }

    fun vkName(vk: Int): String = if (vk == 0) "Unbound" else named[vk] ?: "VK 0x${vk.toString(16).uppercase()}"

    fun modifierName(modifier: ModifierKey): String = when (modifier) {
        ModifierKey.CTRL -> "Ctrl"
        ModifierKey.SHIFT -> "Shift"
        ModifierKey.ALT -> "Alt"
        ModifierKey.WIN -> "Win"
    }

    fun describe(combo: KeyCombo): String {
        val modifiers = combo.modifiers.sortedBy { it.ordinal }.joinToString("+") { modifierName(it) }
        val key = vkName(combo.vk)
        return if (modifiers.isEmpty()) key else "$modifiers+$key"
    }

    fun describe(button: MouseButtonId): String = when (button) {
        MouseButtonId.LEFT -> "Mouse Left"
        MouseButtonId.RIGHT -> "Mouse Right"
        MouseButtonId.MIDDLE -> "Mouse Middle"
        MouseButtonId.X1 -> "Mouse X1"
        MouseButtonId.X2 -> "Mouse X2"
        MouseButtonId.WHEEL_UP -> "Wheel Up"
        MouseButtonId.WHEEL_DOWN -> "Wheel Down"
    }

    fun describe(trigger: TriggerSpec): String = when (trigger) {
        is TriggerSpec.Keyboard -> describe(trigger.combo)
        is TriggerSpec.Mouse -> {
            val modifiers = trigger.modifiers.sortedBy { it.ordinal }.joinToString("+") { modifierName(it) }
            val button = describe(trigger.button)
            if (modifiers.isEmpty()) button else "$modifiers+$button"
        }
    }
}
