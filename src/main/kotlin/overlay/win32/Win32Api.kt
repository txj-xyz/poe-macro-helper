package overlay.win32

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.W32APIOptions

/**
 * jna-platform's User32 interface is missing a couple of calls we need
 * (IsIconic, ScreenToClient) - extending it and re-loading is the standard
 * JNA pattern for filling gaps in the bundled mapping.
 */
interface Win32Api : User32 {
    fun IsIconic(hWnd: WinDef.HWND): Boolean
    fun ScreenToClient(hWnd: WinDef.HWND, lpPoint: WinDef.POINT): Boolean
    fun ClientToScreen(hWnd: WinDef.HWND, lpPoint: WinDef.POINT): Boolean
    fun SetCapture(hWnd: WinDef.HWND): WinDef.HWND?
    fun ReleaseCapture(): Boolean

    companion object {
        val INSTANCE: Win32Api = Native.load("user32", Win32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)

        // Not exposed by WinUser in jna-platform 5.17.0, but stable ABI constants.
        const val WS_EX_TOOLWINDOW = 0x00000080
        const val WS_EX_NOACTIVATE = 0x08000000

        const val HC_ACTION = 0

        const val WM_LBUTTONDOWN = 0x0201
        const val WM_LBUTTONUP = 0x0202
        const val WM_RBUTTONDOWN = 0x0204
        const val WM_RBUTTONUP = 0x0205
        const val WM_MBUTTONDOWN = 0x0207
        const val WM_MBUTTONUP = 0x0208
        const val WM_MOUSEWHEEL = 0x020A
        const val WM_XBUTTONDOWN = 0x020B
        const val WM_XBUTTONUP = 0x020C
        const val WM_MOUSEHWHEEL = 0x020E

        const val XBUTTON1 = 0x0001
        const val XBUTTON2 = 0x0002

        const val MOUSEEVENTF_MOVE = 0x0001
        const val MOUSEEVENTF_LEFTDOWN = 0x0002
        const val MOUSEEVENTF_LEFTUP = 0x0004
        const val MOUSEEVENTF_RIGHTDOWN = 0x0008
        const val MOUSEEVENTF_RIGHTUP = 0x0010
        const val MOUSEEVENTF_MIDDLEDOWN = 0x0020
        const val MOUSEEVENTF_MIDDLEUP = 0x0040
        const val MOUSEEVENTF_XDOWN = 0x0080
        const val MOUSEEVENTF_XUP = 0x0100
        const val MOUSEEVENTF_WHEEL = 0x0800
        const val MOUSEEVENTF_HWHEEL = 0x1000
        const val MOUSEEVENTF_ABSOLUTE = 0x8000

        const val WHEEL_DELTA = 120
    }
}
