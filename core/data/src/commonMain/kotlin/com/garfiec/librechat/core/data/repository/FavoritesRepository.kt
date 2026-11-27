package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.UserFavorite
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the user's pinned favorites list.
 *
 * Both the chat-side pin toggles and the Settings → Favorites screen
 * observe [favorites] and route writes through [setFavorites], so the
 * two surfaces never drift apart and concurrent writes can't lose each
 * other (the impl serializes them with a [kotlinx.coroutines.sync.Mutex]).
 */
interface FavoritesRepository {
    /**
     * Latest known favorites list. Empty until [refresh] or [setFavorites]
     * has populated it. UI should observe this rather than caching its own copy.
     */
    val favorites: StateFlow<List<UserFavorite>>

    /**
     * True once the active account's list has been read from the server. The server replaces the
     * whole list on every write, so a write built from [favorites] while this is false would drop
     * pins the client never saw.
     */
    val isLoaded: Boolean

    /** Fetches the canonical list from the server and publishes it to [favorites]. */
    suspend fun refresh(): Result<List<UserFavorite>>

    /**
     * Replaces the server-side favorites list with [list]. The new list is
     * published to [favorites] optimistically before the network call returns;
     * on error the cache is rolled back from the server. Concurrent calls are
     * serialized so no write is silently lost.
     */
    suspend fun setFavorites(list: List<UserFavorite>): Result<List<UserFavorite>>
}
