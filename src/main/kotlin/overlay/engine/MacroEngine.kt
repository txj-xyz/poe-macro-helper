package overlay.engine

import overlay.engine.model.MacroProfile
import overlay.engine.model.TriggerSpec
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Consumes trigger events from a queue (fed by HookManager) on its own
 * thread and executes the matching macro's action. The GL thread is the sole
 * writer of the active profile (via [setProfile]); this thread only ever
 * reads a fully-formed snapshot through the AtomicReference, so no locking
 * is needed beyond that handoff.
 */
class MacroEngine(private val canExecute: () -> Boolean = { true }) {
    private sealed interface Request {
        data class Trigger(val trigger: TriggerSpec) : Request
        data class Manual(val macroId: String) : Request
    }

    private val profileRef = AtomicReference(MacroProfile(id = "default", name = "Default"))
    private val queue = LinkedBlockingQueue<Request>()

    @Volatile private var running = false

    fun setProfile(profile: MacroProfile) {
        profileRef.set(profile)
    }

    fun currentProfile(): MacroProfile = profileRef.get()

    /** Called from the hook thread - just enqueues, never executes inline. */
    fun submit(trigger: TriggerSpec): Boolean {
        if (!canExecute()) return false
        val macro = profileRef.get().macros.firstOrNull { it.enabled && it.trigger == trigger } ?: return false
        queue.put(Request.Trigger(trigger))
        return macro.suppressOriginalInput
    }

    /** Queue a macro from an on-screen button without blocking the render thread. */
    fun executeManually(macroId: String) {
        if (!canExecute()) return
        queue.put(Request.Manual(macroId))
    }

    fun start() {
        check(!running) { "MacroEngine already started" }
        running = true
        Thread(::runLoop, "macro-engine").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
    }

    private fun runLoop() {
        while (running) {
            val request = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (!canExecute()) continue
            val profile = profileRef.get()
            val macro = when (request) {
                is Request.Trigger -> profile.macros.firstOrNull { it.enabled && it.trigger == request.trigger }
                is Request.Manual -> profile.macros.firstOrNull { it.enabled && it.id == request.macroId }
            } ?: continue
            runCatching { ActionExecutor.execute(macro.action, canExecute) }
                .onFailure { it.printStackTrace() }
        }
    }
}
