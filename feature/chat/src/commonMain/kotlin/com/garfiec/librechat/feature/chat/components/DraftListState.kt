package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
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
 * The least height the list is usable in: its header (12dp above a [header] line, 4dp below), one row
 * and the rows' 4dp bottom padding, as [ComposerDraftListPanel] lays them out. A row is 52dp, or its
 * [name] and [subtitle] lines with their 4dp padding where those are taller. The lines are in sp, so
 * this follows the font size in effect: 92dp at 1x, about 130dp at 2x.
 */
internal fun Density.draftListMinHeight(header: TextStyle, name: TextStyle, subtitle: TextStyle): Dp {
    val row = maxOf(DRAFT_ROW_HEIGHT, 4.dp + lineHeightOf(name) + lineHeightOf(subtitle) + 4.dp)
    return 12.dp + lineHeightOf(header) + 4.dp + row + 4.dp
}

private fun Density.lineHeightOf(style: TextStyle): Dp {
    val lineHeight = style.lineHeight
    return when {
        lineHeight.isSp -> lineHeight.toDp()
        lineHeight.isEm && style.fontSize.isSp -> style.fontSize.toDp() * lineHeight.value
        style.fontSize.isSp -> style.fontSize.toDp()
        else -> 0.dp
    }
}

/** Between the attachment list and the composer it sits on. */
internal val DRAFT_LIST_GAP = 4.dp

/**
 * Whether the list, below the floating top bar ([barBottom]) and above [bottom] (what it sits on, and
 * the gap), needs the keyboard down to get [minHeight] ([draftListMinHeight]) out of a screen
 * [height] tall above the keyboard: the keyboard up on a short or landscape phone, a tall queue above
 * the field, a large font. The list never goes higher than the bar, which draws over it and takes its
 * taps. Before the screen is measured ([height] 0) it fits.
 */
internal fun draftListNeedsRoom(height: Dp, bottom: Dp, barBottom: Dp, minHeight: Dp): Boolean =
    height > 0.dp && height - bottom - barBottom < minHeight

/**
 * This chrome with the attachment list's state and toggles. [toolPause]: a tool-approval panel is
 * docked above the composer, in the space the list opens into, so the list stays shut
 * ([ComposerChrome.draftListShut]): the stack still shows what goes with the message but opens
 * nothing, and the failed-upload line says the file can be dealt with after the approval. The panel
 * is never collapsed or covered for the list.
 */
internal fun ComposerChrome.withDraftList(state: DraftListState, toolPause: Boolean = false): ComposerChrome {
    fun toggle(fromScreenReader: Boolean) {
        if (toolPause) state.close() else state.toggle(fromScreenReader)
    }
    return copy(
        isDraftListOpen = state.isOpen && !toolPause,
        draftListShut = toolPause,
        onToggleDraftList = { toggle(fromScreenReader = false) },
        onToggleDraftListFromScreenReader = { toggle(fromScreenReader = true) },
        onFieldFocused = state::close,
    )
}

/**
 * Closes the list when a pause panel comes up ([pausePanelUp]), so the user sees the panel; it
 * doesn't reopen when the pause ends.
 */
@Composable
internal fun DraftListPauseEffects(state: DraftListState, pausePanelUp: Boolean) {
    LaunchedEffect(pausePanelUp) { if (pausePanelUp) state.close() }
}
