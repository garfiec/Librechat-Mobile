package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Message

/** Where the author's original text of one redacted user turn stands. */
@Immutable
sealed interface OwnerTextState {
    data object Loading : OwnerTextState

    /** The server answered without it, or the request failed. Retry asks again. */
    data object Unavailable : OwnerTextState

    /** Valid only while the turn still has this [revision] and filtered [canonicalText]. */
    data class Loaded(val text: String, val revision: String, val canonicalText: String) : OwnerTextState
}

/** What a redacted user turn shows its author ([PrivateTextStatus]). */
enum class PrivateTextPhase { SHOWN, LOADING, UNAVAILABLE }

/** A redacted user turn's display: the text to draw, and the status under it. */
data class PrivateTextView(val text: String, val phase: PrivateTextPhase?)

/**
 * The owner's original for [message] when it was read against this exact stored turn (upstream
 * `PrivateText`: same revision, same filtered text), else the filtered text with why. A turn with
 * no state yet (the live turn, whose text is what was typed, or one not asked about yet) shows its
 * own text under the bare status line.
 */
fun privateTextView(message: Message, state: OwnerTextState?): PrivateTextView {
    if (state is OwnerTextState.Loaded && state.revision == message.privacyRevision && state.canonicalText == message.text) {
        return PrivateTextView(state.text, PrivateTextPhase.SHOWN)
    }
    return when (state) {
        OwnerTextState.Loading -> PrivateTextView(message.text, PrivateTextPhase.LOADING)
        null -> PrivateTextView(message.text, null)
        // Unavailable, or an original read against an older revision of the turn.
        else -> PrivateTextView(message.text, PrivateTextPhase.UNAVAILABLE)
    }
}
