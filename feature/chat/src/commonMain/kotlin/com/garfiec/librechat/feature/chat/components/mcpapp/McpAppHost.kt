package com.garfiec.librechat.feature.chat.components.mcpapp

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.repository.McpAppFailure
import com.garfiec.librechat.core.data.repository.McpAppResult
import com.garfiec.librechat.core.data.repository.McpAppsRepository
import com.garfiec.librechat.core.model.mcp.McpAppCspDeclaration
import com.garfiec.librechat.core.model.mcp.McpAppsPolicy
import com.garfiec.librechat.core.model.mcp.UiResource
import com.garfiec.librechat.core.model.mcp.isAllowedAppLink
import com.garfiec.librechat.core.model.mcp.pickAppDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Where an open App is in its life. */
sealed interface McpAppViewState {
    /** Resolving the document (validate or read) before the page can load. */
    data object Resolving : McpAppViewState

    /** The relay page is built and loading; [page] is what the WebView loads. */
    data class Loading(val page: McpAppPage) : McpAppViewState

    /** The App initialized or reported its size: the overlay comes off. */
    data class Ready(val page: McpAppPage) : McpAppViewState

    data class Failed(val reason: McpAppLoadFailure) : McpAppViewState
}

enum class McpAppLoadFailure(val retryable: Boolean) {
    /** The read or validate failed for a reason worth retrying ("could not be loaded"). */
    COULD_NOT_LOAD(retryable = true),

    /** Nothing initialized within [McpAppHost.REVEAL_BUDGET_MS] ("failed to load"). */
    TIMED_OUT(retryable = true),

    /** A 400 from validate or read: the server's binding changed. Run the tool again. */
    BINDING_STALE(retryable = false),

    /** The WebView can't bridge (Android without WEB_MESSAGE_LISTENER). */
    UNSUPPORTED(retryable = false),
}

/** Something the screen reports outside the panel. */
enum class McpAppNotice { APPS_DISABLED, REAUTH_REQUIRED, BUSY }

/** An action the App asked for that the user must approve first. Text is shown whole, never cut. */
sealed interface McpAppAction {
    val serverName: String

    data class ToolCall(override val serverName: String, val toolName: String, val argumentsText: String) : McpAppAction
    data class Message(override val serverName: String, val text: String) : McpAppAction
}

/** What the host needs from the screen. */
interface McpAppHostCallbacks {
    /** Sends [text] as a user turn; false when the chat refused it (a reply is running). */
    fun sendMessage(text: String): Boolean
    fun openLink(url: String)
    fun notice(notice: McpAppNotice, serverName: String)

    /** The App asked to go away, or a message was sent: close the panel. */
    fun close()
}

/**
 * The host half of the MCP Apps protocol for one open App, in Kotlin rather than vendored
 * ext-apps JavaScript (which has no browser build): it answers the App's JSON-RPC through the
 * relay page, makes every server call natively (no token enters the WebView), and holds the
 * approval queue for tool calls and messages, upstream's `useAppBridge` + `approval.tsx`.
 *
 * The relay page is the only thing it talks to: [onRelayMessage] takes what the page posted,
 * [send] (set by the WebView) delivers to it. A new attempt (Retry) is a new host; a new web view
 * for the same attempt (Android recreating its Activity on rotation) calls [pageAttached].
 */
