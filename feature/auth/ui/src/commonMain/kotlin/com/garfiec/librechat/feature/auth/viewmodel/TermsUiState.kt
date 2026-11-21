package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable

@Immutable
data class TermsUiState(
    /** The server's `modalContent` (Markdown), or blank when it sent none. */
    val termsText: String = "",
    /** The server's `modalTitle`, or null for the app's own title. */
    val title: String? = null,
    /** A startup config has arrived, so [termsText] and [title] are what the server sends. */
    val termsLoaded: Boolean = false,
    val isAccepted: Boolean = false,
    val isAccepting: Boolean = false,
    /** Loading the terms failed with no config to fall back on, or accepting them failed. */
    val error: String? = null,
)
