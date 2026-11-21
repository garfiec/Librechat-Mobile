package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable

@Immutable
data class PrefetchSettingsUiState(
    val enabled: Boolean,
    val attachmentsEnabled: Boolean,
    val onMeteredEnabled: Boolean,
    val depth: Int,
    /** Whether this platform has an image cache worth warming; false hides the toggle. */
    val attachmentsSupported: Boolean = false,
)
