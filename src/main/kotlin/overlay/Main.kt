package overlay

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.lwjgl.opengl.GL11.*
import overlay.engine.MacroEngine
import overlay.engine.TriggerRouter
import overlay.engine.model.KeyCombo
import overlay.engine.model.Macro
import overlay.engine.model.MacroAction
import overlay.engine.model.MacroProfile
import overlay.engine.model.TriggerSpec
import overlay.settings.ProfileStore
import overlay.settings.SettingsStore
import overlay.share.LocalStubShareProvider
import overlay.ui.WindowBounds
import overlay.ui.panels.BindingEditorPanel
import overlay.ui.panels.GamePickerPanel
import overlay.ui.panels.ImportExportPanel
import overlay.ui.panels.MacroButtonPanel
import overlay.ui.panels.SettingsPanel
import overlay.ui.panels.SharePanel
import overlay.win32.HookManager
import overlay.win32.TargetWindowTracker
import overlay.win32.WindowStyler
import overlay.window.DpiAwareness
import overlay.window.ImGuiLayer
import overlay.window.OverlayWindow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val VK_A = 0x41
private const val VK_F6 = 0x75
private const val VK_F10 = 0x79
private const val DEFAULT_PROFILE_ID = "user"

private val EXAMPLE_MACROS = listOf(
    Macro(
        id = "f6-types-a",
        name = "F6 types A (example)",
        trigger = TriggerSpec.Keyboard(KeyCombo(VK_F6)),
        action = MacroAction.KeyPress(KeyCombo(VK_A)),
    ),
)

