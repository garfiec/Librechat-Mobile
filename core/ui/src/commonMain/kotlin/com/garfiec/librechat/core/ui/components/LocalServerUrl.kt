package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * The active account's server URL, for UI that loads files the server itself serves (provider art
 * under `{server}/assets/`). Null outside a signed-in shell (previews, tests): such UI draws its
 * offline fallback.
 */
val LocalServerUrl = compositionLocalOf<String?> { null }
