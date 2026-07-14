package overlay.window

import imgui.ImGui
import imgui.callback.ImStrConsumer
import imgui.callback.ImStrSupplier
import imgui.flag.ImGuiKey
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import overlay.engine.model.ModifierKey
import overlay.engine.model.MouseButtonId
import overlay.win32.GlobalKeyEvent
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Wraps the imgui-java context + GLFW/GL3 backends. installCallbacks=true
 * (GLFW-driven IO) is fine while the window behaves like a normal window;
 * phase 3's click-through mode stops receiving GLFW mouse callbacks entirely
 * and will feed ImGui's IO manually instead (see TargetWindowTracker plan).
 */
class ImGuiLayer(private val windowHandle: Long) {
    private val implGlfw = ImGuiImplGlfw()
    private val implGl3 = ImGuiImplGl3()
    private val keyboardEvents = ConcurrentLinkedQueue<GlobalKeyEvent>()
    private val mouseButtonEvents = ConcurrentLinkedQueue<Pair<Int, Boolean>>()
    private val clipboardReader = object : ImStrSupplier() {
        override fun get(): String = ClipboardSupport.readText()
    }
    private val clipboardWriter = object : ImStrConsumer() {
        override fun accept(value: String) = ClipboardSupport.writeText(value)
    }

    fun init() {
        ImGui.createContext()
        ImGui.getIO().setIniFilename(null)
        implGlfw.init(windowHandle, true)
        // Override the GLFW callbacks with direct Windows/AWT clipboard access.
        // Keeping these callback objects as fields is required; native ImGui may
        // invoke them long after init and a collected callback would crash.
        ImGui.getIO().setGetClipboardTextFn(clipboardReader)
        ImGui.getIO().setSetClipboardTextFn(clipboardWriter)
        implGl3.init("#version 330 core")
    }

    fun newFrame(mouseX: Float, mouseY: Float) {
        implGlfw.newFrame()
        // ImGuiImplGl3.newFrame() lazily builds GL device objects (incl. the
        // font atlas texture) on first call - must run before ImGui.newFrame(),
        // or ImGui asserts "Font Atlas not built!".
        implGl3.newFrame()
        // The native overlay deliberately does not activate. GLFW may therefore
        // retain a stale cursor position after the pointer leaves an ImGui
        // window. Apply globally-polled Win32 coordinates after the backend
        // update so transparent hit testing always sees the real pointer.
        ImGui.getIO().setMousePos(mouseX, mouseY)
        drainMouseButtonEvents()
        drainKeyboardEvents()
        ImGui.newFrame()
    }

    fun queueKeyboardEvent(event: GlobalKeyEvent) {
        keyboardEvents.add(event)
    }

    fun queueMouseButtonEvent(button: MouseButtonId, isDown: Boolean) {
        val imguiButton = when (button) {
            MouseButtonId.LEFT -> 0
            MouseButtonId.RIGHT -> 1
            MouseButtonId.MIDDLE -> 2
            MouseButtonId.X1 -> 3
            MouseButtonId.X2 -> 4
            MouseButtonId.WHEEL_UP, MouseButtonId.WHEEL_DOWN -> return
        }
        mouseButtonEvents.add(imguiButton to isDown)
    }

    private fun drainMouseButtonEvents() {
        val io = ImGui.getIO()
        while (true) {
            val (button, isDown) = mouseButtonEvents.poll() ?: break
            io.addMouseButtonEvent(button, isDown)
        }
    }

    private fun drainKeyboardEvents() {
        val io = ImGui.getIO()
        while (true) {
            val event = keyboardEvents.poll() ?: break
            // ImGui's shortcut handling uses the aggregate modifier aliases,
            // not only the physical Left/Right key events. The global hook
            // already gives us its current modifier state for every event.
            io.addKeyEvent(ImGuiKey.ModCtrl, ModifierKey.CTRL in event.modifiers)
            io.addKeyEvent(ImGuiKey.ModShift, ModifierKey.SHIFT in event.modifiers)
            io.addKeyEvent(ImGuiKey.ModAlt, ModifierKey.ALT in event.modifiers)
            io.addKeyEvent(ImGuiKey.ModSuper, ModifierKey.WIN in event.modifiers)
            val key = imguiKeyForVk(event.vkCode)
            if (key != ImGuiKey.None) io.addKeyEvent(key, event.isDown)
            if (event.isDown && !event.text.isNullOrEmpty()) io.addInputCharactersUTF8(event.text)
        }
    }

    private fun imguiKeyForVk(vk: Int): Int = when (vk) {
        0x08 -> ImGuiKey.Backspace
        0x09 -> ImGuiKey.Tab
        0x0D -> ImGuiKey.Enter
        0x10, 0xA0 -> ImGuiKey.LeftShift
        0xA1 -> ImGuiKey.RightShift
        0x11, 0xA2 -> ImGuiKey.LeftCtrl
        0xA3 -> ImGuiKey.RightCtrl
        0x12, 0xA4 -> ImGuiKey.LeftAlt
        0xA5 -> ImGuiKey.RightAlt
        0x1B -> ImGuiKey.Escape
        0x20 -> ImGuiKey.Space
        0x21 -> ImGuiKey.PageUp
        0x22 -> ImGuiKey.PageDown
        0x23 -> ImGuiKey.End
        0x24 -> ImGuiKey.Home
        0x25 -> ImGuiKey.LeftArrow
        0x26 -> ImGuiKey.UpArrow
        0x27 -> ImGuiKey.RightArrow
        0x28 -> ImGuiKey.DownArrow
        0x2D -> ImGuiKey.Insert
        0x2E -> ImGuiKey.Delete
        in 0x30..0x39 -> ImGuiKey._0 + (vk - 0x30)
        in 0x41..0x5A -> ImGuiKey.A + (vk - 0x41)
        0x5B -> ImGuiKey.LeftSuper
        0x5C -> ImGuiKey.RightSuper
        in 0x60..0x69 -> ImGuiKey.Keypad0 + (vk - 0x60)
        0x6A -> ImGuiKey.KeypadMultiply
        0x6B -> ImGuiKey.KeypadAdd
        0x6D -> ImGuiKey.KeypadSubtract
        0x6E -> ImGuiKey.KeypadDecimal
        0x6F -> ImGuiKey.KeypadDivide
        in 0x70..0x7B -> ImGuiKey.F1 + (vk - 0x70)
        0x90 -> ImGuiKey.NumLock
        0x91 -> ImGuiKey.ScrollLock
        0xBA -> ImGuiKey.Semicolon
        0xBB -> ImGuiKey.Equal
        0xBC -> ImGuiKey.Comma
        0xBD -> ImGuiKey.Minus
        0xBE -> ImGuiKey.Period
        0xBF -> ImGuiKey.Slash
        0xC0 -> ImGuiKey.GraveAccent
        0xDB -> ImGuiKey.LeftBracket
        0xDC -> ImGuiKey.Backslash
        0xDD -> ImGuiKey.RightBracket
        0xDE -> ImGuiKey.Apostrophe
        else -> ImGuiKey.None
    }

    /** Finalizes ImGui's draw data for this frame. Call after building the UI. */
    fun finishFrame() {
        ImGui.render()
    }

    /** Submits the finalized draw data to GL. Call after clearing the framebuffer. */
    fun renderDrawData() {
        implGl3.renderDrawData(ImGui.getDrawData())
    }

    fun dispose() {
        implGl3.shutdown()
        implGlfw.shutdown()
        ImGui.destroyContext()
    }
}
