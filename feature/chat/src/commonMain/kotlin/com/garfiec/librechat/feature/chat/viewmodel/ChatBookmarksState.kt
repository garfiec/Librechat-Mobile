package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * What the open chat's bookmarks look like from the ⋮ menu. Owned by
 * [com.garfiec.librechat.feature.chat.viewmodel.delegate.ChatBookmarksDelegate].
 */
@Immutable
data class ChatBookmarksState(
    /** The chat carries at least one bookmark (Saved, the favorite star, doesn't count). */
    val hasBookmark: Boolean = false,
)
