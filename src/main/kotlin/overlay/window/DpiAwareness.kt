package overlay.window

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

private interface User32Dpi : StdCallLibrary {
    fun SetProcessDpiAwarenessContext(value: Pointer): Boolean

    companion object {
        val INSTANCE: User32Dpi = Native.load("user32", User32Dpi::class.java)
    }
}

/**
 * Must run before any window is created, or GetWindowRect (Win32) and GLFW's
 * own coordinates will disagree on displays with non-100% scaling.
 */
object DpiAwareness {
    private val PER_MONITOR_AWARE_V2 = Pointer.createConstant(-4)

    fun enable() {
        runCatching { User32Dpi.INSTANCE.SetProcessDpiAwarenessContext(PER_MONITOR_AWARE_V2) }
    }
}
