package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    /** Opens or closes the list, from a touch or a screen reader's click ([fromScreenReader]). */
    fun toggle(fromScreenReader: Boolean) {
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

/**
 * The least height the list is usable in at 1x font: its header (12dp above a 20dp `titleSmall` line,
 * 4dp below), one row and the rows' 4dp bottom padding, as [ComposerDraftListPanel] lays them out.
 */
internal val DRAFT_LIST_MIN_HEIGHT = 12.dp + 20.dp + 4.dp + DRAFT_ROW_HEIGHT + 4.dp

/** Between the attachment list and what it sits on. */
internal val DRAFT_LIST_GAP = 4.dp

/**
 * Whether the list, below the floating top bar ([barBottom]) and above [bottom] (what it sits on, and
 * the gap), needs the keyboard down to get [DRAFT_LIST_MIN_HEIGHT] out of a screen [height] tall
 * above the keyboard: the keyboard up on a short or landscape phone, a tall queue above the field.
 * The list never goes higher than the bar, which draws over it and takes its taps. Before the screen
 * is measured ([height] 0) it fits.
 */
internal fun draftListNeedsRoom(height: Dp, bottom: Dp, barBottom: Dp): Boolean =
    height > 0.dp && height - bottom - barBottom < DRAFT_LIST_MIN_HEIGHT

/**
 * Where the list's bottom edge sits above the screen's: on the [composer], or, while a tool-approval
 * panel is docked above it, on that panel collapsed ([collapsedToolPanel], null without one), so the
 * list never covers the panel.
 */
internal fun draftListBottom(composer: Dp, collapsedToolPanel: Dp?): Dp = composer + (collapsedToolPanel ?: 0.dp) + DRAFT_LIST_GAP

/**
 * This chrome with the attachment list's state and toggles. [toolPauseExpanded]: a tool-approval
 * panel docked above the composer is expanded, taking the space the list opens into, so opening the
 * list collapses it ([onCollapseToolPause]) and the list opens above it. The list stays reachable
 * during the pause: it is the only place a failed file is retried or removed, and that file holds the
 * run's next send.
 */
internal fun ComposerChrome.withDraftList(
    state: DraftListState,
    toolPauseExpanded: Boolean = false,
    onCollapseToolPause: () -> Unit = {},
): ComposerChrome {
    fun toggle(fromScreenReader: Boolean) {
        state.toggle(fromScreenReader)
        if (state.isOpen && toolPauseExpanded) onCollapseToolPause()
    }
    return copy(
        isDraftListOpen = state.isOpen,
        onToggleDraftList = { toggle(fromScreenReader = false) },
        onToggleDraftListFromScreenReader = { toggle(fromScreenReader = true) },
        onFieldFocused = state::close,
    )
}

/**
 * Closes the list when a pause panel comes up ([pausePanelUp]), so the user sees the panel, and when
 * the tool-approval panel is expanded again over it ([toolPauseExpanded]).
 */
@Composable
internal fun DraftListPauseEffects(state: DraftListState, pausePanelUp: Boolean, toolPauseExpanded: Boolean) {
    LaunchedEffect(pausePanelUp) { if (pausePanelUp) state.close() }
    LaunchedEffect(toolPauseExpanded) { if (toolPauseExpanded) state.close() }
}
