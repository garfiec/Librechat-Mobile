package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions

/**
 * iOS `textContentType` roles that [KeyboardOptions.keyboardType] can't express. Compose
 * Multiplatform maps `KeyboardType.Email` to `.emailAddress`, but Password AutoFill pairs a
 * `.username` field with the password field. Android takes its autofill hints from
 * `Modifier.semantics { contentType = … }` instead.
 */
enum class IosTextContentType {
    Username,

    /** The account's existing password, for a field that confirms it (not a sign-in form's). */
    Password,
    NewPassword,
    OneTimeCode,

    /** An email address that is not the sign-in name, e.g. a new address to move the account to. */
    EmailAddress,
}

/** Sets the iOS `textContentType`; returns these options unchanged on Android. */
expect fun KeyboardOptions.withIosTextContentType(type: IosTextContentType): KeyboardOptions
