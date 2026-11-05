package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.garfiec.librechat.core.model.mcp.UiResource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a reply's `\ui{…}` markers draw from. [byId] is every UI resource in the conversation, by
 * `resourceId` (upstream `useConversationUIResources`); [enabled] is `mcpApps.legacyHtmlEnabled`.
 * [onMessage] asks the user to send a resource's action as a chat message, and reports whether it
 * was sent.
 */
@Immutable
data class LegacyUiResources(
    val enabled: Boolean,
    val byId: Map<String, UiResource>,
    val onMessage: (text: String, reply: (sent: Boolean) -> Unit) -> Unit,
)

/** Null where markers can't render (the shared viewer, previews): they are stripped and draw nothing. */
val LocalLegacyUiResources = staticCompositionLocalOf<LegacyUiResources?> { null }

private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/**
 * Upstream `handleUIAction` (`client/src/utils/index.ts`): the chat message a legacy MCP-UI action
 * becomes. `intent`, `tool` and `prompt` are sent; `link`, `notify` and anything else are ignored
 * (null), as on web. The wording is upstream's, byte for byte, trailing indentation included.
 */
internal fun legacyUiActionMessage(type: String?, payload: JsonObject?): String? {
    val params = payload?.get("params")
    return when (type) {
        "intent" -> """The user clicked a button in an embedded UI Resource, and we got a message of type `intent`.
The intent is `${jsText(payload?.get("intent"))}` and the params are:

```json
${jsStringify(params)}
```

Execute the intent that is mentioned in the message using the tools available to you.
    """
        "tool" -> """The user clicked a button in an embedded UI Resource, and we got a message of type `tool`.
The tool name is `${jsText(payload?.get("toolName"))}` and the params are:

```json
${jsStringify(params)}
```

Execute the tool that is mentioned in the message using the tools available to you.
    """
        "prompt" -> """The user clicked a button in an embedded UI Resource, and we got a message of type `prompt`.
The prompt is:

```
${jsText(payload?.get("prompt"))}
```

Execute the intention of the prompt that is mentioned in the message using the tools available to you.
    """
        else -> null
    }
}

/** A value as a JavaScript template literal prints it. */
private fun jsText(value: JsonElement?): String = when (value) {
    null -> "undefined"
    is JsonPrimitive -> value.content
    is JsonObject -> "[object Object]"
    else -> value.toString()
}

/** `JSON.stringify(value, null, 2)`; `undefined` for a missing value. */
private fun jsStringify(value: JsonElement?): String = when (value) {
    null -> "undefined"
    JsonNull -> "null"
    else -> prettyJson.encodeToString(JsonElement.serializer(), value)
}

/**
 * The host side of one legacy resource's frame (`@mcp-ui/client`'s `HTMLResourceRenderer`
 * message handling): `ui-size-change` resizes it; `tool` / `intent` / `prompt` become a message
 * the user confirms; each action is acknowledged with `ui-message-received` and answered with
 * `ui-message-response` once decided. [send] is set by the web view while it is attached.
 */
internal class LegacyUiFrameHost(
    private val onHeight: (cssPx: Int) -> Unit,
    private val onMessage: (text: String, reply: (sent: Boolean) -> Unit) -> Unit,
) {
    var send: ((String) -> Unit)? = null

    fun onFrameMessage(raw: String) {
        val msg = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        val type = (msg["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return
        val payload = msg["payload"] as? JsonObject
        if (type == SIZE_CHANGE) {
            val height = (payload?.get("height") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return
            if (height.isFinite() && height > 0) onHeight(height.toInt())
            return
        }
        val text = legacyUiActionMessage(type, payload) ?: return
        val messageId = msg["messageId"]
        reply(RECEIVED, messageId, error = null)
        onMessage(text) { sent -> reply(RESPONSE, messageId, error = if (sent) null else "The message was not sent.") }
    }

    private fun reply(type: String, messageId: JsonElement?, error: String?) {
        if (messageId == null) return
        send?.invoke(
            buildJsonObject {
                put("type", type)
                put(
                    "payload",
                    buildJsonObject {
                        put("messageId", messageId)
                        if (error != null) put("error", error)
                    },
                )
            }.toString(),
        )
    }

    private companion object {
        const val SIZE_CHANGE = "ui-size-change"
        const val RECEIVED = "ui-message-received"
        const val RESPONSE = "ui-message-response"
    }
}
