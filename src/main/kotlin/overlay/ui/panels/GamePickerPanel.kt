package overlay.ui.panels

import com.sun.jna.platform.win32.WinDef
import imgui.ImGui
import overlay.win32.GamePicker
import overlay.win32.TargetWindowInfo

/** Searchable-sized target list contained within the main window's Target tab. */
class GamePickerPanel(private val ownHwnd: WinDef.HWND) {
    private var windows: List<TargetWindowInfo> = emptyList()
    private var pickerVisible = true
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

        ImGui.separatorText("Attached window")
        val target = selected
        if (target == null) {
            ImGui.textDisabled("No target attached")
            ImGui.textWrapped("Choose a window below. Once attached, macros only run while that window is in the foreground.")
        } else {
            ImGui.textWrapped(target.title)
            ImGui.textDisabled(target.processName)
            if (ImGui.button("Change target")) pickerVisible = true
            ImGui.sameLine()
            if (ImGui.button("Detach")) {
                selected = null
                pickerVisible = true
                changed = true
            }
        }

        if (pickerVisible) {
            ImGui.separatorText("Available windows")
            if (ImGui.button("Refresh list")) refresh()
            ImGui.sameLine()
            ImGui.textDisabled("${windows.size} found")

            ImGui.beginChild("target-window-list", 0f, 0f, true)
            for (window in windows) {
                val label = "${window.title}  [${window.processName}]"
                if (ImGui.selectable("$label##target-${window.hwnd}", selected?.hwnd == window.hwnd)) {
                    selected = window
                    pickerVisible = false
                    changed = true
                }
            }
            ImGui.endChild()
        }
        return changed
    }
}
