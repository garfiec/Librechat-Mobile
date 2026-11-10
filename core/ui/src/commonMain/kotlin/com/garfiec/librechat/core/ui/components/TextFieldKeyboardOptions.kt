package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

// Compose turns autocorrect on unless a field opts out, and both iOS and Android keyboards then
// rewrite what is typed. Any field whose text must arrive exactly as typed takes one of these.

/** URL fields. Space-separated values such as OAuth scopes take [VerbatimKeyboardOptions]. */
val UrlKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)

/** Codes, identifiers and structured text: usernames, header names, hex, cron, JSON. */
val VerbatimKeyboardOptions = KeyboardOptions(autoCorrectEnabled = false)

/**
 * [SecretTextField]'s options. The password type keeps the value out of the Android keyboard's
 * learned words and turns on iOS secure entry; masking alone does neither.
 */
internal val MaskedSecretKeyboardOptions =
    KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
