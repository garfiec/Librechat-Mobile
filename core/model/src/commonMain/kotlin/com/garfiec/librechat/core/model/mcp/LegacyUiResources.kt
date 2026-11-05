package com.garfiec.librechat.core.model.mcp

import com.garfiec.librechat.core.model.Attachment
import kotlinx.serialization.json.JsonArray

/**
 * Legacy MCP-UI: `text/html` resources a tool returns, which the model places in its reply with
 * `\ui{id}` markers. Rendered only where a marker names them (upstream: "Legacy HTML remains
 * marker-owned"; nothing draws one without a marker) and only while `mcpApps.legacyHtmlEnabled`.
 */

private const val LEGACY_MCP_UI_MIME_TYPE = "text/html"

/**
 * Upstream `resolveMCPUIResourceMimeType` (`packages/data-provider/src/mcp/appMime.ts`): an omitted
 * media type means inline HTML; an explicit one is kept, so an unsupported type never becomes HTML.
 */
fun resolveMcpUiResourceMimeType(mimeType: String?): String = mimeType ?: LEGACY_MCP_UI_MIME_TYPE

/** Upstream `isHtmlMediaType`: the media type (case-insensitive, parameters ignored) is `text/html`. */
fun isHtmlMediaType(mimeType: String?): Boolean =
    mimeType != null && mimeType.split(';')[0].trim().lowercase() == LEGACY_MCP_UI_MIME_TYPE

/**
 * Upstream `isSupportedUIResource` (`client/src/components/MCPUIResource/Renderer.tsx`): HTML that is
 * not the MCP App profile. Apps render as "Open app" rows instead, never through a marker.
 */
val UiResource.isLegacyHtml: Boolean
    get() {
        val resolved = resolveMcpUiResourceMimeType(mimeType)
        return !isMcpAppMimeType(resolved) && isHtmlMediaType(resolved)
    }

/**
 * Upstream `useConversationUIResources`: every `ui_resources` entry in [attachments] that has a
 * `resourceId`, by id. Later entries win, as web's `Map.set` does, so markers can name a resource
 * from an earlier turn. Only array payloads count (an index-keyed object is skipped, as upstream).
 */
fun collectConversationUiResources(attachments: Sequence<Attachment>): Map<String, UiResource> {
    val byId = LinkedHashMap<String, UiResource>()
    for (attachment in attachments) {
        if (attachment.type != UI_RESOURCES_TYPE) continue
        val array = attachment.uiResources as? JsonArray ?: continue
        for (element in array) {
            val resource = UiResource.from(element) ?: continue
            val id = resource.resourceId?.takeIf { it.isNotEmpty() } ?: continue
            byId[id] = resource
        }
    }
    return byId
}
