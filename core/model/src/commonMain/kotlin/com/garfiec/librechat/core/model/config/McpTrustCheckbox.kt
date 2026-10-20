package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The deployment's own wording for the "I trust this server" acknowledgement asked before adding an
 * MCP server. Each part is a string or a `{ languageTag: string }` map; resolve with
 * [resolveLocalizedConfigText]. MIRRORED from upstream `mcpServersSchema.trustCheckbox`
 * (`packages/data-provider/src/config.ts`).
 */
data class McpTrustCheckbox(val label: JsonElement?, val subLabel: JsonElement?)

/** The configured trust wording, or null when the deployment leaves it to the client's own strings. */
fun InterfaceConfig.mcpTrustCheckbox(): McpTrustCheckbox? {
    val trust = ((mcpServers as? JsonObject)?.get("trustCheckbox") as? JsonObject) ?: return null
    return McpTrustCheckbox(label = trust["label"], subLabel = trust["subLabel"])
}

/**
 * Text for [languageTag] from a config value that is either a plain string or a map keyed by
 * language tag: the exact tag, then its base language, then English, then whatever comes first.
 *
 * Markup is stripped. Upstream renders these as sanitized HTML (an admin can put a link in the
 * label); this app renders plain text, so tags would otherwise show literally.
 */
fun resolveLocalizedConfigText(element: JsonElement?, languageTag: String): String? {
    val raw = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content
        is JsonObject -> {
            val byTag = element.entries
                .mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.let { normalizeTag(key) to it.content }
                }
                .toMap()
            // Android's app locale arrives as `Locale.toString()` ("de_DE"), not a BCP-47 tag.
            val tag = normalizeTag(languageTag)
            byTag[tag] ?: byTag[tag.substringBefore('-')] ?: byTag["en"] ?: byTag.values.firstOrNull()
        }
        else -> null
    }
    return raw?.let(::plainText)?.takeIf { it.isNotBlank() }
}

// `Locale.toString()` appends a script as `_#Hant`, which no config key carries.
private fun normalizeTag(tag: String): String = tag.substringBefore("_#").replace('_', '-').lowercase()

private fun plainText(html: String): String = html
    // A line or block break separates words; dropping it would glue them together.
    .replace(BLOCK_BREAK, " ")
    .replace(TAG, "")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .replace(WHITESPACE, " ")
    .trim()

private val BLOCK_BREAK = Regex(
    "<\\s*/?\\s*(br|hr|p|div|li|ul|ol|h[1-6]|tr|td|th|blockquote)\\b[^>]*>",
    RegexOption.IGNORE_CASE,
)
private val TAG = Regex("<[^>]*>")
private val WHITESPACE = Regex("\\s+")
