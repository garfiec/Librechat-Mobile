package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.viewmodel.DraftItemsHandle
import com.garfiec.librechat.feature.chat.viewmodel.DraftUndo
import com.garfiec.librechat.feature.chat.viewmodel.RemovedDraftItem

/**
 * The attachment list's × and Undo: what goes with the message being composed (files, picked
 * skills, quotes) is removed here, and a finished removal can be put back where it was.
 *
 * Only a settled item gets an Undo: an uploaded or failed file, a skill, a quote. Removing a file
 * still uploading cancels its upload and offers none, since bringing it back would mean starting the
 * upload over. One Undo is pending at a time (`ComposerState.pendingDraftUndo`); a later removal
 * replaces it, and a send, the queued-edit swap or a conversation switch drops it in their own
 * composer writes.
 */
class DraftItemsDelegate(
    private val handle: DraftItemsHandle,
    private val fileHandler: PlatformFileHandler,
    private val sendDispatchDelegate: SendDispatchDelegate,
) {
    private var nextUndoId = 0L

    fun remove(item: DraftItem) {
        val removed: RemovedDraftItem = when (item) {
            is DraftItem.Attachment -> {
                // The tray's own copy: the row may have been drawn a progress update ago.
                val files = fileHandler.attachedFiles.value
                val index = files.indexOfFirst { it.uri == item.file.uri }
                val file = files.getOrNull(index) ?: item.file
                fileHandler.removeFile(file)
                if (index < 0 || file.isUploading) return
                RemovedDraftItem.File(file, index)
            }

            is DraftItem.Skill -> {
                val index = handle.state.pendingManualSkills.indexOf(item.name)
                if (index < 0) return
                val heldBack = sendDispatchDelegate.removePendingManualSkill(item.name)
                RemovedDraftItem.Skill(item.name, index, heldBack)
            }

            is DraftItem.Quote -> {
                if (handle.state.pendingQuotes.getOrNull(item.index) != item.text) return
                sendDispatchDelegate.removePendingQuote(item.index)
                RemovedDraftItem.Quote(item.text, item.index)
            }
        }
        val undo = DraftUndo(id = ++nextUndoId, removed = removed)
        handle.update { pendingDraftUndo = undo }
    }

    /** Puts back the removal [id] names, if it is still the one pending. */
    fun undo(id: Long) {
        val undo = handle.state.composer.pendingDraftUndo?.takeIf { it.id == id } ?: return
        handle.update { pendingDraftUndo = null }
        when (val removed = undo.removed) {
            is RemovedDraftItem.File -> fileHandler.insertAt(removed.index, removed.file)
            is RemovedDraftItem.Skill ->
                sendDispatchDelegate.restorePendingManualSkill(removed.index, removed.name, removed.heldBack)
            is RemovedDraftItem.Quote -> sendDispatchDelegate.restorePendingQuote(removed.index, removed.text)
        }
    }

    /** The snackbar for [id] went away without an Undo. */
    fun dropUndo(id: Long) {
        handle.update { if (pendingDraftUndo?.id == id) pendingDraftUndo = null }
    }
}
