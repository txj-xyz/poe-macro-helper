package overlay.win32

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference

data class TargetWindowInfo(
    val hwnd: WinDef.HWND,
    val title: String,
    val pid: Int,
    val processName: String,
)

/** Enumerates top-level, user-visible windows a macro profile could attach to. */
object GamePicker {
    private val user32 = Win32Api.INSTANCE
    private val kernel32 = Kernel32.INSTANCE

    fun listWindows(excludeHwnd: WinDef.HWND?): List<TargetWindowInfo> {
        val results = mutableListOf<TargetWindowInfo>()

        val callback = WinUser.WNDENUMPROC { hwnd, _ ->
            if (hwnd != excludeHwnd && user32.IsWindowVisible(hwnd)) {
                val exStyle = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
                if (exStyle and Win32Api.WS_EX_TOOLWINDOW == 0) {
                    val titleLength = user32.GetWindowTextLength(hwnd)
                    if (titleLength > 0) {
                        val buffer = CharArray(titleLength + 1)
                        user32.GetWindowText(hwnd, buffer, buffer.size)
                        val title = String(buffer, 0, titleLength)
                        if (title.isNotBlank()) {
                            val pidRef = IntByReference()
                            user32.GetWindowThreadProcessId(hwnd, pidRef)
                            results.add(TargetWindowInfo(hwnd, title, pidRef.value, resolveProcessName(pidRef.value)))
                        }
                    }
                }
            }
            true
        }
        user32.EnumWindows(callback, null)
        return results
    }

    private fun resolveProcessName(pid: Int): String {
        val handle: WinNT.HANDLE = kernel32.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid)
            ?: return "?"
        try {
            val buffer = CharArray(1024)
            val size = IntByReference(buffer.size)
            if (!kernel32.QueryFullProcessImageName(handle, 0, buffer, size)) return "?"
            val path = String(buffer, 0, size.value)
            return path.substringAfterLast('\\')
        } finally {
            kernel32.CloseHandle(handle)
        }
    }
}
