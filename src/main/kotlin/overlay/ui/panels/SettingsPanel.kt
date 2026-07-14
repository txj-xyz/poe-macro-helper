package overlay.ui.panels

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImString
import overlay.settings.AppSettings
import overlay.window.ClipboardSupport

/** Every tweakable option lives here - no settings-file-only options. */
class SettingsPanel(
    private val getSettings: () -> AppSettings,
    private val onChange: (AppSettings) -> Unit,
) {
    private var shareUrlBuf: ImString? = null

    fun render() {
        var settings = getSettings()
        ImGui.text("Settings")

        val macroWindowVisible = ImBoolean(settings.macroWindowVisible)
        if (ImGui.checkbox("Show macro editor window", macroWindowVisible)) {
            settings = settings.copy(macroWindowVisible = macroWindowVisible.get())
            onChange(settings)
        }

        val buttonPanelVisible = ImBoolean(settings.macroButtonPanelVisible)
        if (ImGui.checkbox("Show manual macro button panel", buttonPanelVisible)) {
            settings = settings.copy(macroButtonPanelVisible = buttonPanelVisible.get())
            onChange(settings)
        }

        val mainWindowVisible = ImBoolean(settings.mainWindowVisible)
        if (ImGui.checkbox("Show main controls (F10 toggles)", mainWindowVisible)) {
            settings = settings.copy(mainWindowVisible = mainWindowVisible.get())
            onChange(settings)
        }

        val suppressDefault = ImBoolean(settings.defaultSuppressOriginalInput)
        if (ImGui.checkbox("Suppress original input by default for new macros", suppressDefault)) {
            settings = settings.copy(defaultSuppressOriginalInput = suppressDefault.get())
            onChange(settings)
        }

        val disableWhenDetached = ImBoolean(settings.disableMacrosWhenDetached)
        if (ImGui.checkbox("Disable macros when no target is attached", disableWhenDetached)) {
            settings = settings.copy(disableMacrosWhenDetached = disableWhenDetached.get())
            onChange(settings)
        }
        ImGui.textWrapped("When attached, macros run only while the selected target is the foreground window.")

        ImGui.textWrapped("Transparent space outside visible ImGui windows is always click-through.")

        ImGui.text("Share server URL (local-only until configured)")
        val urlBuf = shareUrlBuf ?: ImString(settings.shareServerUrl, 256).also { shareUrlBuf = it }
        ImGui.setNextItemWidth((ImGui.getContentRegionAvailX() - 58f).coerceAtLeast(100f))
        ImGui.inputText("##share-server-url", urlBuf)
        ImGui.sameLine()
        if (ImGui.button("Paste##share-server-url")) {
            urlBuf.set(ClipboardSupport.readText())
        }
        if (urlBuf.get() != settings.shareServerUrl) {
            settings = settings.copy(shareServerUrl = urlBuf.get())
            onChange(settings)
        }

        if (settings.lastTargetWindowTitle != null) {
            ImGui.text("Last attached window: ${settings.lastTargetWindowTitle}")
        }
    }
}
