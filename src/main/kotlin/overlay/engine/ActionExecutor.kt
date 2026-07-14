package overlay.engine

import overlay.engine.model.MacroAction
import overlay.win32.InputInjector

object ActionExecutor {
    fun execute(action: MacroAction, canExecute: () -> Boolean = { true }) {
        if (!canExecute()) return
        when (action) {
            is MacroAction.KeyPress -> InputInjector.pressKeyCombo(action.combo, action.holdMs)

            is MacroAction.MouseClick -> {
                if (action.x != null && action.y != null) InputInjector.moveTo(action.x, action.y)
                InputInjector.mouseClick(action.button)
            }

            is MacroAction.MouseMove -> InputInjector.moveTo(action.x, action.y)

            is MacroAction.MouseScroll -> InputInjector.mouseScroll(action.ticks)

            is MacroAction.ChatSend -> {
                InputInjector.pressKeyCombo(action.openChat)
                Thread.sleep(action.preDelayMs)
                if (!canExecute()) return
                InputInjector.typeUnicodeText(action.channel.prefix + action.message)
                Thread.sleep(action.postTypeDelayMs)
                if (!canExecute()) return
                InputInjector.pressKeyCombo(action.submit)
            }

            is MacroAction.Delay -> Thread.sleep(action.ms)

            is MacroAction.Sequence -> action.steps.forEach {
                if (!canExecute()) return
                execute(it, canExecute)
            }
        }
    }
}
