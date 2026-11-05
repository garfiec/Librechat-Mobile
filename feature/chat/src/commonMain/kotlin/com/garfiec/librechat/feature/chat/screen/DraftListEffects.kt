package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_undo
import com.garfiec.librechat.feature.chat.resources.draft_item_removed
import com.garfiec.librechat.feature.chat.resources.draft_quote_removed
import com.garfiec.librechat.feature.chat.viewmodel.DraftUndo
import com.garfiec.librechat.feature.chat.viewmodel.RemovedDraftItem
import org.jetbrains.compose.resources.getString

/**
 * The Undo snackbar for the attachment list's last removal. Shown while [undo] is pending; when the
 * ViewModel drops it (a send, the queued-edit swap, a conversation switch) the effect restarts and
 * the snackbar goes with it. A snackbar that times out drops its Undo through [onDrop].
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
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = getString(Res.string.action_undo),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) currentUndo(pending.id) else currentDrop(pending.id)
    }
}

/**
 * Calls [onClose] for a tap that lands outside what [keep] claims (positions in this layout's
 * coordinates). It only watches: the tap still reaches whatever it landed on, and a drag past touch
 * slop (a scroll of the thread) is not a tap. The lambdas are read when a gesture lands, so they
 * should read state rather than capture values.
 */
internal fun Modifier.closeOnTapOutside(
    enabled: Boolean,
    keep: (Offset) -> Boolean,
    onClose: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (keep(down.position)) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if ((change.position - down.position).getDistance() > slop) return@awaitEachGesture
                if (!change.pressed) {
                    onClose()
                    return@awaitEachGesture
                }
            }
        }
    }
}
