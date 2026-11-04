package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.runtime.Immutable

/**
 * Display data for a prompt group in the library list view.
 */
@Immutable
data class PromptGroupDisplayData(
    val id: String,
    val name: String,
    val oneliner: String?,
    val category: String?,
    val authorName: String,
    val command: String?,
    val promptText: String?,
)

/**
 * Display data for the prompt group detail screen,
 * including command, production prompt text, and prompt metadata.
 */
@Immutable
data class PromptGroupDetailDisplayData(
    val id: String,
    val name: String,
    val oneliner: String?,
    val category: String?,
    val authorName: String,
    val command: String?,
    val productionId: String?,
    val productionPromptText: String?,
    val promptCount: Int,
    /**
     * Managed outside LibreChat (v0.8.9-rc1): no Edit or Delete, which the server refuses, and a
     * note saying where it is edited. Share and use are unaffected.
     */
    val external: ExternalPromptSource? = null,
    /** An external group whose live text could not be read: nothing to show or use. */
    val textUnavailable: Boolean = false,
    /** A Retry of that read is in flight. */
    val isRetryingText: Boolean = false,
)

/** An externally managed prompt group's store and its own name for the prompt. */
@Immutable
data class ExternalPromptSource(val isLangfuse: Boolean, val promptName: String?)
