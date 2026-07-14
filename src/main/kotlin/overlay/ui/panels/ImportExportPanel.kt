package overlay.ui.panels

import imgui.ImGui
import overlay.engine.model.MacroProfile
import overlay.settings.ProfileStore
import overlay.window.ClipboardSupport
import java.awt.FileDialog
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * FileDialog blocks, so it's always launched on a background thread; the
 * result is only ever applied back on the GL/render thread (via [render],
 * next frame) - never call FileDialog directly on the render thread.
 */
class ImportExportPanel(
    private val getCurrentProfile: () -> MacroProfile,
    private val onImport: (MacroProfile) -> Unit,
) {
    private val pendingImport = AtomicReference<MacroProfile?>(null)
    private val statusMessage = AtomicReference("")

    @Volatile private var busy = false

    fun render() {
        ImGui.separatorText("Clipboard")
        ImGui.textWrapped("Copy a complete profile as JSON or import JSON sent by another user.")

        if (ImGui.button("Copy profile JSON")) {
            ClipboardSupport.writeText(ProfileStore.encode(getCurrentProfile()))
            statusMessage.set("Profile JSON copied to the clipboard.")
        }

        ImGui.sameLine()
        if (ImGui.button("Import JSON from clipboard")) {
            importFromText(ClipboardSupport.readText(), "clipboard")
        }

        ImGui.separatorText("Files")
        ImGui.textWrapped("Save a portable profile file or import one from disk.")
        ImGui.beginDisabled(busy)
        if (ImGui.button("Export profile to file...") && !busy) {
            busy = true
            Thread(::exportViaDialog, "profile-export").apply { isDaemon = true; start() }
        }

        ImGui.sameLine()
        if (ImGui.button("Import profile from file...") && !busy) {
            busy = true
            Thread(::importViaDialog, "profile-import").apply { isDaemon = true; start() }
        }
        ImGui.endDisabled()

        pendingImport.getAndSet(null)?.let(onImport)

        val message = statusMessage.get()
        if (message.isNotEmpty()) {
            ImGui.separatorText("Status")
            ImGui.textWrapped(message)
        }
    }

    private fun importFromText(text: String, source: String) {
        if (text.isBlank()) {
            statusMessage.set("Import failed: $source is empty.")
            return
        }
        runCatching { ProfileStore.decode(text) }
            .onSuccess {
                pendingImport.set(it)
                statusMessage.set("Imported '${it.name}' from $source.")
            }
            .onFailure { statusMessage.set("Import failed: ${it.message}") }
    }

    private fun exportViaDialog() {
        try {
            val dialog = FileDialog(null as java.awt.Frame?, "Export Macro Profile", FileDialog.SAVE)
            dialog.file = "macro-profile.json"
            dialog.isVisible = true
            val directory = dialog.directory
            val fileName = dialog.file
            if (directory != null && fileName != null) {
                val file = File(directory, if (fileName.endsWith(".json")) fileName else "$fileName.json")
                file.writeText(ProfileStore.encode(getCurrentProfile()))
                statusMessage.set("Exported to ${file.absolutePath}")
            }
        } catch (e: Exception) {
            statusMessage.set("Export failed: ${e.message}")
        } finally {
            busy = false
        }
    }

    private fun importViaDialog() {
        try {
            val dialog = FileDialog(null as java.awt.Frame?, "Import Macro Profile", FileDialog.LOAD)
            dialog.isVisible = true
            val directory = dialog.directory
            val fileName = dialog.file
            if (directory != null && fileName != null) {
                val file = File(directory, fileName)
                pendingImport.set(ProfileStore.decode(file.readText()))
                statusMessage.set("Imported ${file.name}")
            }
        } catch (e: Exception) {
            statusMessage.set("Import failed: ${e.message}")
        } finally {
            busy = false
        }
    }
}
