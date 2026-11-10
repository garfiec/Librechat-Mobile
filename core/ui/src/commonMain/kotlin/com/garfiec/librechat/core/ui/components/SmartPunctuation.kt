package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The iOS keyboard curls quotes and turns `--` into an em dash even with autocorrect off, and Compose
 * can't set `smartQuotesType`/`smartDashesType`. Fields that take text as typed undo it here.
 */
internal expect val keyboardInsertsSmartPunctuation: Boolean

/** Autocorrect off means the text must arrive as typed. A secret's text is never rewritten. */
internal fun KeyboardOptions.undoesSmartPunctuation(): Boolean =
    keyboardInsertsSmartPunctuation &&
        autoCorrectEnabled == false &&
        keyboardType != KeyboardType.Password &&
        keyboardType != KeyboardType.NumberPassword

/**
 * The typed characters back, with the selection moved past each em dash's extra character. Only the
 * span that changed since [previous] is touched: text already in the field (loaded, or a JSON string
 * value with a real apostrophe) stays as it was.
 */
internal fun TextFieldValue.withoutSmartPunctuation(previous: String): TextFieldValue {
    var start = 0
    val shortest = minOf(text.length, previous.length)
    while (start < shortest && text[start] == previous[start]) start++
    var end = text.length
    while (end > start && text.length - end < previous.length - start &&
        text[end - 1] == previous[previous.length - (text.length - end) - 1]
    ) {
        end--
    }
    if ((start until end).none { text[it] in SMART_PUNCTUATION }) return this
    val straight = StringBuilder(text.length + 4)
    val shift = IntArray(text.length + 1)
    text.forEachIndexed { index, char ->
        straight.append(if (index in start until end) SMART_PUNCTUATION[char] ?: char else char)
        shift[index + 1] = straight.length - (index + 1)
    }
    fun moved(offset: Int) = offset + shift[offset]
    return TextFieldValue(straight.toString(), TextRange(moved(selection.start), moved(selection.end)))
}

/** [withoutSmartPunctuation] for the [androidx.compose.foundation.text.input.TextFieldState] fields. */
internal object SmartPunctuationTransformation : InputTransformation {
    @OptIn(ExperimentalFoundationApi::class)
    override fun TextFieldBuffer.transformInput() {
        val changed = (0 until changes.changeCount).map { changes.getRange(it) }.sortedByDescending { it.min }
        for (range in changed) {
            for (index in range.max - 1 downTo range.min) {
                val straight = SMART_PUNCTUATION[charAt(index)] ?: continue
                replace(index, index + 1, straight)
            }
        }
    }
}

private val SMART_PUNCTUATION = mapOf(
    '“' to "\"",
    '”' to "\"",
    '‘' to "'",
    '’' to "'",
    '„' to "\"",
    '‚' to "'",
    '—' to "--",
)
