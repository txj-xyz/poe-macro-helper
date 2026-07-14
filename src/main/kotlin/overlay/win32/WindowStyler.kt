package overlay.win32

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinUser
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_PASSTHROUGH
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.glfwSetWindowAttrib
import org.lwjgl.glfw.GLFWNativeWin32
import overlay.ui.WindowBounds

/**
 * Applies the overlay-specific extended window styles to the GLFW window's
 * real HWND: WS_EX_TOOLWINDOW (hidden from taskbar/Alt-Tab) and
 * WS_EX_NOACTIVATE (clicking the overlay never steals the game's activation)
 * are set once and left permanent. WS_EX_TRANSPARENT (click-through) is the
 * only flag that toggles every frame. Position/size/topmost/visibility are
 * deliberately NOT handled here - those go through GLFW's own window API
 * (see TargetWindowTracker) so GLFW's internal bookkeeping doesn't fight a
 * raw Win32 SetWindowPos bypassing it.
 */
object WindowStyler {
    private val user32 = Win32Api.INSTANCE
    private val gdi32 = GDI32.INSTANCE
    private var lastInteractiveRegions: List<WindowBounds.WindowRegion>? = null
    private var fullClientRegionActive = false
    private var mouseCaptured = false

    fun hwndOf(glfwWindowHandle: Long): WinDef.HWND {
        val raw = GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle)
        return WinDef.HWND(Pointer(raw))
    }

    fun applyPermanentOverlayStyles(hwnd: WinDef.HWND) {
        val current = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        val updated = current or Win32Api.WS_EX_TOOLWINDOW or Win32Api.WS_EX_NOACTIVATE
        if (updated != current) {
            user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, updated)
        }
    }

    fun setClickThrough(hwnd: WinDef.HWND, clickThrough: Boolean) {
        val current = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        val updated = if (clickThrough) {
            current or WinUser.WS_EX_TRANSPARENT
        } else {
            current and WinUser.WS_EX_TRANSPARENT.inv()
        }
        if (updated != current) {
            user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, updated)
        }
    }

    /**
     * Shapes the 1280x720 native overlay HWND to only the visible ImGui
     * rectangles. Pixels outside this region are not part of the Windows
     * window at all, so clicks go directly to the game underneath.
     */
    fun setInteractiveRegions(hwnd: WinDef.HWND, regions: List<WindowBounds.WindowRegion>) {
        if (!fullClientRegionActive && regions == lastInteractiveRegions) return
        val combined = gdi32.CreateRectRgn(0, 0, 0, 0) ?: return
        for (region in regions) {
            if (region.right <= region.left || region.bottom <= region.top) continue
            val part = gdi32.CreateRectRgn(region.left, region.top, region.right, region.bottom) ?: continue
            gdi32.CombineRgn(combined, combined, part, WinGDI.RGN_OR)
            gdi32.DeleteObject(part)
        }

        // On success Windows owns the HRGN; delete it ourselves only on failure.
        // The frame is rendered and swapped before this call, so requesting
        // an immediate non-client redraw only introduces a competing paint
        // between old/new regions. Reveal the already-painted surface.
        if (user32.SetWindowRgn(hwnd, combined, false) == 0) {
            gdi32.DeleteObject(combined)
        } else {
            fullClientRegionActive = false
            lastInteractiveRegions = regions.toList()
        }
    }

    /**
     * Remove the tight panel-shaped region while ImGui is moving/resizing a
     * panel. The full backing HWND is transparent and mouse-captured for a
     * drag, so it can render without clipping until the button is released.
     */
    fun setFullClientRegion(hwnd: WinDef.HWND) {
        if (fullClientRegionActive) return
        if (user32.SetWindowRgn(hwnd, null, false) != 0) {
            fullClientRegionActive = true
            lastInteractiveRegions = null
        }
    }

    /** Keep all mouse messages on the overlay while a shaped ImGui window moves. */
    fun setMouseCaptured(hwnd: WinDef.HWND, captured: Boolean) {
        if (captured == mouseCaptured) return
        if (captured) {
            user32.SetCapture(hwnd)
        } else {
            user32.ReleaseCapture()
        }
        mouseCaptured = captured
    }

    fun setFocusWithoutActivating(hwnd: WinDef.HWND) {
        user32.SetFocus(hwnd)
    }

    /** Screen-space cursor position converted to this window's client coordinates. */
    fun cursorPosInClient(hwnd: WinDef.HWND): WinDef.POINT {
        val point = WinDef.POINT()
        user32.GetCursorPos(point)
        user32.ScreenToClient(hwnd, point)
        return point
    }
}
