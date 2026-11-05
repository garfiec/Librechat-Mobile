package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart

/**
 * Who a tool call is to the full-part route (`id + stepId + part.agentId`), which matches all three
 * exactly; an empty one counts as absent. The same before and after a call is hydrated, so it is
 * what an open card pins.
 */
data class ToolPartIdentity(val toolCallId: String, val stepId: String?, val agentId: String?) {
    companion object {
        fun of(call: AgentToolCall?, agentId: String?): ToolPartIdentity? {
            val id = call?.id?.takeIf { it.isNotEmpty() } ?: return null
            return ToolPartIdentity(id, call.stepId?.takeIf { it.isNotEmpty() }, agentId?.takeIf { it.isNotEmpty() })
        }
    }
}

/**
 * One previewed tool call's stored version: its identity plus [AgentToolCall.previewKey]. A fetched
 * part is kept under this key, so a reload that re-previews a changed call asks again.
 */
data class ToolPartKey(val identity: ToolPartIdentity, val previewKey: String) {
    companion object {
        /** The key of a previewed call, or null when [call] is not a preview or has no id. */
        fun of(call: AgentToolCall?, agentId: String?): ToolPartKey? {
            if (call?.isPreview != true) return null
            val identity = ToolPartIdentity.of(call, agentId) ?: return null
            return ToolPartKey(identity, call.previewKey())
        }
    }
}

/** A fetch of one previewed part. Only [Loaded] changes what renders. */
sealed interface ToolPartLoad {
    data object Loading : ToolPartLoad
    data object Failed : ToolPartLoad

    /** [chars] is what the part costs to hold: output, args and serialised transcript, measured on arrival. */
    data class Loaded(val full: AgentToolCall, val chars: Long) : ToolPartLoad
}

/** What a card shows while its full content is not in place. */
enum class ToolPartStatus { LOADING, FAILED }

/**
 * [nodes] with every previewed tool call whose full part is in [loaded] swapped for
 * `preview.withFull(full)`. A pure per-part substitution over the already-built display path: it
 * never rebuilds the tree, so `activeBranches` and the streaming anchor are untouched. Returns
 * [nodes] itself when nothing matched, so an emission that changed nothing stays equal.
 */
fun List<MessageNode>.withHydratedToolParts(loaded: Map<ToolPartKey, AgentToolCall>): List<MessageNode> {
    if (loaded.isEmpty()) return this
    val identities = loaded.keys.mapTo(HashSet()) { it.identity }
    var changed = false
    val result = map { node ->
        val content = node.message.content
        val hydrated = content?.withHydratedToolParts(loaded, identities)
        if (hydrated == null || hydrated === content) {
            node
        } else {
            changed = true
            node.copy(message = node.message.copy(content = hydrated))
        }
    }
    return if (changed) result else this
}

/** The same substitution over one message, for a caller that holds messages rather than nodes. */
fun Message.withHydratedToolParts(loaded: Map<ToolPartKey, AgentToolCall>): Message {
    if (loaded.isEmpty()) return this
    val content = content ?: return this
    val hydrated = content.withHydratedToolParts(loaded, loaded.keys.mapTo(HashSet()) { it.identity })
    return if (hydrated === content) this else copy(content = hydrated)
}

private fun List<MessageContentPart>.withHydratedToolParts(
    loaded: Map<ToolPartKey, AgentToolCall>,
    identities: Set<ToolPartIdentity>,
): List<MessageContentPart> {
    var changed = false
    val result = map { part ->
        val call = part.toolCall
        // The identity filter first: hashing a preview for its key is the only per-part cost.
        if (part.type != ContentType.TOOL_CALL || call == null || !call.isPreview ||
            ToolPartIdentity.of(call, part.agentId) !in identities
        ) {
            return@map part
        }
        val full = ToolPartKey.of(call, part.agentId)?.let(loaded::get) ?: return@map part
        changed = true
        part.copy(toolCall = call.withFull(full))
    }
    return if (changed) result else this
}

/** The previewed tool-call parts of [message], with their raw index into `content` (the route's `partIndex`). */
fun previewedToolParts(message: Message): List<Pair<Int, MessageContentPart>> =
    message.content.orEmpty().withIndex()
        .filter { (_, part) -> part.type == ContentType.TOOL_CALL && part.toolCall?.isPreview == true }
        .map { (index, part) -> index to part }

/** What holding [full] costs, in characters: output, args and the serialised transcript. */
fun hydratedChars(full: AgentToolCall): Long =
    full.output.orEmpty().length.toLong() +
        (if (full.args == null) 0 else full.serializedArgs().length).toLong() +
        full.subagentContent.orEmpty().sumOf { it.toString().length.toLong() }

/**
 * Whether [full] is exactly what [preview] stands for: every shortened field's full length
 * matches and its text agrees with what the preview kept. A load that replaced a full call with a
 * preview of it may then reuse the full call instead of fetching what was on screen seconds ago;
 * any mismatch is a real change.
 */
fun isFullCopyOf(full: AgentToolCall, preview: AgentToolCall): Boolean {
    if (full.isPreview || !preview.isPreview) return false
    if (preview.outputTruncated == true && !sameField(preview.output, preview.outputLength, full.output)) return false
    if (preview.argsTruncated == true &&
        !sameField(preview.serializedArgs(), preview.argsLength, full.args?.let { full.serializedArgs() })
    ) {
        return false
    }
    if (preview.subagentContentOmitted == true) {
        val transcript = full.subagentContent ?: return false
        if (preview.subagentContentParts != transcript.size) return false
    }
    return true
}

/** Whether [full] has the [length] the preview declared and the text [kept] around its cuts. */
private fun sameField(kept: String?, length: Int?, full: String?): Boolean =
    full != null && length == full.length && keptBy(kept.orEmpty(), full)

/** The server's preview marks every cut with this (`TOOL_CALL_PREVIEW_ELISION`, a capped JSON string's tail). */
private const val PREVIEW_CUT = '\u2026'

/** How many characters of each kept end are compared: enough to tell a changed call apart, bounded. */
private const val KEPT_END_CHARS = 256

/**
 * Whether [full] starts with what [preview] kept before its first cut and ends with what it kept
 * after its last (a preview with no cut is a plain prefix). Whitespace is skipped on both sides,
 * since a JSON preview is re-serialised compactly from text that may have been pretty-printed.
 */
private fun keptBy(preview: String, full: String): Boolean {
    val cut = preview.indexOf(PREVIEW_CUT)
    val head = if (cut < 0) preview else preview.substring(0, cut)
    if (!sameNonWhitespace(head, full, fromEnd = false)) return false
    if (cut < 0) return true
    return sameNonWhitespace(preview.substring(preview.lastIndexOf(PREVIEW_CUT) + 1), full, fromEnd = true)
}

/** Whether the first (or last) up to [KEPT_END_CHARS] non-whitespace characters of [kept] lead (or end) [full]. */
private fun sameNonWhitespace(kept: String, full: String, fromEnd: Boolean): Boolean {
    val wanted = nonWhitespaceEnd(kept, KEPT_END_CHARS, fromEnd)
    return nonWhitespaceEnd(full, wanted.length, fromEnd) == wanted
}

private fun nonWhitespaceEnd(text: String, count: Int, fromEnd: Boolean): String {
    val out = StringBuilder(count)
    val indices = if (fromEnd) text.indices.reversed() else text.indices
    for (i in indices) {
        if (out.length == count) break
        val c = text[i]
        if (!c.isWhitespace()) out.append(c)
    }
    return if (fromEnd) out.reverse().toString() else out.toString()
}