class McpAppHost(
    private val resource: UiResource,
    private val policy: McpAppsPolicy,
    private val repository: McpAppsRepository,
    private val scope: CoroutineScope,
    private val callbacks: McpAppHostCallbacks,
    private val hostInfoVersion: String,
    hostContext: JsonObject,
) {
    private val serverName = resource.serverName.orEmpty()
    private val serverBinding = resource.serverBinding.orEmpty()

    private val _state = MutableStateFlow<McpAppViewState>(McpAppViewState.Resolving)
    val state: StateFlow<McpAppViewState> = _state.asStateFlow()

    private val _pendingAction = MutableStateFlow<McpAppAction?>(null)

    /** The action awaiting the user's decision (the head of the queue), or null. */
    val pendingAction: StateFlow<McpAppAction?> = _pendingAction.asStateFlow()

    /** Set by the WebView once the relay page can receive. */
    var send: ((String) -> Unit)? = null

    private var hostContext: JsonObject = hostContext
    private var document: String? = null
    private var effectiveCsp = McpAppCspDeclaration()
    private var resourceSent = false
    private var initialized = false
    private var revealed = false
    private var closed = false

    /** Bumped by [pageAttached]: an answer to a request the previous page made is not delivered. */
    private var page = 0

    /** Cancel on the visible request pauses the View until it is closed and reopened. */
    private var paused = false
    private val queue = ArrayDeque<Pair<McpAppAction, CompletableDeferred<Boolean>>>()
    private val jobs = mutableListOf<Job>()
    private var nextRequestId = 1

    fun start() {
        if (serverBinding.isEmpty() || serverName.isEmpty()) {
            _state.value = McpAppViewState.Failed(McpAppLoadFailure.COULD_NOT_LOAD)
            return
        }
        launchTracked { resolve() }
    }

    private suspend fun resolve() {
        val inline = resource.inlineHtml()
        val html: String
        val csp: JsonElement?
        if (inline != null) {
            when (val answer = repository.validate(serverName, serverBinding)) {
                is McpAppResult.Failure -> return fail(answer.failure)
                is McpAppResult.Success -> Unit
            }
            html = inline
            csp = resource.csp
        } else {
            val uri = resource.uri ?: return fail(McpAppFailure.REJECTED)
            when (val answer = repository.readResource(serverName, serverBinding, uri)) {
                is McpAppResult.Failure -> return fail(answer.failure)
                is McpAppResult.Success -> {
                    val doc = pickAppDocument(answer.data, uri)
                        ?: return run { _state.value = McpAppViewState.Failed(McpAppLoadFailure.COULD_NOT_LOAD) }
                    html = doc.html
                    csp = doc.csp
                }
            }
        }
        if (closed) return
        // Upstream withSandboxCsp: an oversized declaration is not delivered at all, so the View
        // runs under the default policy and no link is allowed.
        val normalized = McpAppCspDeclaration.normalize(csp, policy.cspLimits)
        effectiveCsp = if (normalized.serializedLength() > policy.cspLimits.maxSerializedLength) {
            McpAppCspDeclaration()
        } else {
            normalized
        }
        document = html
        _state.value = McpAppViewState.Loading(McpAppRelayPage.build(effectiveCsp))
        launchTracked {
            delay(REVEAL_BUDGET_MS)
            if (!revealed && !closed && _state.value is McpAppViewState.Loading) {
                _state.value = McpAppViewState.Failed(McpAppLoadFailure.TIMED_OUT)
            }
        }
    }

    private fun fail(failure: McpAppFailure) {
        if (closed) return
        when (failure) {
            McpAppFailure.REJECTED -> _state.value = McpAppViewState.Failed(McpAppLoadFailure.BINDING_STALE)
            McpAppFailure.APPS_DISABLED -> {
                callbacks.notice(McpAppNotice.APPS_DISABLED, serverName)
                callbacks.close()
            }
            McpAppFailure.REAUTH_REQUIRED -> {
                callbacks.notice(McpAppNotice.REAUTH_REQUIRED, serverName)
                _state.value = McpAppViewState.Failed(McpAppLoadFailure.COULD_NOT_LOAD)
            }
            else -> _state.value = McpAppViewState.Failed(McpAppLoadFailure.COULD_NOT_LOAD)
        }
    }

    /**
     * A web view is about to load the relay page. On a replacement (the first one went with a
     * configuration change) the App starts over there: its document goes out again on the new
     * relay's ready and it initializes again. Approvals the old page was waiting on are dropped
     * unasked, and answers to its requests are not delivered (the new App never made them and may
     * reuse their ids). Not a pause: the user cancelled nothing.
     */
    fun pageAttached() {
        if (closed || !resourceSent) return
        page++
        resourceSent = false
        initialized = false
        queue.toList().forEach { it.second.complete(false) }
        queue.clear()
        _pendingAction.update { null }
    }

    /** The WebView could not set up an isolated bridge; nothing runs. */
    fun bridgeUnavailable() {
        _state.value = McpAppViewState.Failed(McpAppLoadFailure.UNSUPPORTED)
    }

    /** One message the relay page posted: the relay's own, or the App's, already source-checked. */
    fun onRelayMessage(raw: String) {
        if (closed) return
        val msg = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        if ((msg["jsonrpc"] as? JsonPrimitive)?.content != McpAppProtocol.JSONRPC) return
        val method = (msg["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val id = msg["id"]
        val params = msg["params"] as? JsonObject ?: JsonObject(emptyMap())
        when {
            method == McpAppProtocol.SANDBOX_PROXY_READY -> sendResource()
            method != null && id != null -> handleRequest(id, method, params)
            method != null -> handleNotification(method, params)
            // A response to our own request (teardown); nothing waits on it.
            else -> Unit
        }
    }

    private fun sendResource() {
        val html = document ?: return
        if (resourceSent) return
        resourceSent = true
        post(
            notification(
                McpAppProtocol.SANDBOX_RESOURCE_READY,
                buildJsonObject {
                    put("html", html)
                    put("sandbox", McpAppProtocol.SANDBOX_TOKENS)
                },
            ),
        )
    }

    private fun handleNotification(method: String, params: JsonObject) {
        when (method) {
            McpAppProtocol.INITIALIZED -> {
                if (initialized) return
                initialized = true
                reveal()
                // Tool input exactly once, then the result, as upstream's oninitialized does.
                post(
                    notification(
                        McpAppProtocol.TOOL_INPUT,
                        buildJsonObject { put("arguments", resource.toolInputArguments()) },
                    ),
                )
                post(notification(McpAppProtocol.TOOL_RESULT, resource.appToolResult()))
            }
            McpAppProtocol.SIZE_CHANGED -> reveal()
            McpAppProtocol.REQUEST_TEARDOWN -> {
                pauseActions()
                callbacks.close()
            }
            McpAppProtocol.LOGGING_MESSAGE -> Logger.d("McpApp") { "App log: ${params["level"]}" }
            else -> Unit
        }
    }

    private fun reveal() {
        if (revealed) return
        revealed = true
        val current = _state.value
        if (current is McpAppViewState.Loading) _state.value = McpAppViewState.Ready(current.page)
    }

    private fun handleRequest(id: JsonElement, method: String, params: JsonObject) {
        val askedOn = page
        when (method) {
            McpAppProtocol.INITIALIZE -> respond(askedOn, id, initializeResult(params))
            McpAppProtocol.PING -> respond(askedOn, id, JsonObject(emptyMap()))
            McpAppProtocol.REQUEST_DISPLAY_MODE -> respond(askedOn, id, buildJsonObject { put("mode", DISPLAY_MODE) })
            McpAppProtocol.OPEN_LINK -> {
                val url = (params["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (url != null && isAllowedAppLink(url, effectiveCsp)) {
                    callbacks.openLink(url)
                    respond(askedOn, id, JsonObject(emptyMap()))
                } else {
                    respond(askedOn, id, IS_ERROR)
                }
            }
            McpAppProtocol.TOOLS_CALL -> launchTracked { respond(askedOn, id, callTool(params)) }
            McpAppProtocol.MESSAGE -> launchTracked {
                val sent = sendMessage(params)
                respond(askedOn, id, if (sent) JsonObject(emptyMap()) else IS_ERROR)
                // Answered first, then closed: the reply reaches the App before teardown.
                if (sent) callbacks.close()
            }
            McpAppProtocol.RESOURCES_READ -> {
                val uri = (params["uri"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (uri == null) {
                    respondError(askedOn, id, McpAppProtocol.ERROR_INVALID_PARAMS, "uri is required")
                } else {
                    launchTracked { respondWith(askedOn, id, repository.readResource(serverName, serverBinding, uri)) }
                }
            }
            McpAppProtocol.RESOURCES_LIST -> launchTracked {
                respondWith(askedOn, id, repository.listResources(serverName, serverBinding, params.cursor()))
            }
            McpAppProtocol.RESOURCE_TEMPLATES_LIST -> launchTracked {
                respondWith(askedOn, id, repository.listResourceTemplates(serverName, serverBinding, params.cursor()))
            }
            else -> respondError(askedOn, id, McpAppProtocol.ERROR_METHOD_NOT_FOUND, "Method not found")
        }
    }

    private fun initializeResult(params: JsonObject): JsonObject {
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.content
        return buildJsonObject {
            put(
                "protocolVersion",
                if (requested == McpAppProtocol.PROTOCOL_VERSION) requested else McpAppProtocol.PROTOCOL_VERSION,
            )
            putJsonObject("hostInfo") {
                put("name", HOST_NAME)
                put("version", hostInfoVersion)
            }
            putJsonObject("hostCapabilities") {
                putJsonObject("openLinks") {}
                putJsonObject("logging") {}
                putJsonObject("serverTools") {}
                putJsonObject("serverResources") {}
                putJsonObject("message") { putJsonObject("text") {} }
            }
            put("hostContext", hostContext)
        }
    }

    /** Re-sends the host context (theme or size changed), once the App is listening. */
    fun updateHostContext(context: JsonObject) {
        if (context == hostContext) return
        hostContext = context
        if (initialized && !closed) post(notification(McpAppProtocol.HOST_CONTEXT_CHANGED, context))
    }

    private suspend fun callTool(params: JsonObject): JsonObject {
        val toolName = (params["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return TOOL_ERROR
        val arguments = when (val raw = params["arguments"]) {
            null -> JsonObject(emptyMap())
            is JsonObject -> raw
            else -> return TOOL_ERROR
        }
        // Snapshot the text shown; over the limit fails closed, never truncated.
        val argumentsText = arguments.toString()
        if (argumentsText.length > policy.maxActionPreviewChars) return TOOL_ERROR
        if (!requestApproval(McpAppAction.ToolCall(serverName, toolName, argumentsText))) return TOOL_ERROR
        return when (val answer = repository.callTool(serverName, serverBinding, toolName, arguments)) {
            is McpAppResult.Success -> answer.data
            is McpAppResult.Failure -> {
                reportActionFailure(answer.failure)
                TOOL_ERROR
            }
        }
    }

    /** True once the chat accepted the text; a later abort can't undo it, so it counts as sent. */
    private suspend fun sendMessage(params: JsonObject): Boolean {
        val text = (params["content"] as? JsonArray).orEmpty()
            .mapNotNull { block ->
                val obj = block as? JsonObject ?: return@mapNotNull null
                if ((obj["type"] as? JsonPrimitive)?.content != "text") return@mapNotNull null
                (obj["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
            .joinToString("\n")
        if (text.isBlank() || text.length > policy.maxActionPreviewChars) return false
        if (!requestApproval(McpAppAction.Message(serverName, text))) return false
        return !closed && callbacks.sendMessage(text)
    }

    private fun reportActionFailure(failure: McpAppFailure) {
        when (failure) {
            McpAppFailure.BUSY -> callbacks.notice(McpAppNotice.BUSY, serverName)
            McpAppFailure.REAUTH_REQUIRED -> callbacks.notice(McpAppNotice.REAUTH_REQUIRED, serverName)
            McpAppFailure.APPS_DISABLED -> {
                callbacks.notice(McpAppNotice.APPS_DISABLED, serverName)
                callbacks.close()
            }
            else -> Unit
        }
    }

    private fun respondWith(page: Int, id: JsonElement, answer: McpAppResult<JsonObject>) {
        when (answer) {
            is McpAppResult.Success -> respond(page, id, answer.data)
            is McpAppResult.Failure -> {
                reportActionFailure(answer.failure)
                respondError(page, id, McpAppProtocol.ERROR_INTERNAL, "Request failed")
            }
        }
    }

    // --- approval queue (upstream approval.tsx) ---

    private suspend fun requestApproval(action: McpAppAction): Boolean {
        if (paused || closed || queue.size >= policy.maxPendingActions) return false
        val decision = CompletableDeferred<Boolean>()
        queue.addLast(action to decision)
        publishHead()
        return try {
            decision.await()
        } finally {
            queue.removeAll { it.second === decision }
            publishHead()
        }
    }

    private fun publishHead() {
        _pendingAction.value = queue.firstOrNull()?.first
    }

    /** The user approved the shown action. */
    fun approve() {
        queue.firstOrNull()?.second?.complete(true)
    }

    /** The user cancelled the shown action: it fails, and so does everything after until reopened. */
    fun cancelAction() {
        pauseActions()
    }

    private fun pauseActions() {
        paused = true
        queue.toList().forEach { it.second.complete(false) }
        queue.clear()
        _pendingAction.update { null }
    }

    /** Tears the App down: no pending action runs, in-flight calls are cancelled. */
    fun close() {
        if (closed) return
        pauseActions()
        if (initialized) {
            post(request(McpAppProtocol.RESOURCE_TEARDOWN, JsonObject(emptyMap())))
        }
        closed = true
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
        send = null
    }

    // --- JSON-RPC plumbing ---

    private fun launchTracked(block: suspend () -> Unit) {
        if (closed) return
        val job = scope.launch { block() }
        jobs += job
        job.invokeOnCompletion { jobs.remove(job) }
    }

    private fun respond(page: Int, id: JsonElement, result: JsonObject) {
        if (page != this.page) return
        post(
            buildJsonObject {
                put("jsonrpc", McpAppProtocol.JSONRPC)
                put("id", id)
                put("result", result)
            },
        )
    }

    private fun respondError(page: Int, id: JsonElement, code: Int, message: String) {
        if (page != this.page) return
        post(
            buildJsonObject {
                put("jsonrpc", McpAppProtocol.JSONRPC)
                put("id", id)
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                }
            },
        )
    }

    private fun notification(method: String, params: JsonObject) = buildJsonObject {
        put("jsonrpc", McpAppProtocol.JSONRPC)
        put("method", method)
        put("params", params)
    }

    private fun request(method: String, params: JsonObject) = buildJsonObject {
        put("jsonrpc", McpAppProtocol.JSONRPC)
        put("id", "host-${nextRequestId++}")
        put("method", method)
        put("params", params)
    }

    private fun post(message: JsonObject) {
        if (closed) return
        send?.invoke(message.toString())
    }

    private fun JsonObject.cursor(): String? = (this["cursor"] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        /** Upstream `useMCPAppFrame`'s reveal budget. */
        const val REVEAL_BUDGET_MS = 20_000L
        const val HOST_NAME = "Switchboard"
        const val DISPLAY_MODE = "fullscreen"

        private val IS_ERROR = buildJsonObject { put("isError", true) }
        private val TOOL_ERROR = buildJsonObject {
            put("content", buildJsonArray { })
            put("isError", true)
        }
    }
}
