package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A text field's own [TextFieldValue] for a draft held by a ViewModel.
 *
 * When the draft reaches the screen through `combine`/`stateIn` (or any extra hop), it arrives a
 * frame after each keystroke. A field fed straight from it recomposes once with the old text, its
 * caret is clamped to that text, and on iOS the next character lands before the last one (in an
 * empty field the first character typed ends up last). This state keeps the field's text and caret
 * locally and reports edits up with [onValueChange].
 *
 * It still follows the ViewModel: a text arriving from [rememberDraftField] that is neither the
 * field's current text nor a lagging echo of something it typed is a write of the ViewModel's own
 * (a clear, a restored or composed draft, a reset), and replaces the field's text with the caret
 * at the end.
 */
@Stable
class DraftFieldState internal constructor(initial: String) {
    var value: TextFieldValue by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set

    private val echoes = DraftEchoes(initial)

    /** Stores [new] and calls [onTextChange] when its text differs from what the field held. */
    fun onValueChange(new: TextFieldValue, onTextChange: (String) -> Unit) {
        val changed = new.text != value.text
        value = new
        if (changed) {
            echoes.reported(new.text)
            onTextChange(new.text)
        }
    }

    internal fun follow(external: String) {
        if (echoes.isOwnWrite(external, value.text)) value = TextFieldValue(external, TextRange(external.length))
    }
}

/**
 * Remembers a [DraftFieldState] seeded from [text], following the ViewModel's own writes to it.
 * A new [resetKey] starts it over from [text].
 */
@Composable
fun rememberDraftField(text: String, resetKey: Any? = Unit): DraftFieldState {
    val field = remember(resetKey) { DraftFieldState(text) }
    SideEffect { field.follow(text) }
    return field
}

/**
 * [DraftFieldState] for a field built on [TextFieldState], which a field needs to take pasted,
 * dropped or keyboard-inserted content. The field edits [state] itself; its text reaches the
 * ViewModel through [rememberDraftTextField], and the ViewModel's own writes replace it the same way.
 */
@Stable
class DraftTextFieldState internal constructor(initial: String) {
    val state = TextFieldState(initial)

    private val echoes = DraftEchoes(initial)

    // The text this class wrote into [state], so seeing it come back isn't reported as typing.
    private var applied: String? = initial

    internal fun follow(external: String) {
        if (echoes.isOwnWrite(external, state.text.toString())) {
            applied = external
            state.setTextAndPlaceCursorAtEnd(external)
        }
    }

    /** Called with each new text of [state]; reports it unless it is the ViewModel's own write. */
    internal fun observed(text: String, onTextChange: (String) -> Unit) {
        val own = text == applied
        applied = null
        if (own) return
        echoes.reported(text)
        onTextChange(text)
    }
}

/**
 * Remembers a [DraftTextFieldState] seeded from [text]: edits to its state go to [onTextChange],
 * and the ViewModel's own writes to [text] replace the field's text. A new [resetKey] starts it
 * over from [text].
 */
@Composable
fun rememberDraftTextField(text: String, onTextChange: (String) -> Unit, resetKey: Any? = Unit): DraftTextFieldState {
    val field = remember(resetKey) { DraftTextFieldState(text) }
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    LaunchedEffect(field) {
        snapshotFlow { field.state.text.toString() }.collect { field.observed(it, currentOnTextChange) }
    }
    SideEffect { field.follow(text) }
    return field
}

/**
 * Tells a lagging echo of the field's own typing from a write the ViewModel made itself. Echoes
 * come back in order, but a conflating flow may skip some, so every reported text not yet echoed
 * is kept until a later one comes back.
 */
internal class DraftEchoes(private var lastExternal: String) {
    private val pending = ArrayDeque<String>()

    fun reported(text: String) {
        pending.addLast(text)
        // A ViewModel that drops edits (a save in flight) never echoes them; don't grow forever.
        if (pending.size > MAX_PENDING) pending.removeFirst()
    }

    /** Whether [external] is the ViewModel's own write, which the field (showing [fieldText]) must take. */
    fun isOwnWrite(external: String, fieldText: String): Boolean {
        val changed = external != lastExternal
        lastExternal = external
        val echoAt = pending.indexOf(external)
        return when {
            // Typing back to an earlier text ("ab", then backspace to "a") makes a lagging echo match
            // the field; the reports after it are still to come back.
            external == fieldText -> {
                if (echoAt >= 0) repeat(echoAt + 1) { pending.removeFirst() } else pending.clear()
                false
            }
            !changed -> false
            echoAt >= 0 -> {
                repeat(echoAt + 1) { pending.removeFirst() }
                false
            }
            else -> {
                pending.clear()
                true
            }
        }
    }

    private companion object {
        const val MAX_PENDING = 64
    }
}
