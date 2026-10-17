package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions

/**
 * iOS `textContentType` roles that [KeyboardOptions.keyboardType] can't express. Compose
 * Multiplatform maps `KeyboardType.Email` to `.emailAddress`, but Password AutoFill pairs a
 * `.username` field with the password field. Android takes its autofill hints from
 * `Modifier.semantics { contentType = … }` instead.
 */
enum class IosTextContentType { Username, NewPassword, OneTimeCode }

/** Sets the iOS `textContentType`; returns these options unchanged on Android. */
expect fun KeyboardOptions.withIosTextContentType(type: IosTextContentType): KeyboardOptions
