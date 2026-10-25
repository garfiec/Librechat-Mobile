package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Immutable

/**
 * Everything the inline editor needs for the ONE message currently being edited, or null on every
 * other row.
 *
 * One parameter rather than six, because it threads through four pure-forwarding layers —
 * `MessageList` → `MessageBubble` (androidMain) / the iOS bubble → `MessageContentAndActions` — and the `expect`/`actual` pair has to agree on all of them.
 *
 * Null for a row that is not being edited, which is also what keeps the other rows from recomposing
 * while the user types: they are handed the same stable null throughout.
 *
 * Over the parameter threshold on purpose: these ARE the parameters it exists to stop threading
 * separately, and splitting them again across two objects would put the state and the callbacks
 * that act on it in different places for no reader's benefit.
 */
@Immutable
@Suppress("LongParameterList")
class MessageEditSession(
    /** Draft for the flat-text editor; empty when the message is edited part by part. */
    val text: String,
    /** Per-content-part drafts, keyed by the raw index into `Message.content`. */
    val partDrafts: Map<Int, String>,
    /** The user has typed something that is not yet saved. */
    val hasChanges: Boolean,
    /**
     * A change would persist an empty part. Refused by both actions: nothing offers a way to delete
     * a part, so a cleared field only ever reads as an accident.
     */
    val hasBlankChange: Boolean,
    /** A save is in flight: the fields and every button but Cancel are disabled. */
    val saving: Boolean,
    /**
     * Saving is possible at all — false mid-stream, where the write would re-emit through Room, and
     * in a read-only conversation.
     */
    val saveAllowed: Boolean,
    /**
     * There is something for a rerun to replay: the turn is the user's own, or a user turn sits
     * behind the reply. Withheld rather than shown-and-refused, so an imported reply with no user
     * turn behind it says why instead.
     *
     * Deliberately NOT ANDed with [runAllowed]: the footer explains a false [canRerun] as "no user
     * message to replay", which would be a lie mid-stream or through Compare setup. Those disable
     * the button instead, the way they disable Save.
     */
    val canRerun: Boolean,
    /** A run may start right now — false mid-stream, read-only, or through Compare setup. */
    val runAllowed: Boolean,
    /**
     * The changes cannot ride the rerun as they stand, so it asks for a Save first: more than one
     * changed part (the wire field carries one), or a changed reasoning part (which servers in the
     * supported range would read as empty). See `resubmittablePartIndex`.
     */
    val rerunRequiresSave: Boolean,
    /**
     * The rerun will NOT carry the draft at all — the flat-text editor on a reply, which has no
     * content array for an indexed edit to address. Said in the footer before the button is pressed.
     */
    val rerunDiscardsEdit: Boolean,
    val onTextChange: (String) -> Unit,
    val onPartTextChange: (Int, String) -> Unit,
    val onSave: () -> Unit,
    val onRerun: () -> Unit,
    val onCancel: () -> Unit,
) {
    val saveEnabled: Boolean get() = hasChanges && !hasBlankChange && !saving && saveAllowed

    /** A rerun with nothing changed is a plain regenerate, which is deliberately still offered. */
    val rerunEnabled: Boolean
        get() = canRerun && runAllowed && !rerunRequiresSave && !hasBlankChange && !saving
}
