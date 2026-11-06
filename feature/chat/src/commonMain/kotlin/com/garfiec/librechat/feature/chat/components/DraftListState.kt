package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the attachment list ([ComposerDraftListPanel]) is open, and whether a screen reader opened
 * it, in which case it takes focus at its header ([focusHeader]): drawn above the composer, it comes
 * before the stack in traversal order. `ChatScreen` holds one and hands its toggles to the composer
 * through [withDraftList], so the stack, the failed-upload line and their tests all call the same ones.
 */
@Stable
internal class DraftListState(initiallyOpen: Boolean = false) {
    var isOpen by mutableStateOf(initiallyOpen)
        private set

    var focusHeader by mutableStateOf(false)
        private set

    /**
     * Opens or closes the list, from a touch or a screen reader's click ([fromScreenReader]). While
     * [blocked] (a pause panel holds the space it opens into) it only closes.
     */
    fun toggle(fromScreenReader: Boolean, blocked: Boolean) {
        if (blocked && !isOpen) return
        focusHeader = fromScreenReader && !isOpen
        isOpen = !isOpen
    }

    fun close() {
        isOpen = false
    }

    companion object {
        val Saver: Saver<DraftListState, Boolean> = Saver(save = { it.isOpen }, restore = { DraftListState(it) })
    }
}

@Composable
internal fun rememberDraftListState(): DraftListState = rememberSaveable(saver = DraftListState.Saver) { DraftListState() }

/** The least height the list is usable in: its header and one row. */
internal val DRAFT_LIST_MIN_HEIGHT = 48.dp + DRAFT_ROW_HEIGHT

/** Where the list's top edge goes ([top]), and whether it needs the keyboard down to fit ([needsRoom]). */
internal data class DraftListFit(val top: Dp, val needsRoom: Boolean)

/**
 * Fits the list into a screen [height] tall (above the keyboard) whose composer and gap take
 * [bottom]: below the floating top bar ([barBottom]) while that leaves [DRAFT_LIST_MIN_HEIGHT];
 * else under the bar's scrim, below only the status bar ([statusBarTop]); and when even that is too
 * little (the keyboard up on a short or landscape phone, a tall queue above the field), there too,
 * asking for the keyboard to go down so the list has its rows. Before the screen is measured
 * ([height] 0) it takes the bar's inset.
 */
internal fun draftListFit(height: Dp, bottom: Dp, barBottom: Dp, statusBarTop: Dp): DraftListFit = when {
    height <= 0.dp || height - bottom - barBottom >= DRAFT_LIST_MIN_HEIGHT -> DraftListFit(barBottom, needsRoom = false)
    height - bottom - statusBarTop >= DRAFT_LIST_MIN_HEIGHT -> DraftListFit(statusBarTop, needsRoom = false)
    else -> DraftListFit(statusBarTop, needsRoom = true)
}

/**
 * This chrome with the attachment list's state and toggles. [blocked]: a pause panel holds the space
 * above the composer, so the list reads closed and doesn't open.
 */
internal fun ComposerChrome.withDraftList(state: DraftListState, blocked: Boolean): ComposerChrome = copy(
    isDraftListOpen = state.isOpen && !blocked,
    onToggleDraftList = { state.toggle(fromScreenReader = false, blocked = blocked) },
    onToggleDraftListFromScreenReader = { state.toggle(fromScreenReader = true, blocked = blocked) },
    onFieldFocused = state::close,
)
