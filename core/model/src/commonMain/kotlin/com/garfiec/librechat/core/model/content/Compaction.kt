package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Manual context compaction (v0.8.8-rc3): summarize the branch up to a leaf and end without a
 * reply. The summary is persisted as the boundary every later turn starts from.
 */
object Compaction {

    /**
     * MIRRORED from upstream `isCompactedLeaf` (`packages/data-provider/src/messages.ts`).
     *
     * True when [message] is a FINISHED compaction: every part is a summary and at least one
     * carries text. A part still streaming, or one that failed, contributes nothing — which is
     * what lets an interrupted compaction be retried rather than reading as already done.
     *
     * A missing `boundary` is the same signal: only the final summary block carries one, so a part
     * holding streamed deltas alone has none.
     */
    fun isCompactedLeaf(message: Message?): Boolean {
        val content = message?.content
        if (content.isNullOrEmpty()) return false
        var usable = false
        for (part in content) {
            if (part.type != ContentType.SUMMARY) return false
            if (part.summarizing == true || part.failed == true || part.boundary == null) continue
            if (summaryHasText(part)) usable = true
        }
        return usable
    }

    /**
     * MIRRORED from upstream `supportsCompaction`. An Assistants thread lives on the provider, so a
     * summary inserted into the local history would compact nothing.
     */
    fun supportsCompaction(endpoint: String?): Boolean =
        !endpoint.isNullOrBlank() &&
            !endpoint.equals("assistants", ignoreCase = true) &&
            !endpoint.equals("azureAssistants", ignoreCase = true)

    /**
     * The text a summary part carries. `content` is an array of `{type,text}` blocks, a raw
     * string, or absent — in which case the legacy top-level `text` is the fallback. Mirrors the
     * same three shapes the renderer handles.
     */
    fun summaryText(part: MessageContentPart): String {
        val content = part.content
        if (content is JsonArray) {
            return content.joinToString("") { element ->
                val item = element as? JsonObject ?: return@joinToString ""
                if ((item["type"] as? JsonPrimitive)?.contentOrNull != "text") return@joinToString ""
                (item["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
        }
        if (content is JsonPrimitive && content.isString) return content.content
        return part.text.orEmpty()
    }

    private fun summaryHasText(part: MessageContentPart): Boolean = summaryText(part).isNotBlank()
}
