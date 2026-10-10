package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then

/** Upstream `Constants.COMMANDS_MAX_LENGTH`. */
internal const val COMMAND_MAX_LENGTH = 56

/**
 * Mirrors upstream `Command.tsx`'s `handleInputChange`: lowercase, whitespace to `-`, then only
 * `[a-z0-9-]`, and an edit whose result would exceed [COMMAND_MAX_LENGTH] is rejected rather than
 * truncated. The group update that carries the command rejects anything else with a 400, so input
 * this lets through fails the whole save.
 */
internal val CommandInputTransformation: InputTransformation =
    CommandNormalization.then(InputTransformation.maxLength(COMMAND_MAX_LENGTH))

/**
 * Edits the buffer in place instead of replacing its text, so the buffer carries the caret across
 * a dropped character; the `/` the field shows is its prefix, never part of the value.
 */
private object CommandNormalization : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        // Backwards, so a deletion never shifts an index still to be visited.
        for (i in length - 1 downTo 0) {
            val original = charAt(i)
            when (val normalized = normalizeCommandChar(original)) {
                null -> delete(i, i + 1)
                original -> Unit
                else -> replace(i, i + 1, normalized.toString())
            }
        }
    }
}

/**
 * The per-character rule, or null when the character is dropped. Char tests rather than a
 * `Regex`, which can parse on the JVM and still throw on Android.
 */
internal fun normalizeCommandChar(c: Char): Char? {
    if (c.isWhitespace()) return '-'
    val lower = c.lowercaseChar()
    return lower.takeIf { it in 'a'..'z' || it in '0'..'9' || it == '-' }
}
