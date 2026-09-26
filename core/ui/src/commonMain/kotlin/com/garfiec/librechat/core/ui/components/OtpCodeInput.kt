package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Six-box numeric OTP entry: one [BasicTextField] laid over a row of digit boxes.
 *
 * A single field (rather than six) is what makes the whole code pasteable in one action and keeps
 * focus handling trivial; the boxes are only a decoration. The real field is laid out invisibly
 * over the boxes so long-press → Paste works anywhere on the row, and its cursor is pinned to the
 * end so every edit is an append or a trailing delete — the box highlight is always truthful.
 *
 * [onValueChange] only ever receives [sanitizeOtpInput]'s result: ASCII digits, at most [length].
 * Callers can auto-submit on `value.length == length` without re-validating, but must guard
 * against a second submit, since composition can deliver the terminal value more than once.
 */
@Composable
fun OtpCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    length: Int = OTP_LENGTH,
) {
    // The IME owns the composition: rebuilding the value without it commits the composition and
    // restarts input on every keystroke, so carry it over while the field holds the accepted text.
    var reported by remember { mutableStateOf(TextFieldValue()) }
    val composition = reported.composition.takeIf { reported.text == value }
    CompositionLocalProvider(LocalTextSelectionColors provides HIDDEN_SELECTION_COLORS) {
        BasicTextField(
            value = TextFieldValue(value, selection = TextRange(value.length), composition = composition),
            onValueChange = { new ->
                reported = new
                val accepted = sanitizeOtpInput(old = value, new = new.text, length = length)
                if (accepted != null && accepted != value) onValueChange(accepted)
            },
            enabled = enabled,
            textStyle = HIDDEN_TEXT_STYLE,
            cursorBrush = HIDDEN_CURSOR_BRUSH,
            keyboardOptions = OTP_KEYBOARD_OPTIONS,
            modifier = modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                // Codes read left-to-right in every locale; the hidden text is LTR too, so the
                // paste toolbar anchors over the first box rather than the last in RTL.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box {
                        DigitBoxes(value = value, length = length)
                        Box(modifier = Modifier.matchParentSize()) { innerTextField() }
                    }
                }
            },
        )
    }
}

@Composable
private fun DigitBoxes(value: String, length: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        // The field itself carries the text semantics; the boxes would read every digit twice.
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
    ) {
        repeat(length) { index ->
            val char = value.getOrNull(index)?.toString() ?: ""
            val isFocused = index == value.length
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = DIGIT_BOX_SHAPE,
                    )
                    .background(MaterialTheme.colorScheme.surface, DIGIT_BOX_SHAPE),
            ) {
                Text(
                    text = char,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Maps a raw edit of the OTP field to the value to accept, or `null` to reject it.
 *
 * Any Unicode decimal digit is normalized to ASCII (an Arabic-locale keypad types `٣`, which the
 * server would reject) and everything else is dropped, so a pasted `"123 456"` or `"Code: 123456"`
 * lands as `123456`. An edit that overflows [length] is rejected — except when the text appended
 * to [old] is itself exactly one full code, which replaces the entry (pasting a fresh code over a
 * partial or stale one). A 7th typed digit, or a paste carrying extra numbers, changes nothing:
 * truncating it would submit a guess and spend a rate-limited attempt.
 */
fun sanitizeOtpInput(old: String, new: String, length: Int = OTP_LENGTH): String? {
    val digits = new.asciiDigits()
    if (digits.length <= length) return digits
    if (!new.startsWith(old)) return null
    return new.substring(old.length).asciiDigits().takeIf { it.length == length }
}

private fun String.asciiDigits(): String = buildString {
    for (c in this@asciiDigits) c.digitToIntOrNull()?.let { append('0' + it) }
}

const val OTP_LENGTH = 6

private val DIGIT_BOX_SHAPE = RoundedCornerShape(8.dp)

private val OTP_KEYBOARD_OPTIONS = KeyboardOptions(
    keyboardType = KeyboardType.Number,
    imeAction = ImeAction.Done,
)

private val HIDDEN_TEXT_STYLE = TextStyle(color = Color.Transparent)

private val HIDDEN_CURSOR_BRUSH = SolidColor(Color.Transparent)

private val HIDDEN_SELECTION_COLORS = TextSelectionColors(
    handleColor = Color.Transparent,
    backgroundColor = Color.Transparent,
)
