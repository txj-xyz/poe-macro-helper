package overlay.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import overlay.engine.model.ChatChannel
import overlay.engine.model.KeyCombo
import overlay.engine.model.Macro
import overlay.engine.model.MacroAction
import overlay.engine.model.MacroProfile
import overlay.engine.model.TriggerSpec

class ProfileStoreTest {
    @Test
    fun `older profiles default to local chat and a visible panel button`() {
        val profile = MacroProfile(
            id = "legacy",
            name = "Legacy",
            macros = listOf(
                Macro(
                    id = "chat",
                    name = "Chat",
                    trigger = TriggerSpec.Keyboard(KeyCombo(0x70)),
                    action = MacroAction.ChatSend(
                        openChat = KeyCombo(0x0D),
                        message = "hello",
                        submit = KeyCombo(0x0D),
                    ),
                ),
            ),
        )

        val legacyJson = ProfileStore.encode(profile)
            .replace(Regex(",\\s*\"showInButtonPanel\"\\s*:\\s*true"), "")
            .replace(Regex(",\\s*\"channel\"\\s*:\\s*\"LOCAL\""), "")
        val decoded = ProfileStore.decode(legacyJson)
        val macro = decoded.macros.single()

        assertTrue(macro.showInButtonPanel)
        assertEquals(ChatChannel.LOCAL, (macro.action as MacroAction.ChatSend).channel)
    }

    @Test
    fun `chat channels expose the expected prefixes`() {
        assertEquals("", ChatChannel.LOCAL.prefix)
        assertEquals("#", ChatChannel.GLOBAL.prefix)
        assertEquals("\$", ChatChannel.TRADE.prefix)
        assertEquals("%", ChatChannel.PARTY.prefix)
    }
}
