package com.garfiec.librechat.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.semantics.contentDataType
import androidx.compose.ui.semantics.semantics

/**
 * Keeps a masked field that holds a secret other than the user's login (an API key, a gateway token,
 * a PDF password) out of the autofill service. A field masked with `PasswordVisualTransformation`
 * reports itself as a password field, so without this a password manager offers the LibreChat login
 * for it, and may offer to save a token as that login. Android only: iOS has no autofill manager in
 * Compose Multiplatform and takes its hints from `KeyboardType` alone.
 */
fun Modifier.excludeFromAutofill(): Modifier = semantics { contentDataType = ContentDataType.None }
