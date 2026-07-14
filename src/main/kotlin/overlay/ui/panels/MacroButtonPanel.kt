package overlay.ui.panels

import imgui.ImGui
import overlay.engine.MacroEngine

/** Compact in-game launcher for manually executing enabled macros. */
class MacroButtonPanel(private val macroEngine: MacroEngine) {
    fun render() {
        val macros = macroEngine.currentProfile().macros.filter { it.enabled && it.showInButtonPanel }
        if (macros.isEmpty()) {
            ImGui.textDisabled("No macros enabled for this panel")
            return
        }

        for (macro in macros) {
            if (ImGui.button("${macro.name}##manual-${macro.id}", 220f, 0f)) {
                macroEngine.executeManually(macro.id)
            }
        }
    }
}
