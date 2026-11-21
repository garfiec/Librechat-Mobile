package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * An App's request for the device location, while the user decides: first this app's own question
 * for [serverName] ([Stage.CONSENT], only when never answered), then the OS permission. [sessionId]
 * is the App that asked; only it can be answered or refused by it closing. [requestId] tells one
 * request from the next, so a repeated request from the same App is a new question.
 */
@Immutable
data class McpAppLocationRequest(
    val sessionId: Long,
    val serverName: String,
    val stage: Stage,
    val requestId: Long = 0,
) {
    enum class Stage { CONSENT, OS_PERMISSION }
}
