package overlay.win32

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import overlay.engine.model.KeyCombo
import overlay.engine.model.ModifierKey
import overlay.engine.model.MouseButtonId
import overlay.engine.model.TriggerSpec

/**
 * Global low-level keyboard/mouse hooks on a dedicated thread with its own
 * message pump - WH_KEYBOARD_LL/WH_MOUSE_LL only fire on a thread that pumps
 * messages, and this must not be the GLFW/GL thread (GLFW is single-thread
 * only). [onTrigger] must return fast: Windows silently unhooks a callback
 * that blocks too long (~300ms), so no macro *execution* happens here - it
 * only detects trigger events and hands them off.
 */
data class GlobalKeyEvent(
    val vkCode: Int,
    val scanCode: Int,
    val isDown: Boolean,
    val text: String? = null,
)

class HookManager(
    private val onTrigger: (TriggerSpec) -> Boolean,
    private val onKeyboardInput: (GlobalKeyEvent) -> Boolean = { false },
    private val onMouseInput: (MouseButtonId, Boolean) -> Boolean = { _, _ -> true },
) {
    private val user32 = Win32Api.INSTANCE
    private val heldModifiers = mutableSetOf<ModifierKey>()
    private val heldKeys = mutableSetOf<Int>()
    private val suppressedKeys = mutableSetOf<Int>()

    private var keyboardHook: WinUser.HHOOK? = null
    private var mouseHook: WinUser.HHOOK? = null

    @Volatile private var running = false

    @Volatile private var threadId: Int? = null

    // Held as durable vals: a still-registered native callback that gets
    // GC'd while Windows can still invoke it will crash the JVM.
    private val keyboardProc = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
        val suppress = nCode == Win32Api.HC_ACTION && handleKeyboardEvent(wParam.toInt(), info.vkCode, info.scanCode)
        if (suppress) {
            WinDef.LRESULT(1)
        } else {
            user32.CallNextHookEx(keyboardHook, nCode, wParam, WinDef.LPARAM(Pointer.nativeValue(info.pointer)))
        }
    }

    private val mouseProc = WinUser.LowLevelMouseProc { nCode, wParam, info ->
        val suppress = nCode == Win32Api.HC_ACTION && handleMouseEvent(wParam.toInt(), info.mouseData)
        if (suppress) {
            WinDef.LRESULT(1)
        } else {
            user32.CallNextHookEx(mouseHook, nCode, wParam, WinDef.LPARAM(Pointer.nativeValue(info.pointer)))
        }
    }

    fun start() {
        check(!running) { "HookManager already started" }
        running = true
        Thread(::runLoop, "macro-input-hook").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        threadId?.let { user32.PostThreadMessage(it, WinUser.WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0)) }
    }

    private fun runLoop() {
        threadId = Kernel32.INSTANCE.GetCurrentThreadId()
        keyboardHook = user32.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardProc, null, 0)
        mouseHook = user32.SetWindowsHookEx(WinUser.WH_MOUSE_LL, mouseProc, null, 0)
        try {
            val msg = WinUser.MSG()
            while (running) {
                val result = user32.GetMessage(msg, null, 0, 0)
                if (result <= 0) break
                user32.TranslateMessage(msg)
                user32.DispatchMessage(msg)
            }
        } finally {
            keyboardHook?.let { user32.UnhookWindowsHookEx(it) }
            mouseHook?.let { user32.UnhookWindowsHookEx(it) }
        }
    }

    private fun handleKeyboardEvent(message: Int, vkCode: Int, scanCode: Int): Boolean {
        val isDown = message == WinUser.WM_KEYDOWN || message == WinUser.WM_SYSKEYDOWN
        val isUp = message == WinUser.WM_KEYUP || message == WinUser.WM_SYSKEYUP
        if (!isDown && !isUp) return false

        val modifier = vkToModifier(vkCode)
        if (modifier != null) {
            when {
                isDown -> heldModifiers.add(modifier)
                isUp -> heldModifiers.remove(modifier)
            }
        }

        val firstDown = isDown && heldKeys.add(vkCode)
        val keyboardConsumed = onKeyboardInput(
            GlobalKeyEvent(
                vkCode = vkCode,
                scanCode = scanCode,
                isDown = isDown,
                text = if (isDown) translateText(vkCode, scanCode) else null,
            ),
        )

        if (isUp) {
            heldKeys.remove(vkCode)
            return suppressedKeys.remove(vkCode) || keyboardConsumed
        }

        if (keyboardConsumed) {
            suppressedKeys.add(vkCode)
            return true
        }

        if (modifier != null) return false
        if (isDown) {
            if (!firstDown) return vkCode in suppressedKeys
            val suppress = onTrigger(TriggerSpec.Keyboard(KeyCombo(vkCode, heldModifiers.toSet())))
            if (suppress) suppressedKeys.add(vkCode)
            return suppress
        }
        return false
    }

    /** Translate with the active Windows keyboard layout, including Shift/Caps and punctuation. */
    private fun translateText(vkCode: Int, scanCode: Int): String? {
        if (ModifierKey.CTRL in heldModifiers || ModifierKey.ALT in heldModifiers || ModifierKey.WIN in heldModifiers) {
            return null
        }
        val state = ByteArray(256)
        if (!user32.GetKeyboardState(state)) return null
        state[vkCode] = (state[vkCode].toInt() or 0x80).toByte()
        // Low-level hooks run before the foreground queue updates its key
        // state. Mirror our hook-tracked Shift state so OEM keys translate
        // correctly (for example Shift + '=' becomes '+').
        if (ModifierKey.SHIFT in heldModifiers) {
            state[WinUser.VK_SHIFT] = 0x80.toByte()
        }
        val chars = CharArray(8)
        val count = user32.ToUnicodeEx(
            vkCode,
            scanCode,
            state,
            chars,
            chars.size,
            0,
            user32.GetKeyboardLayout(0),
        )
        return if (count > 0) String(chars, 0, count) else null
    }

    private fun handleMouseEvent(message: Int, mouseData: Int): Boolean {
        val button = when (message) {
            Win32Api.WM_LBUTTONDOWN, Win32Api.WM_LBUTTONUP -> MouseButtonId.LEFT
            Win32Api.WM_RBUTTONDOWN, Win32Api.WM_RBUTTONUP -> MouseButtonId.RIGHT
            Win32Api.WM_MBUTTONDOWN, Win32Api.WM_MBUTTONUP -> MouseButtonId.MIDDLE
            Win32Api.WM_XBUTTONDOWN, Win32Api.WM_XBUTTONUP -> {
                if ((mouseData ushr 16) == Win32Api.XBUTTON1) MouseButtonId.X1 else MouseButtonId.X2
            }
            else -> null
        }
        if (button != null) {
            val isDown = message == Win32Api.WM_LBUTTONDOWN ||
                message == Win32Api.WM_RBUTTONDOWN ||
                message == Win32Api.WM_MBUTTONDOWN ||
                message == Win32Api.WM_XBUTTONDOWN
            val routeAsTrigger = onMouseInput(button, isDown)
            if (isDown && routeAsTrigger) return onTrigger(TriggerSpec.Mouse(button, heldModifiers.toSet()))
        }
        return false
    }

    private fun vkToModifier(vk: Int): ModifierKey? = when (vk) {
        WinUser.VK_CONTROL, WinUser.VK_LCONTROL, WinUser.VK_RCONTROL -> ModifierKey.CTRL
        WinUser.VK_SHIFT, WinUser.VK_LSHIFT, WinUser.VK_RSHIFT -> ModifierKey.SHIFT
        WinUser.VK_MENU, WinUser.VK_LMENU, WinUser.VK_RMENU -> ModifierKey.ALT
        VK_LWIN, VK_RWIN -> ModifierKey.WIN
        else -> null
    }

    private companion object {
        const val VK_LWIN = 0x5B
        const val VK_RWIN = 0x5C
    }
}
