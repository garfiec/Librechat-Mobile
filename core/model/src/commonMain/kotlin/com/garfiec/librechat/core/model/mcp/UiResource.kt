package com.garfiec.librechat.core.model.mcp

import com.garfiec.librechat.core.model.Attachment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * One entry of a `ui_resources` attachment (upstream `UIResource`, `schemas.ts`; built by
 * `packages/api/src/mcp/parsers.ts`). Read field by field from [Attachment.uiResources], so one
 * odd field costs that field, never the message.
 *
 * A resource over the server's `maxPersistedAppBytes` arrives URI-only: no [text]/[blob] and no
 * [csp], and the document is read with `POST /api/mcp/resources/read` when opened.
 */
data class UiResource(
    val resourceId: String?,
    val uri: String?,
    val mimeType: String?,
    val text: String?,
    val blob: String?,
    val serverName: String?,
    val toolName: String?,
    /** Opaque `v1.<hmac>` the App routes require; invalidated when the server's config changes. */
    val serverBinding: String?,
    val structuredContent: JsonElement?,
    val content: JsonElement?,
    val toolArgs: JsonElement?,
    val isError: Boolean,
    val resultMeta: JsonElement?,
    /** The raw `csp` declaration; normalize it with [McpAppCspDeclaration.normalize]. */
    val csp: JsonElement?,
    /** The raw `permissions` declaration; read it with [McpAppPermissions.supported]. */
    val permissions: JsonElement? = null,
) {
    /** Upstream `getResourceKey`. */
    val key: String get() = resourceId?.takeIf { it.isNotEmpty() } ?: uri.orEmpty()

    /** Upstream `isMcpAppResource`: server-bound and of the App profile. */
    val isMcpApp: Boolean
        get() = !toolName.isNullOrEmpty() && !serverName.isNullOrEmpty() && isMcpAppMimeType(mimeType)

    /** Upstream `getInlineResourceHtml`: [text], else [blob] decoded as UTF-8; null if neither. */
    @OptIn(ExperimentalEncodingApi::class)
    fun inlineHtml(): String? {
        text?.takeIf { it.isNotEmpty() }?.let { return it }
        val encoded = blob?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Base64.Default.decode(encoded).decodeToString() }.getOrNull()
    }

    /**
     * Upstream `buildAppToolResult` for an App-backed resource: always a result (so the App's
     * tool-result handler fires on empty output), with `structuredContent` only when it is an
     * object and `_meta` from [resultMeta].
     */
    fun appToolResult(): JsonObject = buildJsonObject {
        put("content", content as? JsonArray ?: JsonArray(emptyList()))
        (structuredContent as? JsonObject)?.let { put("structuredContent", it) }
        if (isError) put("isError", true)
        resultMeta?.takeIf { it != JsonNull }?.let { put("_meta", it) }
    }

    /** The tool's arguments as the App's `tool-input` notification carries them (`toolArgs ?? {}`). */
    fun toolInputArguments(): JsonObject = toolArgs as? JsonObject ?: JsonObject(emptyMap())

    companion object {
        fun from(element: JsonElement): UiResource? {
            val obj = element as? JsonObject ?: return null
            fun string(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            return UiResource(
                resourceId = string("resourceId"),
                uri = string("uri"),
                mimeType = string("mimeType"),
                text = string("text"),
                blob = string("blob"),
                serverName = string("serverName"),
                toolName = string("toolName"),
                serverBinding = string("serverBinding"),
                structuredContent = obj["structuredContent"],
                content = obj["content"],
                toolArgs = obj["toolArgs"],
                isError = (obj["isError"] as? JsonPrimitive)?.takeIf { !it.isString }?.content == "true",
                resultMeta = obj["resultMeta"],
                csp = obj["csp"],
                permissions = obj["permissions"],
            )
        }
    }
}

/**
 * One App in a message, in message order (upstream `MCPAppResourceOccurrence`). [key] is upstream's
 * `[toolCallId, agentId, stepId, resourceKey]` plus an occurrence count, so a provider that reuses a
 * tool-call id across steps still gives each App its own row. [ordinal] is its 1-based position
 * among the message's Apps, for the row's accessible label.
 */
data class McpAppOccurrence(
    val attachment: Attachment,
    val resource: UiResource,
    val key: String,
    val ordinal: Int,
)

/**
 * Upstream `selectToolCallUIResources` (`client/src/utils/mcpApps.ts`): every MCP App in
 * [attachments], in order. Only a `ui_resources` attachment whose payload is an array counts (an
 * index-keyed object, which older servers could send, is skipped as upstream skips it), and only
 * entries that are Apps ([UiResource.isMcpApp]); plain `text/html` is the legacy MCP-UI feature.
 */
