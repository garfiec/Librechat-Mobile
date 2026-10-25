package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.changedPartIndices
import com.garfiec.librechat.feature.chat.util.findRerunParent
import com.garfiec.librechat.feature.chat.util.hasBlankPartEdit
import com.garfiec.librechat.feature.chat.util.resubmittablePartIndex
import com.garfiec.librechat.feature.chat.viewmodel.MessageEditingState

/**
 * Resolves what the editor may do to [message] right now.
 *
 * Every predicate here is the SAME pure function `MessageEditingDelegate` consults before it acts,
 * over the SAME baselines: `editing` carries both the drafts and what they started as, so nothing
 * is re-derived from a message that can move under an open editor. Recomputing the part baselines
 * from `message.content` here would let a background revalidate landing mid-edit shift them and
 * read the user's typing as unchanged — Save would grey out with their words still on screen.
 */
@Composable
internal fun rememberMessageEditSession(
    message: Message,
    displayed: List<MessageNode>,
    editing: MessageEditingState,
    saveAllowed: Boolean,
    canStartRun: Boolean,
    onTextChange: (String) -> Unit,
    onPartTextChange: (Int, String) -> Unit,
    onSave: () -> Unit,
    onRerun: () -> Unit,
    onCancel: () -> Unit,
): MessageEditSession {
    val editingParts = editing.isEditingParts

    val rerunParent = remember(displayed, message.parentMessageId) {
        findRerunParent(displayed.map { it.message }, message.parentMessageId)
    }
    // A user turn is replayed as itself, so it always has something to rerun. A reply needs the
    // user turn behind it, which an imported or migrated thread can lack — that is the only thing
    // that withholds the button, because it is the only thing the footer's explanation names.
    val canRerun = message.isCreatedByUser || rerunParent != null
    // Mid-stream, read-only, or through Compare setup nothing may start a run, so the button
    // disables rather than disappearing behind a status line that would name the wrong reason.
    val runAllowed = canStartRun && saveAllowed

    val hasChanges = if (editingParts) {
        changedPartIndices(editing.partDrafts, editing.partOriginals).isNotEmpty()
    } else {
        editing.editingText != editing.editingTextOriginal
    }
    val hasBlankChange = if (editingParts) {
        hasBlankPartEdit(editing.partDrafts, editing.partOriginals)
    } else {
        hasChanges && editing.editingText.isBlank()
    }
    // A reply's flat-text draft can never ride the rerun: `editedContent` addresses a content array
    // this message does not have. Said up front rather than discovered afterwards.
    val rerunDiscardsEdit = !editingParts && !message.isCreatedByUser
    // Changed, but not in a form one `editedContent` can carry — more than one part, or reasoning.
    // Replies only: a user turn's rerun rebuilds its text from every draft rather than naming one
    // part, so no number of changes can hold it back.
    val rerunRequiresSave = editingParts &&
        !message.isCreatedByUser &&
        hasChanges &&
        resubmittablePartIndex(message, editing.partDrafts, editing.partOriginals) == null

    return MessageEditSession(
        text = editing.editingText,
        partDrafts = editing.partDrafts,
        hasChanges = hasChanges,
        hasBlankChange = hasBlankChange,
        saving = editing.isSavingEdit,
        saveAllowed = saveAllowed,
        canRerun = canRerun,
        runAllowed = runAllowed,
        rerunRequiresSave = rerunRequiresSave,
        rerunDiscardsEdit = rerunDiscardsEdit,
        onTextChange = onTextChange,
        onPartTextChange = onPartTextChange,
        onSave = onSave,
        onRerun = onRerun,
        onCancel = onCancel,
    )
}
