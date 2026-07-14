package overlay.window

import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.imageio.ImageIO

/**
 * Keeps the overlay reachable even when every ImGui panel is hidden.
 *
 * Tray callbacks run on AWT's event thread, so they only signal the main
 * loop. GLFW and ImGui work remains on the thread that created them.
 */
class TrayController(
    private val onShowMain: () -> Unit,
    private val onExit: () -> Unit,
) : AutoCloseable {
    @Volatile
    private var trayIcon: TrayIcon? = null

    fun start(): Boolean {
        if (!SystemTray.isSupported()) return false

        return try {
            runOnEventThreadAndWait {
                if (trayIcon != null) return@runOnEventThreadAndWait

                val imageResource = requireNotNull(
                    TrayController::class.java.getResource("/icons/macro-overlay.png"),
                ) { "Missing tray icon resource: /icons/macro-overlay.png" }
                val image = ImageIO.read(imageResource)
                val menu = PopupMenu().apply {
                    add(MenuItem("Macro Overlay is running").apply { isEnabled = false })
                    addSeparator()
                    add(MenuItem("Show Main Controls").apply {
                        addActionListener { onShowMain() }
                    })
                    addSeparator()
                    add(MenuItem("Exit Macro Overlay").apply {
                        addActionListener { onExit() }
                    })
                }
                val icon = TrayIcon(image, "Macro Overlay - Running", menu).apply {
                    isImageAutoSize = true
                    addActionListener { onShowMain() }
                }

                SystemTray.getSystemTray().add(icon)
                trayIcon = icon
            }
            true
        } catch (exception: Exception) {
            System.err.println("System tray icon could not be created: ${exception.message}")
            false
        }
    }

    override fun close() {
        try {
            runOnEventThreadAndWait {
                trayIcon?.let { SystemTray.getSystemTray().remove(it) }
                trayIcon = null
            }
        } catch (exception: Exception) {
            System.err.println("System tray icon could not be removed: ${exception.message}")
        }
    }

    private fun runOnEventThreadAndWait(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            action()
        } else {
            EventQueue.invokeAndWait(action)
        }
    }
}
