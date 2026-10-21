package com.garfiec.librechat.feature.settings.model

import androidx.compose.runtime.Immutable

/**
 * One card on the Shared chats page.
 *
 * @property title the shared conversation's title; null when it has none, which the card words
 *   in the user's language.
 * @property createdAt when the link was made (ISO), not when it was last updated: the list
 *   carries no publish date.
 */
@Immutable
data class SharedLinkDisplayData(
    val shareId: String,
    val conversationId: String,
    val title: String?,
    val createdAt: String?,
    val url: String,
)
