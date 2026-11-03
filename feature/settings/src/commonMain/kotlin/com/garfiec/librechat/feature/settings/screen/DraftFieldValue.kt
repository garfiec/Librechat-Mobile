package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A field's own [TextFieldValue], seeded from [text] (caret at the end) and reseeded only when
 * [resetKey] changes. For a draft held by a ViewModel whose state reaches the screen through
 * `combine`/`stateIn`, a frame after each keystroke: a field fed straight from that state
 * recomposes once with the old text, and on the first keystroke into an empty field the caret is
 * clamped to 0, so on iOS the first character typed ends up last.
 *
 * Report edits with [onDraftChange], which forwards only a change of text.
 */
@Composable
internal fun rememberDraftFieldValue(text: String, resetKey: Any? = Unit): MutableState<TextFieldValue> =
    remember(resetKey) { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }

/** Stores [new] in [field] and calls [onTextChange] when its text differs from what the field held. */
internal fun onDraftChange(field: MutableState<TextFieldValue>, new: TextFieldValue, onTextChange: (String) -> Unit) {
    val changed = new.text != field.value.text
    field.value = new
    if (changed) onTextChange(new.text)
}
