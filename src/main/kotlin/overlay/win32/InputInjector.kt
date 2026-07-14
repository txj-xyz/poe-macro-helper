package overlay.win32

import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import overlay.engine.model.KeyCombo
import overlay.engine.model.ModifierKey
import overlay.engine.model.MouseButtonId

/**
 * Synthesizes keyboard/mouse input via SendInput - more reliable for games
 * than java.awt.Robot, which goes through a different, less universally
 * honored injection path.
 *
 * SendInput requires all events in one call to live in a single contiguous
 * native array, so each [WinUser.INPUT] element below is allocated via
 * `template.toArray(n)` and populated *in place* - constructing standalone
 * INPUT instances and copying their `input` union field over would leave
 * that field's backing memory pointing outside the contiguous block.
 */
object InputInjector {
    private val user32 = Win32Api.INSTANCE

    private fun sendKeyboard(events: List<Triple<Int, Int, Int>>) {
        if (events.isEmpty()) return
        val array = WinUser.INPUT().toArray(events.size) as Array<WinUser.INPUT>
        events.forEachIndexed { i, (vk, scan, flags) ->
            val input = array[i]
            input.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
            input.input.setType("ki")
            input.input.ki.wVk = WinDef.WORD(vk.toLong())
            input.input.ki.wScan = WinDef.WORD(scan.toLong())
            input.input.ki.dwFlags = WinDef.DWORD(flags.toLong())
            input.input.ki.time = WinDef.DWORD(0)
            input.input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(0)
            input.write()
        }
        user32.SendInput(WinDef.DWORD(array.size.toLong()), array, array[0].size())
    }

    private data class MouseEvent(val dx: Int, val dy: Int, val mouseData: Int, val flags: Int)

    private fun sendMouseEvents(events: List<MouseEvent>) {
        if (events.isEmpty()) return
        val array = WinUser.INPUT().toArray(events.size) as Array<WinUser.INPUT>
        events.forEachIndexed { i, e ->
            val input = array[i]
            input.type = WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE.toLong())
            input.input.setType("mi")
            input.input.mi.dx = WinDef.LONG(e.dx.toLong())
            input.input.mi.dy = WinDef.LONG(e.dy.toLong())
            input.input.mi.mouseData = WinDef.DWORD(e.mouseData.toLong())
            input.input.mi.dwFlags = WinDef.DWORD(e.flags.toLong())
            input.input.mi.time = WinDef.DWORD(0)
            input.input.mi.dwExtraInfo = BaseTSD.ULONG_PTR(0)
            input.write()
        }
        user32.SendInput(WinDef.DWORD(array.size.toLong()), array, array[0].size())
    }

    private fun modifierVk(modifier: ModifierKey): Int = when (modifier) {
        ModifierKey.CTRL -> WinUser.VK_CONTROL
        ModifierKey.SHIFT -> WinUser.VK_SHIFT
        ModifierKey.ALT -> WinUser.VK_MENU
        ModifierKey.WIN -> 0x5B // VK_LWIN - not exposed by jna-platform's WinUser
    }

    fun keyDown(vk: Int) = sendKeyboard(listOf(Triple(vk, 0, 0)))

    fun keyUp(vk: Int) = sendKeyboard(listOf(Triple(vk, 0, WinUser.KEYBDINPUT.KEYEVENTF_KEYUP)))

    /** Presses modifiers, then the key, holds [holdMs], then releases in reverse order. */
    fun pressKeyCombo(combo: KeyCombo, holdMs: Long = 40) {
        if (combo.vk == 0) return
        combo.modifiers.forEach { keyDown(modifierVk(it)) }
        keyDown(combo.vk)
        if (holdMs > 0) Thread.sleep(holdMs)
        keyUp(combo.vk)
        combo.modifiers.reversed().forEach { keyUp(modifierVk(it)) }
    }

    /** Types arbitrary Unicode text via KEYEVENTF_UNICODE - layout-independent, no clipboard involved. */
    fun typeUnicodeText(text: String) {
        for (ch in text) {
            val scan = ch.code
            sendKeyboard(
                listOf(
                    Triple(0, scan, WinUser.KEYBDINPUT.KEYEVENTF_UNICODE),
                    Triple(0, scan, WinUser.KEYBDINPUT.KEYEVENTF_UNICODE or WinUser.KEYBDINPUT.KEYEVENTF_KEYUP),
                ),
            )
        }
    }

    fun mouseClick(button: MouseButtonId) {
        val (downFlag, upFlag, data) = mouseEventFlags(button)
        sendMouseEvents(listOf(MouseEvent(0, 0, data, downFlag)))
        Thread.sleep(30)
        sendMouseEvents(listOf(MouseEvent(0, 0, data, upFlag)))
    }

    fun mouseScroll(ticks: Int) {
        sendMouseEvents(listOf(MouseEvent(0, 0, ticks * Win32Api.WHEEL_DELTA, Win32Api.MOUSEEVENTF_WHEEL)))
    }

    /** Moves the cursor to an absolute screen position via SendInput (seen by raw-input games, unlike SetCursorPos). */
    fun moveTo(screenX: Int, screenY: Int) {
        val virtualLeft = user32.GetSystemMetrics(WinUser.SM_XVIRTUALSCREEN)
        val virtualTop = user32.GetSystemMetrics(WinUser.SM_YVIRTUALSCREEN)
        val virtualWidth = user32.GetSystemMetrics(WinUser.SM_CXVIRTUALSCREEN)
        val virtualHeight = user32.GetSystemMetrics(WinUser.SM_CYVIRTUALSCREEN)
        val normalizedX = ((screenX - virtualLeft) * 65536) / virtualWidth
        val normalizedY = ((screenY - virtualTop) * 65536) / virtualHeight
        sendMouseEvents(listOf(MouseEvent(normalizedX, normalizedY, 0, Win32Api.MOUSEEVENTF_MOVE or Win32Api.MOUSEEVENTF_ABSOLUTE)))
    }

    private fun mouseEventFlags(button: MouseButtonId): Triple<Int, Int, Int> = when (button) {
        MouseButtonId.LEFT -> Triple(Win32Api.MOUSEEVENTF_LEFTDOWN, Win32Api.MOUSEEVENTF_LEFTUP, 0)
        MouseButtonId.RIGHT -> Triple(Win32Api.MOUSEEVENTF_RIGHTDOWN, Win32Api.MOUSEEVENTF_RIGHTUP, 0)
        MouseButtonId.MIDDLE -> Triple(Win32Api.MOUSEEVENTF_MIDDLEDOWN, Win32Api.MOUSEEVENTF_MIDDLEUP, 0)
        MouseButtonId.X1 -> Triple(Win32Api.MOUSEEVENTF_XDOWN, Win32Api.MOUSEEVENTF_XUP, Win32Api.XBUTTON1)
        MouseButtonId.X2 -> Triple(Win32Api.MOUSEEVENTF_XDOWN, Win32Api.MOUSEEVENTF_XUP, Win32Api.XBUTTON2)
        MouseButtonId.WHEEL_UP, MouseButtonId.WHEEL_DOWN -> Triple(0, 0, 0) // not clickable; see mouseScroll
    }
}
