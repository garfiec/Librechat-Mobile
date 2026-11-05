package com.garfiec.librechat.feature.chat.components.mcpapp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** A legacy MCP-UI resource's frame messages, as `@mcp-ui/client` sends them, and web's message text. */
class LegacyUiFrameHostTest {

    private val heights = mutableListOf<Int>()
    private val asked = mutableListOf<String>()
    private var decide: ((Boolean) -> Unit)? = null
    private val out = mutableListOf<String>()
    private val host = LegacyUiFrameHost(
        onHeight = { heights += it },
        onMessage = { text, reply ->
            asked += text
            decide = reply
        },
    ).also { it.send = { raw -> out += raw } }

    @Test
    fun `a size report resizes the frame`() {
        host.onFrameMessage("""{"type":"ui-size-change","payload":{"height":312.6,"width":300}}""")
        host.onFrameMessage("""{"type":"ui-size-change","payload":{"height":-1}}""")
        host.onFrameMessage("""{"type":"ui-size-change","payload":{}}""")
        assertThat(heights).containsExactly(312)
    }

    @Test
    fun `a prompt becomes web's message, acknowledged then answered once decided`() {
        host.onFrameMessage("""{"type":"prompt","messageId":"m1","payload":{"prompt":"Book the 9am"}}""")
        assertThat(asked.single()).isEqualTo(
            "The user clicked a button in an embedded UI Resource, and we got a message of type `prompt`.\n" +
                "The prompt is:\n\n```\nBook the 9am\n```\n\n" +
                "Execute the intention of the prompt that is mentioned in the message using the tools available to you.\n    ",
        )
        val received = Json.parseToJsonElement(out.single()).jsonObject
        assertThat(received["type"]!!.jsonPrimitive.content).isEqualTo("ui-message-received")
        assertThat(received["payload"]!!.jsonObject["messageId"]!!.jsonPrimitive.content).isEqualTo("m1")

        decide!!(false)
        val response = Json.parseToJsonElement(out.last()).jsonObject
        assertThat(response["type"]!!.jsonPrimitive.content).isEqualTo("ui-message-response")
        assertThat(response["payload"]!!.jsonObject.keys).containsExactly("messageId", "error")
    }

    @Test
    fun `a tool action carries its name and pretty-printed params`() {
        host.onFrameMessage("""{"type":"tool","payload":{"toolName":"book","params":{"slot":"9am","n":2}}}""")
        assertThat(asked.single()).contains(
            "The tool name is `book` and the params are:\n\n```json\n{\n  \"slot\": \"9am\",\n  \"n\": 2\n}\n```",
        )
        // No messageId: nothing to acknowledge.
        assertThat(out).isEmpty()
    }

    @Test
    fun `an intent without params prints undefined, as a JavaScript template would`() {
        host.onFrameMessage("""{"type":"intent","payload":{"intent":"refresh"}}""")
        assertThat(asked.single()).contains("The intent is `refresh` and the params are:\n\n```json\nundefined\n```")
    }

    @Test
    fun `links, notifications, unknown types and junk are ignored, as on web`() {
        for (raw in listOf(
            """{"type":"link","payload":{"url":"https://x"}}""",
            """{"type":"notify","payload":{"message":"hi"}}""",
            """{"type":"ui-lifecycle-iframe-ready"}""",
            """{"payload":{}}""",
            "not json",
        )) {
            host.onFrameMessage(raw)
        }
        assertThat(asked).isEmpty()
        assertThat(out).isEmpty()
    }
}
