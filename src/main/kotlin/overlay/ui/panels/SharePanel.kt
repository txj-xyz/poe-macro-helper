package overlay.ui.panels

import imgui.ImGui
import imgui.type.ImString
import overlay.engine.model.MacroProfile
import overlay.share.ShareProvider
import overlay.share.ShareResult
import overlay.window.ClipboardSupport

class SharePanel(
    private val shareProvider: ShareProvider,
    private val getCurrentProfile: () -> MacroProfile,
    private val onImport: (MacroProfile) -> Unit,
) {
    private var lastShareCode: String? = null
    private var statusMessage: String = ""
    private val importBuffer = ImString(128)

    fun render() {
        ImGui.textWrapped("Share via code (local-only until a server is configured)")

        if (ImGui.button("Share current profile")) {
            when (val result = shareProvider.upload(getCurrentProfile())) {
                is ShareResult.Success -> {
                    lastShareCode = result.url
                    statusMessage = "Shared - this only works on this computer until a server is set up."
                }
                is ShareResult.Failure -> {
                    lastShareCode = null
                    statusMessage = "Share failed: ${result.message}"
                }
            }
        }

        lastShareCode?.let { code ->
            ImGui.textWrapped(code)
            if (ImGui.button("Copy##sharecode")) {
                ClipboardSupport.writeText(code)
                statusMessage = "Code copied to the clipboard."
            }
        }

        ImGui.text("Code or URL")
        ImGui.setNextItemWidth(-1f)
        ImGui.inputText("##share-code-import", importBuffer)
        if (ImGui.button("Paste##sharecode")) {
            importBuffer.set(ClipboardSupport.readText())
        }
        ImGui.sameLine()
        if (ImGui.button("Import##sharecode")) {
            val profile = shareProvider.download(importBuffer.get())
            if (profile != null) {
                onImport(profile)
                statusMessage = "Imported '${profile.name}'."
            } else {
                statusMessage = "No profile found for that code."
            }
        }

        if (statusMessage.isNotEmpty()) {
            ImGui.textWrapped(statusMessage)
        }
    }
}
