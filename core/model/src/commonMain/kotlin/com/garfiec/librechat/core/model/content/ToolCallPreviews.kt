package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The query parameter and format version that ask a message load for tool-call previews
 * (upstream `TOOL_CALL_PREVIEWS_PARAM` / `TOOL_CALL_PREVIEWS_VERSION`). The server honours only an
 * exact version match, so a bumped format would silently send full payloads again.
 */
object ToolCallPreviews {
    const val PARAM: String = "toolPreviews"
    const val VERSION: String = "1"
}

/** True when any tool call in these messages is a preview, at any depth of a subagent transcript. */
fun List<Message>.containsToolCallPreview(): Boolean = any { message ->
    message.content.orEmpty().any { it.containsToolCallPreview() }
}

private fun MessageContentPart.containsToolCallPreview(): Boolean {
    val call = toolCall ?: return false
    return call.isPreview || call.subagentContent.orEmpty().any { it.containsToolCallPreview() }
}

/**
 * True when raw export JSON holds a `tool_call` carrying a preview marker. The import route stores a
 * file as is, so a preview imported would keep its shortened output for good. Walks the raw tree
 * rather than a decoded model so a format this client doesn't fully decode is still checked.
 */
fun JsonElement.containsToolCallPreviewMarkers(): Boolean = when (this) {
    is JsonObject -> {
        val call = this[TOOL_CALL_KEY] as? JsonObject
        (call != null && PREVIEW_MARKERS.any { (call[it] as? JsonPrimitive)?.booleanOrNull == true }) ||
            values.any { it.containsToolCallPreviewMarkers() }
    }
    is JsonArray -> any { it.containsToolCallPreviewMarkers() }
    else -> false
}

/**
 * This tree with every `tool_call`'s unset preview fields left out rather than written as `null`.
 * The server sends them only on a preview, and web's export (`JSON.stringify`) has no key for them on
 * a full call; an export written with `"outputTruncated": null` would read as a preview-shaped file.
 */
fun JsonElement.withoutUnsetToolCallPreviewFields(): JsonElement = when (this) {
    is JsonObject -> JsonObject(
        mapValues { (key, value) ->
            val stripped = value.withoutUnsetToolCallPreviewFields()
            if (key == TOOL_CALL_KEY && stripped is JsonObject) {
                JsonObject(stripped.filterNot { (field, fieldValue) -> field in PREVIEW_FIELDS && fieldValue is JsonNull })
            } else {
                stripped
            }
        },
    )
    is JsonArray -> JsonArray(map { it.withoutUnsetToolCallPreviewFields() })
    else -> this
}

private const val TOOL_CALL_KEY = "tool_call"
private val PREVIEW_MARKERS = listOf("outputTruncated", "argsTruncated", "subagentContentOmitted")

/** Every field the server sets only on a preview (`ToolCallPreviewMarkers`, upstream `previews.ts`). */
private val PREVIEW_FIELDS = setOf(
    "outputTruncated", "outputLength", "argsTruncated", "argsLength",
    "subagentContentOmitted", "subagentContentParts", "previewRevision",
)
