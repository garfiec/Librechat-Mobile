package com.garfiec.librechat.feature.chat.components.mcpapp

import com.garfiec.librechat.core.data.repository.McpAppFailure
import com.garfiec.librechat.core.data.repository.McpAppResult
import com.garfiec.librechat.core.data.repository.McpAppsRepository
import com.garfiec.librechat.core.model.mcp.McpAppsPolicy
import com.garfiec.librechat.core.model.mcp.UiResource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The host half of the MCP Apps protocol, driven as the relay page would drive it: the App's
 * JSON-RPC in, the host's replies out. No WebView.
 */
class McpAppHostTest {

    private class FakeRepository : McpAppsRepository {
        var validate: McpAppResult<Unit> = McpAppResult.Success(Unit)
        var read: McpAppResult<JsonObject> = McpAppResult.Failure(McpAppFailure.OTHER)
        var tool: McpAppResult<JsonObject> = McpAppResult.Success(
            Json.parseToJsonElement("""{"content":[{"type":"text","text":"ok"}]}""").jsonObject,
        )
        val toolCalls = mutableListOf<Pair<String, JsonObject>>()

        override suspend fun validate(serverName: String, serverBinding: String) = validate
        override suspend fun readResource(serverName: String, serverBinding: String, uri: String) = read
        override suspend fun listResources(serverName: String, serverBinding: String, cursor: String?) =
            McpAppResult.Success(JsonObject(mapOf("resources" to Json.parseToJsonElement("[]"))))
        override suspend fun listResourceTemplates(serverName: String, serverBinding: String, cursor: String?) =
            McpAppResult.Success(JsonObject(emptyMap()))
        override suspend fun callTool(
            serverName: String,
            serverBinding: String,
            toolName: String,
            arguments: JsonObject,
        ): McpAppResult<JsonObject> {
            toolCalls += toolName to arguments
            return tool
        }
    }

    private class Callbacks : McpAppHostCallbacks {
        var acceptMessage = true
        val sent = mutableListOf<String>()
        val links = mutableListOf<String>()
        val notices = mutableListOf<McpAppNotice>()
        var closes = 0
        override fun sendMessage(text: String): Boolean {
            sent += text
            return acceptMessage
        }
        override fun openLink(url: String) {
            links += url
        }
        override fun notice(notice: McpAppNotice, serverName: String) {
            notices += notice
        }
        override fun close() {
            closes++
        }
    }

    private val inlineApp = UiResource.from(
        Json.parseToJsonElement(
            """{"resourceId":"r1","uri":"ui://weather/forecast","mimeType":"text/html;profile=mcp-app",
            "text":"<!doctype html><p>app</p>","serverName":"weather","toolName":"show_forecast",
            "serverBinding":"v1.abc","toolArgs":{"city":"Seattle","days":5},
            "content":[{"type":"text","text":"5-day forecast"}],"structuredContent":{"days":5},
            "csp":{"connectDomains":["https://api.weather.example"]}}""",
        ),
    )!!

    private class Harness(
        scope: CoroutineScope,
        resource: UiResource,
        policy: McpAppsPolicy = McpAppsPolicy(enabled = true, legacyHtmlEnabled = true),
    ) {
        val repository = FakeRepository()
        val callbacks = Callbacks()
        val out = mutableListOf<JsonObject>()
        val host = McpAppHost(
            resource = resource,
            policy = policy,
            repository = repository,
            scope = scope,
            callbacks = callbacks,
            hostInfoVersion = "1.0",
            hostContext = JsonObject(mapOf("theme" to JsonPrimitive("light"))),
        ).also { it.send = { raw -> out += Json.parseToJsonElement(raw).jsonObject } }

        fun app(json: String) = host.onRelayMessage(json)
        fun reply(id: Int) = out.last { (it["id"] as? JsonPrimitive)?.content == id.toString() }
        fun methods() = out.mapNotNull { it["method"]?.jsonPrimitive?.content }
    }

