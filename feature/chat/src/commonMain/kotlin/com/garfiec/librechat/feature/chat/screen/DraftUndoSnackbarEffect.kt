package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.draft_item_removed
import com.garfiec.librechat.feature.chat.resources.draft_quote_removed
import com.garfiec.librechat.feature.chat.viewmodel.DraftUndo
import com.garfiec.librechat.feature.chat.viewmodel.RemovedDraftItem
import org.jetbrains.compose.resources.getString

/**
 * The Undo snackbar for the draft's last removal (a strip item's ×). Shown while [undo] is pending; when the
 * ViewModel drops it (a send, the queued-edit swap, a conversation switch) the effect restarts and
 * the snackbar goes with it. A snackbar that times out, or is cut short because the screen left
 * composition, drops its Undo through [onDrop].
 */
@Composable
internal fun DraftUndoSnackbarEffect(
    undo: DraftUndo?,
    snackbarHostState: SnackbarHostState,
    onUndo: (undoId: Long) -> Unit,
    onDrop: (undoId: Long) -> Unit,
) {
    val currentUndo by rememberUpdatedState(onUndo)
    val currentDrop by rememberUpdatedState(onDrop)
    LaunchedEffect(undo?.id) {
        val pending = undo ?: return@LaunchedEffect
        val message = when (val removed = pending.removed) {
            is RemovedDraftItem.File -> getString(Res.string.draft_item_removed, removed.file.name)
            is RemovedDraftItem.Skill -> getString(Res.string.draft_item_removed, removed.name)
            is RemovedDraftItem.Quote -> getString(Res.string.draft_quote_removed)
        }
        var undone = false
        try {
            if (snackbarHostState.showUndo(message)) {
                undone = true
                currentUndo(pending.id)
            }
        } finally {
            // Also when the screen leaves composition with the snackbar up (another nav entry, an
            // Activity recreated on a fold): a kept Undo would come back on return, long after the
            // removal. A no-op when the ViewModel already dropped it or a newer removal replaced it.
            if (!undone) currentDrop(pending.id)
        }
    }
}