fun selectToolCallUiResources(attachments: List<Attachment>): List<McpAppOccurrence> {
    val occurrences = HashMap<String, Int>()
    val out = mutableListOf<McpAppOccurrence>()
    for (attachment in attachments) {
        if (attachment.type != UI_RESOURCES_TYPE) continue
        val array = attachment.uiResources as? JsonArray ?: continue
        for (element in array) {
            val resource = UiResource.from(element) ?: continue
            if (!resource.isMcpApp) continue
            val semantic = listOf(attachment.toolCallId, attachment.agentId, attachment.stepId, resource.key)
                .joinToString("\u0000") { it.orEmpty() }
            val occurrence = occurrences[semantic] ?: 0
            occurrences[semantic] = occurrence + 1
            out += McpAppOccurrence(attachment, resource, "$semantic\u0000$occurrence", out.size + 1)
        }
    }
    return out
}

const val UI_RESOURCES_TYPE = "ui_resources"

/** An App document ready to run: its HTML and the CSP and permissions declarations that came with it. */
data class McpAppDocument(val html: String, val csp: JsonElement?, val permissions: JsonElement? = null)

/**
 * Upstream `fetchMCPResourceHtml`'s pick from a `resources/read` result: the `contents[]` item with
 * the same [uri] and an App media type, its `text` or UTF-8 `blob`, and its content-level
 * `_meta.ui.csp` and `_meta.ui.permissions`. Null when no item matches or it carries no HTML.
 */
@OptIn(ExperimentalEncodingApi::class)
fun pickAppDocument(readResult: JsonElement?, uri: String): McpAppDocument? {
    val contents = (readResult as? JsonObject)?.get("contents") as? JsonArray ?: return null
    val item = contents.firstOrNull { element ->
        val obj = element as? JsonObject ?: return@firstOrNull false
        (obj["uri"] as? JsonPrimitive)?.content == uri &&
            isMcpAppMimeType((obj["mimeType"] as? JsonPrimitive)?.takeIf { it.isString }?.content)
    } as? JsonObject ?: return null
    fun string(key: String) = (item[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val html = string("text")?.takeIf { it.isNotEmpty() }
        ?: string("blob")?.takeIf { it.isNotEmpty() }?.let { encoded ->
            runCatching { Base64.Default.decode(encoded).decodeToString() }.getOrNull()
        }
    if (html.isNullOrEmpty()) return null
    val ui = ((item["_meta"] as? JsonObject)?.get("ui")) as? JsonObject
    return McpAppDocument(html = html, csp = ui?.get("csp"), permissions = ui?.get("permissions"))
}

/**
 * The browser capabilities an App declared that the app's opaque App frame can delegate (upstream
 * `getSupportedMCPAppPermissions`, `client/src/utils/mcpApps.ts`): geolocation and clipboard-write.
 * Camera and microphone are never delegated. A key counts when its value is truthy, as upstream
 * reads it (`permissions.geolocation` is normally `{}`).
 */
data class McpAppPermissions(val geolocation: Boolean = false, val clipboardWrite: Boolean = false) {
    /** The `allow` attribute tokens for the App frame, upstream `mcp-sandbox.html`'s `allowParts`. */
    fun allowTokens(): List<String> = buildList {
        if (geolocation) add("geolocation")
        if (clipboardWrite) add("clipboard-write")
    }

    /** As the sandbox-resource-ready `permissions` param: `{"geolocation":{}, "clipboardWrite":{}}`. */
    fun toJson(): JsonObject = buildJsonObject {
        if (geolocation) put("geolocation", JsonObject(emptyMap()))
        if (clipboardWrite) put("clipboardWrite", JsonObject(emptyMap()))
    }

    companion object {
        val NONE = McpAppPermissions()

        fun supported(raw: JsonElement?): McpAppPermissions {
            val obj = raw as? JsonObject ?: return NONE
            return McpAppPermissions(
                geolocation = obj["geolocation"].isTruthy(),
                clipboardWrite = obj["clipboardWrite"].isTruthy(),
            )
        }

        /** JavaScript truthiness for a JSON value. */
        private fun JsonElement?.isTruthy(): Boolean = when (this) {
            null, JsonNull -> false
            is JsonPrimitive -> if (isString) content.isNotEmpty() else content != "false" && content.toDoubleOrNull() != 0.0
            else -> true
        }
    }
}