    private fun TestScope.loaded(policy: McpAppsPolicy = McpAppsPolicy(enabled = true, legacyHtmlEnabled = true)): Harness {
        val h = Harness(backgroundScope, inlineApp, policy)
        h.host.start()
        runCurrent()
        h.app("""{"jsonrpc":"2.0","method":"ui/notifications/sandbox-proxy-ready","params":{}}""")
        h.app(
            """{"jsonrpc":"2.0","id":1,"method":"ui/initialize","params":{"protocolVersion":"2026-01-26",""" +
                """"appInfo":{"name":"a","version":"1"},"appCapabilities":{}}}""",
        )
        h.app("""{"jsonrpc":"2.0","method":"ui/notifications/initialized"}""")
        return h
    }

    @Test
    fun `pins the spec 2026-01-26 method names`() {
        assertThat(McpAppProtocol.PROTOCOL_VERSION).isEqualTo("2026-01-26")
        assertThat(
            listOf(
                McpAppProtocol.INITIALIZE, McpAppProtocol.INITIALIZED, McpAppProtocol.OPEN_LINK,
                McpAppProtocol.MESSAGE, McpAppProtocol.REQUEST_DISPLAY_MODE, McpAppProtocol.SIZE_CHANGED,
                McpAppProtocol.REQUEST_TEARDOWN, McpAppProtocol.TOOL_INPUT, McpAppProtocol.TOOL_RESULT,
                McpAppProtocol.HOST_CONTEXT_CHANGED, McpAppProtocol.RESOURCE_TEARDOWN,
                McpAppProtocol.SANDBOX_PROXY_READY, McpAppProtocol.SANDBOX_RESOURCE_READY,
            ),
        ).containsExactly(
            "ui/initialize", "ui/notifications/initialized", "ui/open-link", "ui/message",
            "ui/request-display-mode", "ui/notifications/size-changed", "ui/notifications/request-teardown",
            "ui/notifications/tool-input", "ui/notifications/tool-result",
            "ui/notifications/host-context-changed", "ui/resource-teardown",
            "ui/notifications/sandbox-proxy-ready", "ui/notifications/sandbox-resource-ready",
        ).inOrder()
    }

    @Test
    fun `inline html is validated, delivered once, then input and result follow initialize`() = runTest {
        val h = loaded()
        h.app("""{"jsonrpc":"2.0","method":"ui/notifications/sandbox-proxy-ready","params":{}}""")

        val resourceReady = h.out.filter { it["method"]?.jsonPrimitive?.content == McpAppProtocol.SANDBOX_RESOURCE_READY }
        assertThat(resourceReady).hasSize(1)
        assertThat(resourceReady.single()["params"]!!.jsonObject["sandbox"]!!.jsonPrimitive.content)
            .isEqualTo("allow-scripts allow-forms")

        val init = h.reply(1)["result"]!!.jsonObject
        assertThat(init["protocolVersion"]!!.jsonPrimitive.content).isEqualTo("2026-01-26")
        assertThat(init["hostCapabilities"]!!.jsonObject.keys)
            .containsExactly("openLinks", "logging", "serverTools", "serverResources", "message")
        assertThat(init["hostContext"]!!.jsonObject["theme"]!!.jsonPrimitive.content).isEqualTo("light")

        assertThat(h.methods().takeLast(2)).containsExactly(McpAppProtocol.TOOL_INPUT, McpAppProtocol.TOOL_RESULT).inOrder()
        val input = h.out.first { it["method"]?.jsonPrimitive?.content == McpAppProtocol.TOOL_INPUT }
        assertThat(input["params"]!!.jsonObject["arguments"]!!.jsonObject["city"]!!.jsonPrimitive.content).isEqualTo("Seattle")
        assertThat(h.host.state.value).isInstanceOf(McpAppViewState.Ready::class.java)
    }

