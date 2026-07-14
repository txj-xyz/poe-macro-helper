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
import overlay.ui.WindowBounds
import overlay.ui.VkNames
import overlay.ui.panels.BindingEditorPanel
import overlay.ui.panels.GamePickerPanel
import overlay.ui.panels.ImportExportPanel
import overlay.ui.panels.MacroButtonPanel
import overlay.ui.panels.SettingsPanel
import overlay.ui.panels.WindowControlsPanel
import overlay.win32.HookManager
import overlay.win32.TargetWindowTracker
import overlay.win32.WindowStyler
import overlay.window.DpiAwareness
import overlay.window.ImGuiLayer
import overlay.window.OverlayWindow
import overlay.window.TrayController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val VK_A = 0x41
private const val VK_F6 = 0x75
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
    val showMainRequested = AtomicBoolean(false)
    val exitRequested = AtomicBoolean(false)
    val mainWindowToggle = AtomicReference(settings.mainWindowToggle)
    val uiKeyboardActive = AtomicBoolean(false)
    val cursorOverUi = AtomicBoolean(false)
    val forwardedOutsideMouseButtons = ConcurrentHashMap.newKeySet<overlay.engine.model.MouseButtonId>()
    val applicationOpen = ImBoolean(true)

    val hookManager = HookManager(
        onTrigger = { trigger ->
            val keyboard = trigger as? TriggerSpec.Keyboard
            when {
                triggerRouter.isCapturing() -> triggerRouter.route(trigger)
                keyboard != null && keyboard.combo.vk != 0 && keyboard.combo == mainWindowToggle.get() -> {
                    toggleMainRequested.set(true)
                    true
                }
                else -> triggerRouter.route(trigger)
            }
        },
        onKeyboardInput = { event ->
            val capturingBinding = triggerRouter.isCapturing()
            val toggle = mainWindowToggle.get()
            val isMainToggle = event.isDown &&
                toggle.vk != 0 &&
                event.vkCode == toggle.vk &&
                event.modifiers == toggle.modifiers
            if (!capturingBinding) {
                // Keep ImGui's key state synchronized even while no widget is
                // active. Character events are discarded by ImGui on frames
                // where no text field accepts them.
                imgui.queueKeyboardEvent(event)
            }
            event.isDown &&
                !isMainToggle &&
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
    // This listener remains alive so the configured recovery hotkey can
    // always restore the main window and binding capture works globally.
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
        mainWindowToggle.set(settings.mainWindowToggle)
        SettingsStore.save(settings)
    }

    val settingsPanel = SettingsPanel(getSettings = { settings }, onChange = ::saveSettings)
    val windowControlsPanel = WindowControlsPanel(
        triggerRouter = triggerRouter,
        getSettings = { settings },
        onChange = ::saveSettings,
        onResetLayout = WindowBounds::resetLayout,
    )
    val importExportPanel = ImportExportPanel(
        getCurrentProfile = { macroEngine.currentProfile() },
        onImport = { profile -> bindingEditor.replaceAll(profile.macros) },
    )
    val trayController = TrayController(
        onShowMain = { showMainRequested.set(true) },
        onExit = { exitRequested.set(true) },
    )
    trayController.start()

    fun renderMainMenuBar() {
        if (!ImGui.beginMenuBar()) return

        if (ImGui.beginMenu("Windows")) {
            if (ImGui.menuItem("Macro Editor", "", settings.macroWindowVisible)) {
                saveSettings(settings.copy(macroWindowVisible = !settings.macroWindowVisible))
            }
            if (ImGui.menuItem("Macro Buttons", "", settings.macroButtonPanelVisible)) {
                saveSettings(settings.copy(macroButtonPanelVisible = !settings.macroButtonPanelVisible))
            }
            ImGui.separator()
            if (ImGui.menuItem("Show all windows")) {
                saveSettings(
                    settings.copy(
                        mainWindowVisible = true,
                        macroWindowVisible = true,
                        macroButtonPanelVisible = true,
                    ),
                )
            }
            if (ImGui.menuItem("Reset window layout")) WindowBounds.resetLayout()
            ImGui.separator()
            val toggleDescription = VkNames.describe(TriggerSpec.Keyboard(settings.mainWindowToggle))
            if (ImGui.menuItem("Hide main controls", toggleDescription, false, settings.mainWindowToggle.vk != 0)) {
                saveSettings(settings.copy(mainWindowVisible = false))
            }
            ImGui.endMenu()
        }

        if (ImGui.beginMenu("Application")) {
            if (ImGui.menuItem("Exit")) applicationOpen.set(false)
            ImGui.endMenu()
        }

        ImGui.endMenuBar()
    }

    try {
        while (!window.shouldClose()) {
            if (toggleMainRequested.getAndSet(false)) {
                saveSettings(settings.copy(mainWindowVisible = !settings.mainWindowVisible))
            }
            if (showMainRequested.getAndSet(false)) {
                saveSettings(settings.copy(mainWindowVisible = true))
            }
            if (exitRequested.getAndSet(false)) {
                window.requestClose()
            }

            window.pollEvents()
            tracker.update()

            // Poll globally in both native states: this window never activates,
            // and GLFW can otherwise leave the last interactive position stale.
            val cursor = WindowStyler.cursorPosInClient(overlayHwnd)

            imgui.newFrame(cursor.x.toFloat(), cursor.y.toFloat())
            WindowBounds.beginFrame()

            if (settings.mainWindowVisible) {
                ImGui.setNextWindowSizeConstraints(440f, 380f, Float.MAX_VALUE, Float.MAX_VALUE)
                if (
                    WindowBounds.begin(
                        title = "Macro Overlay",
                        defaultX = 16f,
                        defaultY = 16f,
                        defaultWidth = 520f,
                        defaultHeight = 500f,
                        fixedSize = false,
                        flags = ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.MenuBar,
                        open = applicationOpen,
                    )
                ) {
                    renderMainMenuBar()

                    ImGui.text("Status")
                    ImGui.sameLine()
                    if (tracker.target != null) {
                        ImGui.textDisabled("Attached to ${gamePicker.selected?.title ?: "selected target"}")
                    } else {
                        ImGui.textDisabled("Detached")
                    }
                    ImGui.separator()

                    if (ImGui.beginTabBar("main-workflows")) {
                        if (ImGui.beginTabItem("Target")) {
                            if (gamePicker.render()) {
                                tracker.setTarget(gamePicker.selected?.hwnd)
                                saveSettings(settings.copy(lastTargetWindowTitle = gamePicker.selected?.title))
                            }
                            ImGui.endTabItem()
                        }
                        if (ImGui.beginTabItem("Windows")) {
                            windowControlsPanel.render()
                            ImGui.endTabItem()
                        }
                        if (ImGui.beginTabItem("Profiles")) {
                            importExportPanel.render()
                            ImGui.endTabItem()
                        }
                        if (ImGui.beginTabItem("Preferences")) {
                            settingsPanel.render()
                            ImGui.endTabItem()
                        }
                        ImGui.endTabBar()
                    }
                }
                ImGui.end()
                if (!applicationOpen.get()) window.requestClose()
            }

            if (settings.macroWindowVisible) {
                val macroEditorOpen = ImBoolean(true)
                ImGui.setNextWindowSizeConstraints(400f, 320f, Float.MAX_VALUE, Float.MAX_VALUE)
                if (
                    WindowBounds.begin(
                        title = "Macro Editor",
                        defaultX = 552f,
                        defaultY = 16f,
                        defaultWidth = 445f,
                        defaultHeight = 422f,
                        fixedSize = false,
                        open = macroEditorOpen,
                    )
                ) {
                    bindingEditor.render()
                }
                ImGui.end()
                if (!macroEditorOpen.get()) {
                    saveSettings(settings.copy(macroWindowVisible = false))
                }
            }

            if (settings.macroButtonPanelVisible) {
                val macroButtonsOpen = ImBoolean(true)
                if (
                    WindowBounds.begin(
                        title = "Macro Buttons",
                        defaultX = (ImGui.getIO().displaySize.x - 260f).coerceAtLeast(16f),
                        defaultY = 16f,
                        defaultWidth = 240f,
                        defaultHeight = 100f,
                        fixedSize = false,
                        flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoResize,
                        open = macroButtonsOpen,
                    )
                ) {
                    macroButtonPanel.render()
                }
                ImGui.end()
                if (!macroButtonsOpen.get()) {
                    saveSettings(settings.copy(macroButtonPanelVisible = false))
                }
            }

            // The HWND is shaped to the moving ImGui rectangles. Without
            // capture, Windows can alternate mouse ownership with the game as
            // the region advances frame-by-frame beneath the cursor.
            WindowStyler.setMouseCaptured(overlayHwnd, WindowBounds.isDraggingAnyWindow())
            val panelManipulationActive = WindowBounds.isManipulatingAnyWindow()
            if (panelManipulationActive) {
                // The previous frame's tight native region would clip fast
                // movement and resize expansion. Temporarily use the complete
                // transparent backing surface while the mouse is held.
                WindowStyler.setFullClientRegion(overlayHwnd)
            }

            imgui.finishFrame()
            uiKeyboardActive.set(
                ImGui.getIO().wantTextInput ||
                    ImGui.isAnyItemActive(),
            )
            cursorOverUi.set(WindowBounds.isCursorOverVisibleWindow())
            val visibleWindowRegions = WindowBounds.visibleWindowRegions()
            if (WindowBounds.isCursorOverVisibleWindow()) {
                WindowStyler.setFocusWithoutActivating(overlayHwnd)
            }

            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            imgui.renderDrawData()
            tracker.syncToTargetForPresentation()
            window.swapBuffers()
            tracker.syncToTargetForPresentation()

            // Reveal the new native region only after its pixels have reached
            // the front buffer. Expanding it before swap briefly exposed an
            // unpainted Windows surface as a white rectangle while dragging.
            if (!panelManipulationActive) {
                WindowStyler.setInteractiveRegions(overlayHwnd, visibleWindowRegions)
            }
        }
    } finally {
        trayController.close()
        hookManager.stop()
        macroEngine.stop()
        imgui.dispose()
        window.destroy()
    }
}
