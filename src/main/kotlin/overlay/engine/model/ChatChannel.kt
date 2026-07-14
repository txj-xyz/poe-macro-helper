package overlay.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatChannel(val prefix: String) {
    LOCAL(""),
    GLOBAL("#"),
    TRADE("\$"),
    PARTY("%"),
}
