package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

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
