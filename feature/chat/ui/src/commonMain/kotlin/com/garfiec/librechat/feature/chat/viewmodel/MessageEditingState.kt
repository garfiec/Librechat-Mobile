package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * Inline edit-field UI state for editing a message in place. Owned by
 * [com.garfiec.librechat.feature.chat.viewmodel.delegate.MessageEditingDelegate].
 *
 * One of two shapes is live at a time, chosen by `editShapeOf` when the editor opens: [editingText]
 * for a message edited as flat text, or [partDrafts] for one edited part by part. The unused half
 * stays at its default.
 */
@Immutable
data class MessageEditingState(
    val editingMessageId: String? = null,
    /** Draft for the flat-text editor. Empty when editing content parts. */
    val editingText: String = "",
    /** What [editingText] started as, so "changed" is measured against what the user saw. */
    val editingTextOriginal: String = "",
    /** Per-content-part drafts, keyed by the raw index into `Message.content`. */
    val partDrafts: Map<Int, String> = emptyMap(),
    /**
     * What each draft started as, snapshotted when the editor opened, so "changed" is measured
     * against what the user saw. Read from the live message instead and a Room emission landing
     * under the open editor — a background revalidate, another client's edit — would move the
     * baseline and silently mark the user's typing as unchanged.
     *
     * A save that succeeds advances these to the saved text, which is what leaves only the
     * still-unsaved parts marked as changed after a partial failure.
     */
    val partOriginals: Map<Int, String> = emptyMap(),
    /**
     * A save is in flight. Guards the double tap, and disables the fields so a keystroke cannot
     * land between the request and the baseline advancing.
     */
    val isSavingEdit: Boolean = false,
) {
    /**
     * Whether the open editor is the per-part one. The single discriminator every reader uses — the
     * delegate to route Save and Rerun, the renderer to decide whether to swap fields into the
     * content parts. Deriving it again from `editShapeOf(message)` in the UI would let the two
     * disagree when the message moves under the open editor.
     */
    val isEditingParts: Boolean get() = partDrafts.isNotEmpty()
}
