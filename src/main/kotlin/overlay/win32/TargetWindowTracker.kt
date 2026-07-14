package overlay.win32

import com.sun.jna.platform.win32.WinDef
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL

/**
 * Polls (rather than hooks) the target window's foreground/minimized/rect
 * state every frame and keeps the overlay window positioned exactly over it,
 * only while it's the foreground window. Polling avoids the extra
 * thread + message pump that SetWinEventHook would need, and a handful of
 * Win32 calls at 60fps is negligible.
 *
 * Repositioning goes through GLFW's own window API (glfwSetWindowPos/Size/
 * Attrib), not raw Win32 SetWindowPos: GLFW keeps its own bookkeeping of the
 * window's position/size/floating state, and a non-resizable GLFW window
 * silently reverts a bypassed, raw SetWindowPos back to its original
 * geometry shortly after - GLFW has to be the one making the change for it
 * to stick.
 */
class TargetWindowTracker(private val glfwWindowHandle: Long) {
    private val user32 = Win32Api.INSTANCE
    private var primaryBoundsApplied = false

    @Volatile
    var target: WinDef.HWND? = null
        private set

    /** True on the frame the overlay is actually visible and positioned over the target. */
    var isOverlayVisible: Boolean = true
        private set

    fun setTarget(hwnd: WinDef.HWND?) {
        target = hwnd
        primaryBoundsApplied = false
    }

    /** Safe to call from the hook/engine threads before accepting or injecting input. */
    fun isTargetForeground(): Boolean {
        val current = target ?: return false
        return user32.IsWindow(current) && !user32.IsIconic(current) && user32.GetForegroundWindow() == current
    }

    fun update() {
        // No target picked: cover the entire primary monitor. The native HWND
        // is shaped to only the ImGui rectangles, so the rest remains absent
        // and fully clickable despite the full-screen backing dimensions.
        val current = target ?: run {
            glfwSetWindowAttrib(glfwWindowHandle, GLFW_FLOATING, GLFW_TRUE)
            if (!primaryBoundsApplied) applyPrimaryMonitorBounds()
            glfwShowWindow(glfwWindowHandle)
            isOverlayVisible = true
            return
        }

        if (!user32.IsWindow(current)) {
            target = null
            hide()
            return
        }

        val isForeground = user32.GetForegroundWindow() == current
        val isMinimized = user32.IsIconic(current)
        if (!isForeground || isMinimized) {
            hide()
            return
        }

        // Client area only, not GetWindowRect's full outer rect: a decorated
        // target (visible title bar/borders, e.g. windowed-mode apps) would
        // otherwise get its own title bar and system buttons covered by the
        // overlay, making it impossible to move/resize/minimize/close.
        val clientSize = WinDef.RECT()
        user32.GetClientRect(current, clientSize)
        val origin = WinDef.POINT()
        user32.ClientToScreen(current, origin)

        glfwSetWindowAttrib(glfwWindowHandle, GLFW_FLOATING, GLFW_TRUE)
        glfwSetWindowPos(glfwWindowHandle, origin.x, origin.y)
        glfwSetWindowSize(glfwWindowHandle, clientSize.right - clientSize.left, clientSize.bottom - clientSize.top)
        glfwShowWindow(glfwWindowHandle)
        isOverlayVisible = true
    }

    private fun applyPrimaryMonitorBounds() {
        val monitor = glfwGetPrimaryMonitor()
        if (monitor == NULL) return
        val mode = glfwGetVideoMode(monitor) ?: return
        MemoryStack.stackPush().use { stack ->
            val x = stack.mallocInt(1)
            val y = stack.mallocInt(1)
            glfwGetMonitorPos(monitor, x, y)
            glfwSetWindowPos(glfwWindowHandle, x[0], y[0])
            glfwSetWindowSize(glfwWindowHandle, mode.width(), mode.height())
        }
        primaryBoundsApplied = true
    }

    private fun hide() {
        if (isOverlayVisible) {
            glfwHideWindow(glfwWindowHandle)
        }
        isOverlayVisible = false
    }
}
