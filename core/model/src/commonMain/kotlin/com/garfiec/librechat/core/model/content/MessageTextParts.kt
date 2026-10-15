package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.ContentType
import kotlinx.serialization.json.JsonPrimitive

/**
 * Faithful port of the server's `parseTextParts`
 * (upstream/packages/data-provider/src/parsers.ts): the plain text of a message's content
 * parts, joined by the server's own separator rule.
 *
 * - TEXT parts are always included.
 * - THINK parts are included unless [skipReasoning] is set.
 * - STEER parts (mid-run user speech) only when [includeSteer] is set. Upstream opts in for the
 *   full-record surfaces: search indexing (`mongoMeili.ts`) and the persisted abort text.
 *
 * The separator rule is the server's exactly: a single `' '` between two chunks iff the running
 * result is non-empty, the incoming value is non-empty, the result's last char is not `' '`, and
 * the incoming's first char is not `' '`. It is a literal-space comparison, NOT isWhitespace(), so
 * a chunk ending in `\n` still gets the space the server inserts.
 *
 * MIRRORED SERVER LOGIC — verify against upstream on version bumps (/sync-upstream).
 */
fun List<MessageContentPart>.flattenSearchableText(
    includeSteer: Boolean = false,
    skipReasoning: Boolean = false,
): String {
    val result = StringBuilder()
    for (part in this) {
        val value = when (part.type) {
            ContentType.TEXT -> part.text.orEmpty()
            ContentType.THINK -> if (skipReasoning) null else part.think.orEmpty()
            ContentType.STEER -> if (includeSteer) part.steerText().orEmpty() else null
            else -> null
        } ?: continue
        if (result.isNotEmpty() && value.isNotEmpty() && result.last() != ' ' && value.first() != ' ') {
            result.append(' ')
        }
        result.append(value)
    }
    return result.toString()
}

/** Upstream reads `part.steer` only when it is a string; anything else contributes `''`. */
private fun MessageContentPart.steerText(): String? =
    (steer as? JsonPrimitive)?.takeIf { it.isString }?.content
