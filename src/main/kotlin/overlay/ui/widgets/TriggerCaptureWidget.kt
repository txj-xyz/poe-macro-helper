package overlay.ui.widgets

import imgui.ImGui
import overlay.engine.TriggerRouter
import overlay.engine.model.TriggerSpec
import overlay.ui.VkNames
import java.util.concurrent.atomic.AtomicReference

/**
 * A "click to bind" button: while listening, the *next* global key/mouse
 * event (captured via [TriggerRouter], on the hook thread) is grabbed here
 * instead of reaching the macro engine, so binding a key doesn't also fire
 * whatever macro currently owns it.
 */
class TriggerCaptureWidget(private val triggerRouter: TriggerRouter) {
    private val captured = AtomicReference<TriggerSpec?>(null)
    private var listening = false

    /** Renders the button; returns a newly captured trigger the frame it arrives, else null. */
    fun render(widgetId: String, current: TriggerSpec): TriggerSpec? {
        val label = if (listening) "Press a key or click..." else VkNames.describe(current)
        if (ImGui.button("$label##$widgetId")) {
            if (listening) {
                listening = false
                triggerRouter.cancelCapture()
            } else {
                listening = true
                captured.set(null)
                triggerRouter.captureNext { captured.set(it) }
            }
        }
        ImGui.sameLine()
        if (ImGui.button("Clear##clear-$widgetId")) {
            if (listening) triggerRouter.cancelCapture()
            listening = false
            captured.set(null)
            return TriggerSpec.Keyboard(overlay.engine.model.KeyCombo(0))
        }
        val result = captured.getAndSet(null)
        if (result != null) {
            listening = false
        }
        return result
    }
}
