package overlay.ui.panels

import com.sun.jna.platform.win32.WinDef
import imgui.ImGui
import imgui.flag.ImGuiCond
import overlay.win32.GamePicker
import overlay.win32.TargetWindowInfo

/** Lets the user refresh the list of visible top-level windows and pick one to attach the overlay to. */
class GamePickerPanel(private val ownHwnd: WinDef.HWND) {
    private var windows: List<TargetWindowInfo> = emptyList()
    private var requestedOpenState: Boolean? = true
    var selected: TargetWindowInfo? = null
        private set

    init {
        refresh()
    }

    private fun refresh() {
        windows = GamePicker.listWindows(ownHwnd)
    }

    /** Returns true if the selection changed this frame. */
    fun render(): Boolean {
        var changed = false

        selected?.let { target ->
            ImGui.textWrapped("Attached to: ${target.title} [${target.processName}]")
            ImGui.sameLine()
            if (ImGui.button("Change target")) requestedOpenState = true
            ImGui.sameLine()
            if (ImGui.button("Detach")) {
                selected = null
                requestedOpenState = true
                changed = true
            }
        }

        requestedOpenState?.let { open ->
            ImGui.setNextItemOpen(open, ImGuiCond.Always)
            requestedOpenState = null
        }

        if (ImGui.collapsingHeader("Target window picker")) {
            if (ImGui.button("Refresh windows")) refresh()
            for (window in windows) {
                val label = "${window.title}  [${window.processName}]"
                val isSelected = selected?.hwnd == window.hwnd
                if (ImGui.selectable(label, isSelected)) {
                    selected = window
                    requestedOpenState = false
                    changed = true
                }
            }
        }
        return changed
    }
}
