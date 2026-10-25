package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactSegment
import com.garfiec.librechat.feature.chat.components.artifact.detectArtifacts

/**
 * Which editor a message opens, decided once and read by both the affordance and the sink.
 *
 * `MessageEditingDelegate` and `MessageList` must agree: the Edit button is hidden for [NONE] and
 * the delegate refuses it too, the way the feedback thumbs are gated at both ends. An
 * affordance-only gate leaves the next caller unprotected; a sink-only gate offers a button that
 * silently does nothing.
 */
enum class MessageEditShape {
    /** One field over the message's flat `text`. The server takes it with no `index`. */
    PLAIN_TEXT,

    /** One field per editable content part, each addressed by its raw index into `content`. */
    CONTENT_PARTS,

    /** Nothing a field could hold — a turn of tool calls, images or an artifact. */
    NONE,
}

fun editShapeOf(message: Message): MessageEditShape {
    // Matches what MessageContentAndActions renders: an absent or empty part list falls back to
    // the flat text, so that is what the editor has to edit.
    val parts = message.content
    if (parts.isNullOrEmpty()) return MessageEditShape.PLAIN_TEXT
    return if (parts.any { isEditablePart(it) }) {
        MessageEditShape.CONTENT_PARTS
    } else {
        MessageEditShape.NONE
    }
}

/** Whether opening the editor on [message] would show any field at all. */
fun hasEditablePart(message: Message): Boolean = editShapeOf(message) != MessageEditShape.NONE

/**
 * Whether the editor gives this part a field to type in. TEXT and THINK only — the server refuses
 * any other type on the indexed write ("Cannot update non-text content").
 *
 * Two kinds of text part are deliberately left read-only, as upstream leaves them: one carrying
 * `tool_call_ids` (it is a tool-call container, not prose) and one holding an artifact, which keeps
 * its artifact card rather than becoming a text field.
 */
fun isEditablePart(part: MessageContentPart?): Boolean {
    if (part == null) return false
    val value = when (part.type) {
        // Empty-list, not just null: an empty `tool_call_ids` names no tool calls, so the part is
        // prose and keeps its field. A DELIBERATE divergence — upstream's `isEditablePart` tests
        // `tool_call_ids != null` and would hide the field for `[]`.
        ContentType.TEXT -> if (!part.toolCallIds.isNullOrEmpty()) return false else part.text
        ContentType.THINK -> part.think
        else -> return false
    } ?: return false
    return !containsArtifact(value)
}

// detectArtifacts has its own literal-prefix fast path, so an artifact-free part costs one scan.
private fun containsArtifact(text: String): Boolean =
    detectArtifacts(text).any { it is ArtifactSegment.ArtifactReference }

/** The drafts the editor opens with: every editable part's current text, keyed by its raw index. */
fun partEditSeeds(message: Message): Map<Int, String> {
    val parts = message.content ?: return emptyMap()
    return buildMap {
        parts.forEachIndexed { index, part ->
            if (isEditablePart(part)) put(index, partEditText(part))
        }
    }
}

/** The text a field shows for [part] — the field its own type names, never the other one. */
fun partEditText(part: MessageContentPart): String =
    (if (part.type == ContentType.THINK) part.think else part.text).orEmpty()

/** Indices whose draft differs from what the editor opened with, in content order. */
fun changedPartIndices(drafts: Map<Int, String>, originals: Map<Int, String>): List<Int> =
    drafts.keys.sorted().filter { drafts[it] != originals[it] }

/**
 * Whether any changed part has been emptied. Both editors refuse that: nothing offers a way to
 * delete a part, so a cleared field only ever reads as an accident, and saving it would persist a
 * blank part.
 */
fun hasBlankPartEdit(drafts: Map<Int, String>, originals: Map<Int, String>): Boolean =
    changedPartIndices(drafts, originals).any { drafts[it].orEmpty().isBlank() }

/**
 * The part a rerun may carry as `editedContent`, or null when the rerun must not carry one.
 *
 * Null for three different reasons, which the caller tells apart by counting the changes itself:
 * - **no change** — the rerun is a plain regenerate, which is a first-class action (a cancelled or
 *   failed reply has to be reissued as it was), not a mistake;
 * - **more than one change** — the field is a single part, so the rest would be silently dropped;
 *   the editor asks for a Save first;
 * - **the one change is a THINK part** — servers in the supported range read `text` out of
 *   `editedContent` whatever its `type` says, so a think payload writes nothing and wipes the
 *   reasoning prefix. See `EditedContent`.
 */
fun resubmittablePartIndex(
    message: Message,
    drafts: Map<Int, String>,
    originals: Map<Int, String>,
): Int? {
    val index = changedPartIndices(drafts, originals).singleOrNull() ?: return null
    val part = message.content?.getOrNull(index) ?: return null
    return index.takeIf { part.type == ContentType.TEXT }
}

/**
 * The user turn a rerun would replay, or null when the message has none.
 *
 * A rerun resubmits the edited message's parent AS the turn's user message, so that parent has to
 * be in the thread and be the user's. An imported or migrated conversation can have neither — a
 * reply chained onto another reply, or one left at the root. Resolved at render and again at
 * submit, so the offered action and the submission cannot disagree about what is replayed.
 */
fun findRerunParent(messages: List<Message>, parentMessageId: String?): Message? {
    if (parentMessageId == null) return null
    return messages.firstOrNull { it.messageId == parentMessageId }?.takeIf { it.isCreatedByUser }
}
