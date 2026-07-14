package overlay.ui.panels

import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
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
import java.util.UUID

private val ACTION_TYPE_NAMES =
    arrayOf("Key Press", "Mouse Click", "Mouse Move", "Mouse Scroll", "Chat Send", "Delay", "Sequence")

/** Full macro CRUD: add/remove/enable/rename macros, capture triggers, and edit every action type. */
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

    fun replaceAll(newMacros: List<Macro>) {
        macros.clear()
        macros.addAll(newMacros)
        sync()
    }

    fun render() {
        ImGui.text("${macros.size} macro${if (macros.size == 1) "" else "s"}")
        ImGui.sameLine()
        if (ImGui.button("+ Add macro")) {
            macros.add(
                Macro(
                    id = UUID.randomUUID().toString(),
                    name = "New Macro",
                    trigger = TriggerSpec.Keyboard(KeyCombo(0)),
                    action = MacroAction.Delay(100),
                    suppressOriginalInput = defaultSuppressOriginalInput(),
                ),
            )
            sync()
        }
        ImGui.separator()

        var removeId: String? = null

        for (macro in macros.toList()) {
            ImGui.pushID(macro.id)

            val state = if (macro.enabled) "ON" else "OFF"
            val header = "[$state] ${macro.name}  -  ${VkNames.describe(macro.trigger)}###macro-${macro.id}"
            if (!ImGui.collapsingHeader(header, ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.popID()
                continue
            }

            val enabledRef = ImBoolean(macro.enabled)
            if (ImGui.checkbox("Enabled", enabledRef)) {
                replace(macro.id) { it.copy(enabled = enabledRef.get()) }
            }

            val showButtonRef = ImBoolean(macro.showInButtonPanel)
            if (ImGui.checkbox("Show in Macro Buttons panel", showButtonRef)) {
                replace(macro.id) { it.copy(showInButtonPanel = showButtonRef.get()) }
            }

            val nameBuf = nameBuffers.getOrPut(macro.id) { ImString(macro.name, 128) }
            ImGui.text("Name")
            ImGui.setNextItemWidth(-1f)
            ImGui.inputText("##name", nameBuf)
            if (nameBuf.get() != macro.name) {
                replace(macro.id) { it.copy(name = nameBuf.get()) }
            }

            if (ImGui.button("Delete")) {
                removeId = macro.id
            }

            ImGui.text("Trigger:")
            ImGui.sameLine()
            captureWidgetFor("${macro.id}.trigger").render("${macro.id}.trigger", macro.trigger)?.let { newTrigger ->
                replace(macro.id) { it.copy(trigger = newTrigger) }
            }

            val suppressRef = ImBoolean(macro.suppressOriginalInput)
            if (ImGui.checkbox("Suppress original input", suppressRef)) {
                replace(macro.id) { it.copy(suppressOriginalInput = suppressRef.get()) }
            }

            renderActionEditor(macro.id, "action", macro.action) { newAction ->
                replace(macro.id) { it.copy(action = newAction) }
            }

            ImGui.separator()
            ImGui.popID()
        }

        if (removeId != null) {
            macros.removeAll { it.id == removeId }
            captureWidgets.keys.removeAll { it.startsWith("$removeId.") }
            sync()
        }
    }

    private fun captureWidgetFor(widgetId: String) = captureWidgets.getOrPut(widgetId) { TriggerCaptureWidget(triggerRouter) }

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

        val typeIndex = ImInt(actionTypeIndex(action))
        ImGui.text("Action type")
        ImGui.setNextItemWidth(-1f)
        if (ImGui.combo("##action-type", typeIndex, ACTION_TYPE_NAMES)) {
            onChange(defaultActionFor(typeIndex.get()))
        }

        when (action) {
            is MacroAction.KeyPress -> {
                ImGui.text("Key:")
                ImGui.sameLine()
                captureWidgetFor("$widgetId.combo").render("$widgetId.combo", TriggerSpec.Keyboard(action.combo))?.let {
                    if (it is TriggerSpec.Keyboard) onChange(action.copy(combo = it.combo))
                }
                val holdMs = ImInt(action.holdMs.toInt())
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("Hold (ms)", holdMs)) {
                    onChange(action.copy(holdMs = holdMs.get().toLong().coerceAtLeast(0)))
                }
            }

            is MacroAction.MouseClick -> {
                val buttonIndex = ImInt(MouseButtonId.entries.indexOf(action.button))
                val buttonNames = MouseButtonId.entries.map { VkNames.describe(it) }.toTypedArray()
                ImGui.text("Button")
                ImGui.setNextItemWidth(-1f)
                if (ImGui.combo("##mouse-button", buttonIndex, buttonNames)) {
                    onChange(action.copy(button = MouseButtonId.entries[buttonIndex.get()]))
                }
                val relative = ImBoolean(action.relativeToTargetWindow)
                if (ImGui.checkbox("Relative to target window", relative)) {
                    onChange(action.copy(relativeToTargetWindow = relative.get()))
                }
                val x = ImInt(action.x ?: 0)
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("X", x)) onChange(action.copy(x = x.get()))
                val y = ImInt(action.y ?: 0)
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("Y", y)) onChange(action.copy(y = y.get()))
            }

            is MacroAction.MouseMove -> {
                val relative = ImBoolean(action.relativeToTargetWindow)
                if (ImGui.checkbox("Relative to target window", relative)) {
                    onChange(action.copy(relativeToTargetWindow = relative.get()))
                }
                val x = ImInt(action.x)
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("X", x)) onChange(action.copy(x = x.get()))
                val y = ImInt(action.y)
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("Y", y)) onChange(action.copy(y = y.get()))
            }

            is MacroAction.MouseScroll -> {
                val ticks = ImInt(action.ticks)
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("Ticks (+up/-down)", ticks)) onChange(action.copy(ticks = ticks.get()))
            }

            is MacroAction.ChatSend -> {
                val channelIndex = ImInt(ChatChannel.entries.indexOf(action.channel))
                ImGui.text("Channel")
                ImGui.setNextItemWidth(-1f)
                if (ImGui.combo("##chat-channel", channelIndex, arrayOf("Local", "Global (#)", "Trade (\$)", "Party (%)"))) {
                    onChange(action.copy(channel = ChatChannel.entries[channelIndex.get()]))
                }
                ImGui.text("Open chat key:")
                ImGui.sameLine()
                captureWidgetFor("$widgetId.openChat").render("$widgetId.openChat", TriggerSpec.Keyboard(action.openChat))?.let {
                    if (it is TriggerSpec.Keyboard) onChange(action.copy(openChat = it.combo))
                }
                val messageBuf = messageBuffers.getOrPut(widgetId) { ImString(action.message, 512) }
                ImGui.text("Message")
                ImGui.setNextItemWidth(-1f)
                ImGui.inputText("##chat-message", messageBuf)
                if (messageBuf.get() != action.message) {
                    onChange(action.copy(message = messageBuf.get()))
                }
                ImGui.textDisabled("Sends: ${action.channel.prefix}${action.message}")
                ImGui.text("Submit key:")
                ImGui.sameLine()
                captureWidgetFor("$widgetId.submit").render("$widgetId.submit", TriggerSpec.Keyboard(action.submit))?.let {
                    if (it is TriggerSpec.Keyboard) onChange(action.copy(submit = it.combo))
                }
                val preDelay = ImInt(action.preDelayMs.toInt())
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("Pre-delay (ms)", preDelay)) {
                    onChange(action.copy(preDelayMs = preDelay.get().toLong().coerceAtLeast(0)))
                }
                val postDelay = ImInt(action.postTypeDelayMs.toInt())
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("Post-type delay (ms)", postDelay)) {
                    onChange(action.copy(postTypeDelayMs = postDelay.get().toLong().coerceAtLeast(0)))
                }
            }

            is MacroAction.Delay -> {
                val ms = ImInt(action.ms.toInt())
                ImGui.setNextItemWidth(140f)
                if (ImGui.inputInt("Delay (ms)", ms)) onChange(action.copy(ms = ms.get().toLong().coerceAtLeast(0)))
            }

            is MacroAction.Sequence -> {
                ImGui.indent()
                for ((index, step) in action.steps.withIndex()) {
                    ImGui.pushID(index)
                    renderActionEditor(macroId, "$path.$index", step) { newStep ->
                        val newSteps = action.steps.toMutableList()
                        newSteps[index] = newStep
                        onChange(action.copy(steps = newSteps))
                    }
                    if (ImGui.button("Remove step")) {
                        val newSteps = action.steps.toMutableList()
                        newSteps.removeAt(index)
                        onChange(action.copy(steps = newSteps))
                    }
                    ImGui.popID()
                }
                if (ImGui.button("+ Add step")) {
                    onChange(action.copy(steps = action.steps + MacroAction.Delay(100)))
                }
                ImGui.unindent()
            }
        }

        ImGui.popID()
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
