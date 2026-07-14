package overlay.ui.panels

import imgui.ImGui
import imgui.type.ImBoolean
import overlay.settings.AppSettings

/** Macro and overlay preferences; window management lives in WindowControlsPanel. */
class SettingsPanel(
    private val getSettings: () -> AppSettings,
    private val onChange: (AppSettings) -> Unit,
) {
    fun render() {
        var settings = getSettings()

        ImGui.separatorText("Macro behavior")
        val suppressDefault = ImBoolean(settings.defaultSuppressOriginalInput)
        if (ImGui.checkbox("Suppress trigger by default for new macros", suppressDefault)) {
            settings = settings.copy(defaultSuppressOriginalInput = suppressDefault.get())
            onChange(settings)
        }
        ImGui.textWrapped("Individual macros can override this from their Setup tab.")

        val disableWhenDetached = ImBoolean(settings.disableMacrosWhenDetached)
        if (ImGui.checkbox("Disable macros while detached", disableWhenDetached)) {
            settings = settings.copy(disableMacrosWhenDetached = disableWhenDetached.get())
            onChange(settings)
        }
        ImGui.textWrapped("Attached macros only run while the selected target is the foreground window.")

        ImGui.separatorText("Overlay behavior")
        ImGui.textWrapped("Transparent space is click-through. Panel movement temporarily uses the full transparent backing surface so windows remain smooth and unclipped.")

        settings.lastTargetWindowTitle?.let {
            ImGui.separatorText("Last target")
            ImGui.textWrapped(it)
        }
    }
}
