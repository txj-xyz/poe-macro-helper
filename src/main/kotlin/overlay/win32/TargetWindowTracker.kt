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
    private data class ClientGeometry(val x: Int, val y: Int, val width: Int, val height: Int)

    private val user32 = Win32Api.INSTANCE
    private var primaryBoundsApplied = false
    private var appliedTargetGeometry: ClientGeometry? = null

    @Volatile
    var target: WinDef.HWND? = null
        private set

    /** True on the frame the overlay is actually visible and positioned over the target. */
    var isOverlayVisible: Boolean = true
        private set

    fun setTarget(hwnd: WinDef.HWND?) {
        target = hwnd
        primaryBoundsApplied = false
        appliedTargetGeometry = null
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
            appliedTargetGeometry = null
            glfwSetWindowAttrib(glfwWindowHandle, GLFW_FLOATING, GLFW_TRUE)
            if (!primaryBoundsApplied) applyPrimaryMonitorBounds()
            glfwShowWindow(glfwWindowHandle)
            isOverlayVisible = true
            return
        }

        if (!user32.IsWindow(current)) {
            target = null
            appliedTargetGeometry = null
            hide()
            return
        }

        val isForeground = user32.GetForegroundWindow() == current
        val isMinimized = user32.IsIconic(current)
        if (!isForeground || isMinimized) {
            hide()
            return
        }

        val geometry = clientGeometry(current) ?: run {
            hide()
            return
        }

        glfwSetWindowAttrib(glfwWindowHandle, GLFW_FLOATING, GLFW_TRUE)
        applyGeometry(geometry)
        glfwShowWindow(glfwWindowHandle)
        isOverlayVisible = true
    }

    /**
     * Re-sample target geometry around presentation to avoid displaying one
     * stale overlay position while the target is in a live Windows move loop.
     * A mid-frame resize is hidden until the next full ImGui frame can use the
     * new display bounds; a position-only change is safe to apply immediately.
     */
    fun syncToTargetForPresentation() {
        val current = target ?: return
        if (!user32.IsWindow(current) || user32.GetForegroundWindow() != current || user32.IsIconic(current)) {
            hide()
            return
        }

        val geometry = clientGeometry(current) ?: run {
            hide()
            return
        }
        val applied = appliedTargetGeometry ?: return
        if (geometry.width != applied.width || geometry.height != applied.height) {
            hide()
            return
        }
        if (geometry.x != applied.x || geometry.y != applied.y) {
            glfwSetWindowPos(glfwWindowHandle, geometry.x, geometry.y)
            appliedTargetGeometry = geometry
        }
    }

    private fun applyGeometry(geometry: ClientGeometry) {
        val previous = appliedTargetGeometry
        // Size first so a shrinking target never spends a native update at
        // its new position with the previous larger overlay dimensions.
        if (previous == null || previous.width != geometry.width || previous.height != geometry.height) {
            glfwSetWindowSize(glfwWindowHandle, geometry.width, geometry.height)
        }
        if (previous == null || previous.x != geometry.x || previous.y != geometry.y) {
            glfwSetWindowPos(glfwWindowHandle, geometry.x, geometry.y)
        }
        appliedTargetGeometry = geometry
    }

    /** Client area only, excluding the target's title bar and borders. */
    private fun clientGeometry(hwnd: WinDef.HWND): ClientGeometry? {
        val clientSize = WinDef.RECT()
        if (!user32.GetClientRect(hwnd, clientSize)) return null
        val origin = WinDef.POINT()
        if (!user32.ClientToScreen(hwnd, origin)) return null
        return ClientGeometry(
            x = origin.x,
            y = origin.y,
            width = (clientSize.right - clientSize.left).coerceAtLeast(1),
            height = (clientSize.bottom - clientSize.top).coerceAtLeast(1),
        )
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
