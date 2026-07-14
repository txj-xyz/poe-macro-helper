package overlay.window

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Reliable system clipboard access used by ImGui and explicit paste buttons. */
object ClipboardSupport {
    fun readText(): String = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor)?.toString().orEmpty()
        } else {
            ""
        }
    }.getOrDefault("")

    fun writeText(value: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
        }
    }
}
