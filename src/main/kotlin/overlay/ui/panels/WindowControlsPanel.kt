package overlay.ui.panels

import imgui.ImGui
import imgui.type.ImBoolean
import overlay.engine.TriggerRouter
import overlay.engine.model.KeyCombo
import overlay.engine.model.TriggerSpec
import overlay.settings.AppSettings
import overlay.ui.VkNames
import overlay.ui.widgets.TriggerCaptureWidget

private const val DEFAULT_MAIN_TOGGLE_VK = 0x79 // F10

/** Central visibility, layout, and recovery controls for every ImGui window. */
class WindowControlsPanel(
    private val triggerRouter: TriggerRouter,
    private val getSettings: () -> AppSettings,
    private val onChange: (AppSettings) -> Unit,
    private val onResetLayout: () -> Unit,
) {
    private val toggleCapture = TriggerCaptureWidget(triggerRouter)
    private var statusMessage = ""

    fun render() {
        var settings = getSettings()

        ImGui.separatorText("Visibility")
        val mainVisible = ImBoolean(settings.mainWindowVisible)
        ImGui.beginDisabled(settings.mainWindowToggle.vk == 0)
        if (ImGui.checkbox("Main controls", mainVisible)) {
            settings = settings.copy(mainWindowVisible = mainVisible.get())
            onChange(settings)
        }
        ImGui.endDisabled()

        val editorVisible = ImBoolean(settings.macroWindowVisible)
        if (ImGui.checkbox("Macro Editor", editorVisible)) {
            settings = settings.copy(macroWindowVisible = editorVisible.get())
            onChange(settings)
        }

        val buttonsVisible = ImBoolean(settings.macroButtonPanelVisible)
        if (ImGui.checkbox("Macro Buttons", buttonsVisible)) {
            settings = settings.copy(macroButtonPanelVisible = buttonsVisible.get())
            onChange(settings)
        }

        if (ImGui.button("Show all windows")) {
            settings = settings.copy(
                mainWindowVisible = true,
                macroWindowVisible = true,
                macroButtonPanelVisible = true,
            )
            onChange(settings)
        }
        ImGui.sameLine()
        if (ImGui.button("Hide auxiliary windows")) {
            settings = settings.copy(macroWindowVisible = false, macroButtonPanelVisible = false)
            onChange(settings)
        }

        ImGui.separatorText("Layout")
        if (ImGui.button("Reset window positions and sizes")) {
            onResetLayout()
            statusMessage = "Window layout reset."
        }
        ImGui.textWrapped("Each auxiliary window can also be closed from its title-bar X and reopened here or from the Windows menu.")

        ImGui.separatorText("Main controls hotkey")
        ImGui.text("Current: ${VkNames.describe(TriggerSpec.Keyboard(settings.mainWindowToggle))}")
        toggleCapture.render("main-window-toggle", TriggerSpec.Keyboard(settings.mainWindowToggle))?.let { captured ->
            if (captured is TriggerSpec.Keyboard) {
                settings = settings.copy(mainWindowToggle = captured.combo)
                onChange(settings)
                statusMessage = if (captured.combo.vk == 0) {
                    "Assign a hotkey before hiding the main controls."
                } else {
                    "Main controls hotkey updated."
                }
            } else {
                statusMessage = "The main controls toggle must be a keyboard input."
            }
        }
        if (ImGui.button("Reset hotkey to F10")) {
            onChange(settings.copy(mainWindowToggle = KeyCombo(DEFAULT_MAIN_TOGGLE_VK)))
            statusMessage = "Main controls hotkey reset to F10."
        }
        ImGui.textWrapped("This recovery hotkey remains active globally even while the main controls are hidden.")

        if (statusMessage.isNotEmpty()) {
            ImGui.separator()
            ImGui.textWrapped(statusMessage)
        }
    }
}