    @Test
    fun `a tool call waits for approval, then runs natively and answers the App`() = runTest {
        val h = loaded()
        h.app(
            """{"jsonrpc":"2.0","id":7,"method":"tools/call",""" +
                """"params":{"name":"refresh_forecast","arguments":{"city":"Seattle","units":"metric"}}}""",
        )
        runCurrent()
        val pending = h.host.pendingAction.value as McpAppAction.ToolCall
        assertThat(pending.toolName).isEqualTo("refresh_forecast")
        assertThat(pending.argumentsText).isEqualTo("""{"city":"Seattle","units":"metric"}""")
        assertThat(h.repository.toolCalls).isEmpty()

        h.host.approve()
        runCurrent()
        assertThat(h.repository.toolCalls.single().first).isEqualTo("refresh_forecast")
        assertThat(h.reply(7)["result"]!!.jsonObject["content"].toString()).contains("ok")
        assertThat(h.host.pendingAction.value).isNull()
    }

    @Test
    fun `cancel fails the request and pauses every later one`() = runTest {
        val h = loaded()
        h.app("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"t","arguments":{}}}""")
        runCurrent()
        h.host.cancelAction()
        runCurrent()
        assertThat(h.reply(7)["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content).isEqualTo("true")

        h.app("""{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"t","arguments":{}}}""")
        runCurrent()
        assertThat(h.host.pendingAction.value).isNull()
        assertThat(h.reply(8)["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(h.repository.toolCalls).isEmpty()
    }

    @Test
    fun `arguments over the preview limit fail closed without asking`() = runTest {
        val h = loaded(McpAppsPolicy(enabled = true, legacyHtmlEnabled = true, maxActionPreviewChars = 10))
        h.app("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"t","arguments":{"long":"0123456789"}}}""")
        runCurrent()
        assertThat(h.host.pendingAction.value).isNull()
        assertThat(h.reply(7)["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `the queue holds at most maxPendingActions`() = runTest {
        val h = loaded(McpAppsPolicy(enabled = true, legacyHtmlEnabled = true, maxPendingActions = 1))
        h.app("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"a","arguments":{}}}""")
        h.app("""{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"b","arguments":{}}}""")
        runCurrent()
        assertThat((h.host.pendingAction.value as McpAppAction.ToolCall).toolName).isEqualTo("a")
        assertThat(h.reply(8)["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `a message is sent as text only after approval, and closes the panel`() = runTest {
        val h = loaded()
        h.app(
            """{"jsonrpc":"2.0","id":9,"method":"ui/message","params":{"role":"user",""" +
                """"content":[{"type":"text","text":"Plan a hike"},{"type":"image","data":"x"}]}}""",
        )
        runCurrent()
        assertThat((h.host.pendingAction.value as McpAppAction.Message).text).isEqualTo("Plan a hike")
        h.host.approve()
        runCurrent()
        assertThat(h.callbacks.sent).containsExactly("Plan a hike")
        assertThat(h.reply(9)["result"]!!.jsonObject).isEmpty()
        assertThat(h.callbacks.closes).isEqualTo(1)
    }

    @Test
    fun `a message the chat refuses while a reply runs is an error to the App`() = runTest {
        val h = loaded()
        h.callbacks.acceptMessage = false
        h.app("""{"jsonrpc":"2.0","id":9,"method":"ui/message","params":{"role":"user","content":[{"type":"text","text":"hi"}]}}""")
        runCurrent()
        h.host.approve()
        runCurrent()
        assertThat(h.reply(9)["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(h.callbacks.closes).isEqualTo(0)
    }

    @Test
    fun `links open only on a host the delivered csp declared`() = runTest {
        val h = loaded()
        h.app("""{"jsonrpc":"2.0","id":3,"method":"ui/open-link","params":{"url":"https://api.weather.example/radar"}}""")
        h.app("""{"jsonrpc":"2.0","id":4,"method":"ui/open-link","params":{"url":"https://evil.example/?d=secret"}}""")
        assertThat(h.callbacks.links).containsExactly("https://api.weather.example/radar")
        assertThat(h.reply(4)["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `resource reads need no approval, unknown methods get method-not-found`() = runTest {
        val h = loaded()
        h.app("""{"jsonrpc":"2.0","id":5,"method":"resources/list","params":{}}""")
        h.app("""{"jsonrpc":"2.0","id":6,"method":"sampling/createMessage","params":{}}""")
        runCurrent()
        assertThat(h.reply(5)["result"]!!.jsonObject.keys).contains("resources")
        assertThat(h.reply(6)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content).isEqualTo("-32601")
    }

    @Test
    fun `nothing initializing within the budget times out`() = runTest {
        val h = Harness(backgroundScope, inlineApp)
        h.host.start()
        runCurrent()
        assertThat(h.host.state.value).isInstanceOf(McpAppViewState.Loading::class.java)
        advanceTimeBy(McpAppHost.REVEAL_BUDGET_MS + 1)
        runCurrent()
        assertThat(h.host.state.value).isEqualTo(McpAppViewState.Failed(McpAppLoadFailure.TIMED_OUT))
    }

    @Test
    fun `a 400 from validate is a stale binding with no retry`() = runTest {
        val h = Harness(backgroundScope, inlineApp)
        h.repository.validate = McpAppResult.Failure(McpAppFailure.REJECTED)
        h.host.start()
        runCurrent()
        assertThat(h.host.state.value).isEqualTo(McpAppViewState.Failed(McpAppLoadFailure.BINDING_STALE))
        assertThat(McpAppLoadFailure.BINDING_STALE.retryable).isFalse()
    }

    @Test
    fun `apps switched off on the server close the panel and say so`() = runTest {
        val h = Harness(backgroundScope, inlineApp)
        h.repository.validate = McpAppResult.Failure(McpAppFailure.APPS_DISABLED)
        h.host.start()
        runCurrent()
        assertThat(h.callbacks.notices).containsExactly(McpAppNotice.APPS_DISABLED)
        assertThat(h.callbacks.closes).isEqualTo(1)
    }

    @Test
    fun `a uri-only App reads its document and runs under its content-level csp`() = runTest {
        val uriOnly = inlineApp.copy(text = null, csp = null)
        val h = Harness(backgroundScope, uriOnly)
        h.repository.read = McpAppResult.Success(
            Json.parseToJsonElement(
                """{"contents":[{"uri":"ui://weather/forecast","mimeType":"text/html;profile=mcp-app","text":"<p>read</p>",
                "_meta":{"ui":{"csp":{"connectDomains":["https://read.example"]}}}}]}""",
            ).jsonObject,
        )
        h.host.start()
        runCurrent()
        val page = (h.host.state.value as McpAppViewState.Loading).page
        assertThat(page.shellHtml).contains("connect-src https://read.example")
        h.app("""{"jsonrpc":"2.0","method":"ui/notifications/sandbox-proxy-ready","params":{}}""")
        assertThat(h.out.single()["params"]!!.jsonObject["html"]!!.jsonPrimitive.content).isEqualTo("<p>read</p>")
    }

    @Test
    fun `a missing binding never calls the server`() = runTest {
        val h = Harness(backgroundScope, inlineApp.copy(serverBinding = null))
        h.host.start()
        runCurrent()
        assertThat(h.host.state.value).isEqualTo(McpAppViewState.Failed(McpAppLoadFailure.COULD_NOT_LOAD))
    }

    @Test
    fun `close cancels the pending action and sends nothing after teardown`() = runTest {
        val h = loaded()
        h.app("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"t","arguments":{}}}""")
        runCurrent()
        h.host.close()
        runCurrent()
        assertThat(h.methods().last()).isEqualTo(McpAppProtocol.RESOURCE_TEARDOWN)
        assertThat(h.host.pendingAction.value).isNull()
        assertThat(h.repository.toolCalls).isEmpty()
        val before = h.out.size
        h.app("""{"jsonrpc":"2.0","id":8,"method":"ping"}""")
        assertThat(h.out.size).isEqualTo(before)
    }
}
