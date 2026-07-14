package overlay.ui.panels

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import overlay.engine.MacroEngine
import overlay.engine.TriggerRouter
import overlay.engine.model.ChatChannel
import overlay.engine.model.KeyCombo
import overlay.engine.model.Macro
import overlay.engine.model.MacroAction
import overlay.engine.model.MacroProfile
import overlay.engine.model.MouseButtonId
import overlay.engine.model.TriggerSpec
import overlay.ui.VkNames
import overlay.ui.widgets.TriggerCaptureWidget
import overlay.window.ClipboardSupport
import java.util.UUID

private val ACTION_TYPE_NAMES =
    arrayOf("Key Press", "Mouse Click", "Mouse Move", "Mouse Scroll", "Chat Send", "Delay", "Sequence")
private val CHAT_CHANNEL_NAMES = arrayOf("Local", "Global (#)", "Trade (\$)", "Party (%)")

/** Two-pane macro browser with focused setup/action editing for the selected macro. */
class BindingEditorPanel(
    private val triggerRouter: TriggerRouter,
    private val macroEngine: MacroEngine,
    private val defaultSuppressOriginalInput: () -> Boolean = { false },
    private val onMacrosChanged: (List<Macro>) -> Unit = {},
) {
    private val macros = mutableListOf<Macro>()
    private val captureWidgets = mutableMapOf<String, TriggerCaptureWidget>()
    private val nameBuffers = mutableMapOf<String, ImString>()
    private val messageBuffers = mutableMapOf<String, ImString>()
    private var selectedMacroId: String? = null

    fun replaceAll(newMacros: List<Macro>) {
        triggerRouter.cancelCapture()
        captureWidgets.clear()
        nameBuffers.clear()
        messageBuffers.clear()
        macros.clear()
        macros.addAll(newMacros)
        selectedMacroId = newMacros.firstOrNull()?.id
        sync()
    }

    fun render() {
        val listWidth = (ImGui.getContentRegionAvailX() * 0.34f).coerceIn(125f, 165f)
        var removeId: String? = null

        ImGui.beginChild("macro-list", listWidth, 0f, true)
        ImGui.text("Macros")
        ImGui.sameLine()
        ImGui.textDisabled("(${macros.size})")

        if (ImGui.button("+ New Macro", -1f, 0f)) addMacro()
        ImGui.separator()

        for (macro in macros) {
            val state = if (macro.enabled) "ON" else "OFF"
            val name = macro.name.ifBlank { "Unnamed Macro" }
            if (ImGui.selectable("[$state] $name##list-${macro.id}", selectedMacroId == macro.id)) {
                selectMacro(macro.id)
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip()
                ImGui.text(name)
                ImGui.textDisabled("Trigger: ${VkNames.describe(macro.trigger)}")
                ImGui.endTooltip()
            }
        }
        ImGui.endChild()

        ImGui.sameLine()
        ImGui.beginChild("macro-details", 0f, 0f, true)
        val selected = macros.firstOrNull { it.id == selectedMacroId }
        if (selected == null) {
            ImGui.textDisabled("Select a macro to edit")
            ImGui.spacing()
            ImGui.textWrapped("Create a macro with the button on the left, then configure its trigger and action here.")
        } else {
            ImGui.pushID(selected.id)
            ImGui.text(selected.name.ifBlank { "Unnamed Macro" })
            ImGui.textDisabled("Trigger: ${VkNames.describe(selected.trigger)}")

            if (ImGui.button("Duplicate")) duplicateMacro(selected)
            ImGui.sameLine()
            if (ImGui.button("Delete")) removeId = selected.id
            ImGui.separator()

            if (ImGui.beginTabBar("macro-editor-tabs")) {
                if (ImGui.beginTabItem("Setup")) {
                    renderSetupEditor(selected)
                    ImGui.endTabItem()
                }
                if (ImGui.beginTabItem("Action")) {
                    renderActionEditor(selected.id, "action", selected.action) { newAction ->
                        replace(selected.id) { it.copy(action = newAction) }
                    }
                    ImGui.endTabItem()
                }
                ImGui.endTabBar()
            }
            ImGui.popID()
        }
        ImGui.endChild()

        removeId?.let(::removeMacro)
    }

    private fun renderSetupEditor(macro: Macro) {
        ImGui.separatorText("Identity")
        ImGui.text("Name")
        val nameBuffer = nameBuffers.getOrPut(macro.id) { ImString(macro.name, 128) }
        ImGui.setNextItemWidth(-1f)
        ImGui.inputText("##macro-name", nameBuffer)
        if (nameBuffer.get() != macro.name) {
            replace(macro.id) { it.copy(name = nameBuffer.get()) }
        }

        val enabled = ImBoolean(macro.enabled)
        if (ImGui.checkbox("Macro enabled", enabled)) {
            replace(macro.id) { it.copy(enabled = enabled.get()) }
        }

        val showButton = ImBoolean(macro.showInButtonPanel)
        if (ImGui.checkbox("Show manual button", showButton)) {
            replace(macro.id) { it.copy(showInButtonPanel = showButton.get()) }
        }

        ImGui.separatorText("Trigger")
        ImGui.text("Activation input")
        captureWidgetFor("${macro.id}.trigger")
            .render("${macro.id}.trigger", macro.trigger)
            ?.let { newTrigger -> replace(macro.id) { it.copy(trigger = newTrigger) } }

        val suppress = ImBoolean(macro.suppressOriginalInput)
        if (ImGui.checkbox("Suppress original input", suppress)) {
            replace(macro.id) { it.copy(suppressOriginalInput = suppress.get()) }
        }
        ImGui.textWrapped("Suppression prevents the trigger key or mouse button from also reaching the game.")
    }

    private fun addMacro() {
        val macro = Macro(
            id = UUID.randomUUID().toString(),
            name = "New Macro",
            trigger = TriggerSpec.Keyboard(KeyCombo(0)),
            action = MacroAction.Delay(100),
            suppressOriginalInput = defaultSuppressOriginalInput(),
        )
        macros.add(macro)
        selectedMacroId = macro.id
        sync()
    }

    private fun duplicateMacro(source: Macro) {
        val copy = source.copy(id = UUID.randomUUID().toString(), name = "${source.name} Copy")
        val sourceIndex = macros.indexOfFirst { it.id == source.id }
        macros.add((sourceIndex + 1).coerceAtMost(macros.size), copy)
        selectedMacroId = copy.id
        sync()
    }

    private fun removeMacro(id: String) {
        val index = macros.indexOfFirst { it.id == id }
        if (index < 0) return
        triggerRouter.cancelCapture()
        macros.removeAt(index)
        captureWidgets.keys.removeAll { it.startsWith(id) }
        nameBuffers.remove(id)
        messageBuffers.keys.removeAll { it.startsWith(id) }
        selectedMacroId = macros.getOrNull(index.coerceAtMost(macros.lastIndex))?.id
        sync()
    }

    private fun selectMacro(id: String) {
        if (selectedMacroId == id) return
        triggerRouter.cancelCapture()
        captureWidgets.clear()
        selectedMacroId = id
    }

    private fun captureWidgetFor(widgetId: String) =
        captureWidgets.getOrPut(widgetId) { TriggerCaptureWidget(triggerRouter) }

    private fun replace(id: String, transform: (Macro) -> Macro) {
        val index = macros.indexOfFirst { it.id == id }
        if (index >= 0) {
            macros[index] = transform(macros[index])
            sync()
        }
    }

    private fun sync() {
        macroEngine.setProfile(MacroProfile(id = "user", name = "User Profile", macros = macros.toList()))
        onMacrosChanged(macros.toList())
    }

    private fun renderActionEditor(macroId: String, path: String, action: MacroAction, onChange: (MacroAction) -> Unit) {
        val widgetId = "$macroId.$path"
        ImGui.pushID(widgetId)

        ImGui.text("Action type")
        val typeIndex = ImInt(actionTypeIndex(action))
        ImGui.setNextItemWidth(-1f)
        if (ImGui.combo("##action-type", typeIndex, ACTION_TYPE_NAMES)) {
            clearActionEditorState(widgetId)
            onChange(defaultActionFor(typeIndex.get()))
        }
        ImGui.spacing()

        when (action) {
            is MacroAction.KeyPress -> {
                ImGui.separatorText("Keyboard")
                ImGui.text("Key combination")
                captureWidgetFor("$widgetId.combo")
                    .render("$widgetId.combo", TriggerSpec.Keyboard(action.combo))
                    ?.let { if (it is TriggerSpec.Keyboard) onChange(action.copy(combo = it.combo)) }
                intField("Hold duration (ms)", "hold-ms", action.holdMs.toInt()) {
                    onChange(action.copy(holdMs = it.toLong().coerceAtLeast(0)))
                }
            }

            is MacroAction.MouseClick -> {
                ImGui.separatorText("Mouse click")
                ImGui.text("Button")
                val buttonIndex = ImInt(MouseButtonId.entries.indexOf(action.button))
                val buttonNames = MouseButtonId.entries.map(VkNames::describe).toTypedArray()
                ImGui.setNextItemWidth(-1f)
                if (ImGui.combo("##mouse-button", buttonIndex, buttonNames)) {
                    onChange(action.copy(button = MouseButtonId.entries[buttonIndex.get()]))
                }

                val moveFirst = ImBoolean(action.x != null && action.y != null)
                if (ImGui.checkbox("Move before clicking", moveFirst)) {
                    onChange(if (moveFirst.get()) action.copy(x = 0, y = 0) else action.copy(x = null, y = null))
                }
                if (moveFirst.get()) {
                    val relative = ImBoolean(action.relativeToTargetWindow)
                    if (ImGui.checkbox("Coordinates relative to target", relative)) {
                        onChange(action.copy(relativeToTargetWindow = relative.get()))
                    }
                    intField("X coordinate", "mouse-click-x", action.x ?: 0) { onChange(action.copy(x = it)) }
                    intField("Y coordinate", "mouse-click-y", action.y ?: 0) { onChange(action.copy(y = it)) }
                }
            }

            is MacroAction.MouseMove -> {
                ImGui.separatorText("Mouse position")
                val relative = ImBoolean(action.relativeToTargetWindow)
                if (ImGui.checkbox("Coordinates relative to target", relative)) {
                    onChange(action.copy(relativeToTargetWindow = relative.get()))
                }
                intField("X coordinate", "mouse-move-x", action.x) { onChange(action.copy(x = it)) }
                intField("Y coordinate", "mouse-move-y", action.y) { onChange(action.copy(y = it)) }
            }

            is MacroAction.MouseScroll -> {
                ImGui.separatorText("Mouse wheel")
                intField("Scroll ticks (+ up / - down)", "scroll-ticks", action.ticks) {
                    onChange(action.copy(ticks = it))
                }
            }

            is MacroAction.ChatSend -> {
                ImGui.separatorText("Chat message")
                ImGui.text("Channel")
                val channelIndex = ImInt(ChatChannel.entries.indexOf(action.channel))
                ImGui.setNextItemWidth(-1f)
                if (ImGui.combo("##chat-channel", channelIndex, CHAT_CHANNEL_NAMES)) {
                    onChange(action.copy(channel = ChatChannel.entries[channelIndex.get()]))
                }

                ImGui.text("Open chat input")
                captureWidgetFor("$widgetId.openChat")
                    .render("$widgetId.openChat", TriggerSpec.Keyboard(action.openChat))
                    ?.let { if (it is TriggerSpec.Keyboard) onChange(action.copy(openChat = it.combo)) }

                ImGui.text("Message")
                val messageBuffer = messageBuffers.getOrPut(widgetId) { ImString(action.message, 512) }
                ImGui.setNextItemWidth(-1f)
                ImGui.inputText("##chat-message", messageBuffer)
                if (ImGui.beginPopupContextItem("chat-message-context-menu")) {
                    val currentMessage = messageBuffer.get()
                    if (ImGui.menuItem("Copy message", "Ctrl+C", false, currentMessage.isNotEmpty())) {
                        ClipboardSupport.writeText(currentMessage)
                    }
                    if (ImGui.menuItem("Cut message", "Ctrl+X", false, currentMessage.isNotEmpty())) {
                        ClipboardSupport.writeText(currentMessage)
                        messageBuffer.set("")
                    }

                    val clipboardText = ClipboardSupport.readText()
                    if (ImGui.menuItem("Paste (replace)", "Ctrl+V", false, clipboardText.isNotEmpty())) {
                        messageBuffer.set(clipboardText)
                    }
                    if (ImGui.menuItem("Paste at end", "", false, clipboardText.isNotEmpty())) {
                        messageBuffer.set(currentMessage + clipboardText)
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Clear", "", false, currentMessage.isNotEmpty())) {
                        messageBuffer.set("")
                    }
                    ImGui.endPopup()
                }
                if (messageBuffer.get() != action.message) {
                    onChange(action.copy(message = messageBuffer.get()))
                }
                ImGui.textWrapped("Preview: ${action.channel.prefix}${messageBuffer.get()}")

                ImGui.text("Submit input")
                captureWidgetFor("$widgetId.submit")
                    .render("$widgetId.submit", TriggerSpec.Keyboard(action.submit))
                    ?.let { if (it is TriggerSpec.Keyboard) onChange(action.copy(submit = it.combo)) }

                intField("Delay before typing (ms)", "chat-pre-delay", action.preDelayMs.toInt()) {
                    onChange(action.copy(preDelayMs = it.toLong().coerceAtLeast(0)))
                }
                intField("Delay before submit (ms)", "chat-post-delay", action.postTypeDelayMs.toInt()) {
                    onChange(action.copy(postTypeDelayMs = it.toLong().coerceAtLeast(0)))
                }
            }

            is MacroAction.Delay -> {
                ImGui.separatorText("Wait")
                intField("Duration (ms)", "delay-ms", action.ms.toInt()) {
                    onChange(action.copy(ms = it.toLong().coerceAtLeast(0)))
                }
            }

            is MacroAction.Sequence -> {
                ImGui.separatorText("Sequence steps")
                if (action.steps.isEmpty()) ImGui.textDisabled("No steps yet")
                for ((index, step) in action.steps.withIndex()) {
                    val label = "Step ${index + 1}: ${actionTypeName(step)}##step-$index"
                    if (ImGui.treeNode(label)) {
                        renderActionEditor(macroId, "$path.$index", step) { newStep ->
                            val steps = action.steps.toMutableList()
                            steps[index] = newStep
                            onChange(action.copy(steps = steps))
                        }
                        if (ImGui.button("Remove step##$index")) {
                            val steps = action.steps.toMutableList()
                            steps.removeAt(index)
                            onChange(action.copy(steps = steps))
                        }
                        ImGui.treePop()
                    }
                }
                if (ImGui.button("+ Add step")) {
                    onChange(action.copy(steps = action.steps + MacroAction.Delay(100)))
                }
            }
        }

        ImGui.popID()
    }

    private fun intField(label: String, id: String, value: Int, onChange: (Int) -> Unit) {
        ImGui.text(label)
        val valueRef = ImInt(value)
        ImGui.setNextItemWidth(-1f)
        if (ImGui.inputInt("##$id", valueRef)) onChange(valueRef.get())
    }

    private fun clearActionEditorState(widgetId: String) {
        triggerRouter.cancelCapture()
        captureWidgets.keys.removeAll { it.startsWith(widgetId) }
        messageBuffers.keys.removeAll { it.startsWith(widgetId) }
    }

    private fun actionTypeIndex(action: MacroAction): Int = when (action) {
        is MacroAction.KeyPress -> 0
        is MacroAction.MouseClick -> 1
        is MacroAction.MouseMove -> 2
        is MacroAction.MouseScroll -> 3
        is MacroAction.ChatSend -> 4
        is MacroAction.Delay -> 5
        is MacroAction.Sequence -> 6
    }

    private fun actionTypeName(action: MacroAction): String = ACTION_TYPE_NAMES[actionTypeIndex(action)]

    private fun defaultActionFor(index: Int): MacroAction = when (index) {
        0 -> MacroAction.KeyPress(KeyCombo(0))
        1 -> MacroAction.MouseClick(MouseButtonId.LEFT)
        2 -> MacroAction.MouseMove(0, 0)
        3 -> MacroAction.MouseScroll(1)
        4 -> MacroAction.ChatSend(openChat = KeyCombo(0), message = "", submit = KeyCombo(0))
        5 -> MacroAction.Delay(100)
        else -> MacroAction.Sequence(emptyList())
    }
}
