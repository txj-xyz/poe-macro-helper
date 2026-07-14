package overlay.engine

import overlay.engine.model.TriggerSpec
import java.util.concurrent.atomic.AtomicReference

/**
 * Sits between HookManager (hook thread) and MacroEngine: normally forwards
 * every trigger straight to the engine, but while the binding editor is
 * "listening" for a new trigger (see TriggerCaptureWidget), the next trigger
 * is instead handed to that one-shot callback and never reaches the engine -
 * so capturing a bind doesn't also fire whatever macro used to own that key.
 */
class TriggerRouter(private val macroEngine: MacroEngine) {
    private val captureCallback = AtomicReference<((TriggerSpec) -> Unit)?>(null)

    fun route(trigger: TriggerSpec): Boolean {
        val capture = captureCallback.getAndSet(null)
        if (capture != null) {
            capture(trigger)
            return true
        } else {
            return macroEngine.submit(trigger)
        }
    }

    fun captureNext(callback: (TriggerSpec) -> Unit) {
        captureCallback.set(callback)
    }

    fun isCapturing(): Boolean = captureCallback.get() != null

    fun cancelCapture() {
        captureCallback.set(null)
    }
}