fun main() {
    DpiAwareness.enable()

    // Transparent pixels are now unconditionally click-through. Keep the old
    // serialized field true for backwards-compatible settings files.
    var settings = SettingsStore.load().copy(clickThroughEnabled = true)
    SettingsStore.save(settings)

    val window = OverlayWindow("Macro Overlay")
    val overlayHwnd = WindowStyler.hwndOf(window.handle)
    WindowStyler.applyPermanentOverlayStyles(overlayHwnd)
    WindowStyler.setClickThrough(overlayHwnd, false)
    WindowStyler.setInteractiveRegions(overlayHwnd, emptyList())

    val imgui = ImGuiLayer(window.handle)
    imgui.init()

    val tracker = TargetWindowTracker(window.handle)
    val gamePicker = GamePickerPanel(overlayHwnd)

    val disableMacrosWhenDetached = AtomicBoolean(settings.disableMacrosWhenDetached)
    val macroEngine = MacroEngine {
        if (tracker.target != null) {
            tracker.isTargetForeground()
        } else {
            !disableMacrosWhenDetached.get()
        }
    }
    macroEngine.start()
    val triggerRouter = TriggerRouter(macroEngine)

    val toggleMainRequested = AtomicBoolean(false)
    val uiKeyboardActive = AtomicBoolean(false)
    val cursorOverUi = AtomicBoolean(false)
    val forwardedOutsideMouseButtons = ConcurrentHashMap.newKeySet<overlay.engine.model.MouseButtonId>()
    val applicationOpen = ImBoolean(true)

    val hookManager = HookManager(
        onTrigger = { trigger ->
            val keyboard = trigger as? TriggerSpec.Keyboard
            when {
                keyboard?.combo?.vk == VK_F10 && keyboard.combo.modifiers.isEmpty() -> {
                    toggleMainRequested.set(true)
                    true
                }
                else -> triggerRouter.route(trigger)
            }
        },
        onKeyboardInput = { event ->
            val capturingBinding = triggerRouter.isCapturing()
            if (!capturingBinding) {
                // Keep ImGui's key state synchronized even while no widget is
                // active. Character events are discarded by ImGui on frames
                // where no text field accepts them.
                imgui.queueKeyboardEvent(event)
            }
            event.isDown &&
                event.vkCode != VK_F10 &&
                !capturingBinding &&
                uiKeyboardActive.get()
        },
        onMouseInput = { button, isDown ->
            // GLFW receives clicks inside the shaped overlay. Forward only
            // outside clicks so ImGui can release an input field when the
            // user clicks back into the game.
            val overUi = cursorOverUi.get()
            if (isDown && !overUi) {
                forwardedOutsideMouseButtons.add(button)
                imgui.queueMouseButtonEvent(button, isDown)
            } else if (!isDown && forwardedOutsideMouseButtons.remove(button)) {
                imgui.queueMouseButtonEvent(button, isDown)
            }
            !overUi
        },
    )
    // This listener remains alive so F10 can always recover a hidden main
    // window and binding capture works globally.
    hookManager.start()

    val bindingEditor = BindingEditorPanel(
        triggerRouter,
        macroEngine,
        defaultSuppressOriginalInput = { settings.defaultSuppressOriginalInput },
        onMacrosChanged = { macros -> ProfileStore.save(MacroProfile(DEFAULT_PROFILE_ID, "User Profile", macros)) },
    )
    bindingEditor.replaceAll(ProfileStore.load(DEFAULT_PROFILE_ID)?.macros ?: EXAMPLE_MACROS)
    val macroButtonPanel = MacroButtonPanel(macroEngine)

    fun saveSettings(updated: overlay.settings.AppSettings) {
        settings = updated.copy(clickThroughEnabled = true)
        disableMacrosWhenDetached.set(settings.disableMacrosWhenDetached)
        SettingsStore.save(settings)
    }

    val settingsPanel = SettingsPanel(getSettings = { settings }, onChange = ::saveSettings)
    val importExportPanel = ImportExportPanel(
        getCurrentProfile = { macroEngine.currentProfile() },
        onImport = { profile -> bindingEditor.replaceAll(profile.macros) },
    )
    val sharePanel = SharePanel(
        shareProvider = LocalStubShareProvider(),
        getCurrentProfile = { macroEngine.currentProfile() },
        onImport = { profile -> bindingEditor.replaceAll(profile.macros) },
    )

    try {
        while (!window.shouldClose()) {
            if (toggleMainRequested.getAndSet(false)) {
                saveSettings(settings.copy(mainWindowVisible = !settings.mainWindowVisible))
            }

            window.pollEvents()
            tracker.update()

            // Poll globally in both native states: this window never activates,
            // and GLFW can otherwise leave the last interactive position stale.
            val cursor = WindowStyler.cursorPosInClient(overlayHwnd)

            imgui.newFrame(cursor.x.toFloat(), cursor.y.toFloat())
            WindowBounds.beginFrame()

            if (settings.mainWindowVisible) {
                if (
                    WindowBounds.begin(
                        title = "Overlay Controls",
                        defaultX = 16f,
                        defaultY = 16f,
                        defaultWidth = 520f,
                        defaultHeight = 650f,
                        fixedSize = true,
                        flags = ImGuiWindowFlags.NoCollapse,
                        open = applicationOpen,
                    )
                ) {
                    if (gamePicker.render()) {
                        tracker.setTarget(gamePicker.selected?.hwnd)
                        saveSettings(settings.copy(lastTargetWindowTitle = gamePicker.selected?.title))
                    }

                    ImGui.separator()
                    settingsPanel.render()
                    ImGui.separator()
                    importExportPanel.render()
                    ImGui.separator()
                    sharePanel.render()
                }
                ImGui.end()
                if (!applicationOpen.get()) window.requestClose()
            }

            if (settings.macroWindowVisible) {
                if (
                    WindowBounds.begin(
                        title = "Macro Editor",
                        defaultX = 552f,
                        defaultY = 16f,
                        defaultWidth = 445f,
                        defaultHeight = 422f,
                        fixedSize = false,
                    )
                ) {
                    bindingEditor.render()
                }
                ImGui.end()
            }

            if (settings.macroButtonPanelVisible) {
                if (
                    WindowBounds.begin(
                        title = "Macro Buttons",
                        defaultX = (ImGui.getIO().displaySize.x - 260f).coerceAtLeast(16f),
                        defaultY = 16f,
                        defaultWidth = 240f,
                        defaultHeight = 100f,
                        fixedSize = false,
                        flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoResize,
                    )
                ) {
                    macroButtonPanel.render()
                }
                ImGui.end()
            }

            // The HWND is shaped to the moving ImGui rectangles. Without
            // capture, Windows can alternate mouse ownership with the game as
            // the region advances frame-by-frame beneath the cursor.
            WindowStyler.setMouseCaptured(overlayHwnd, WindowBounds.isDraggingAnyWindow())

            imgui.finishFrame()
            uiKeyboardActive.set(
                ImGui.getIO().wantTextInput ||
                    ImGui.isAnyItemActive(),
            )
            cursorOverUi.set(WindowBounds.isCursorOverVisibleWindow())
            WindowStyler.setInteractiveRegions(overlayHwnd, WindowBounds.visibleWindowRegions())
            if (WindowBounds.isCursorOverVisibleWindow()) {
                WindowStyler.setFocusWithoutActivating(overlayHwnd)
            }

            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            imgui.renderDrawData()
            window.swapBuffers()
        }
    } finally {
        hookManager.stop()
        macroEngine.stop()
        imgui.dispose()
        window.destroy()
    }
}
