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
import androidx.compose.ui.layout.LayoutCoordinates
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
        snackbarHostState.currentSnackbarData?.dismiss()
        var undone = false
        try {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = getString(Res.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
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

/**
 * Where [closeOnTapOutside] measures from ([root], the layout it watches) and the attachment stack,
 * whose own tap toggles the list and so must not also count as outside. Plain fields: they are set
 * on layout and read when a gesture lands, never in composition.
 */
internal class DraftListTapTargets {
    var root: LayoutCoordinates? = null
    var stack: LayoutCoordinates? = null

    /** Whether [position], in [root]'s coordinates, lands on the stack. */
    fun onStack(position: Offset): Boolean {
        val from = root?.takeIf { it.isAttached } ?: return false
        val target = stack?.takeIf { it.isAttached } ?: return false
        return from.localBoundingBoxOf(target).contains(position)
    }
}
