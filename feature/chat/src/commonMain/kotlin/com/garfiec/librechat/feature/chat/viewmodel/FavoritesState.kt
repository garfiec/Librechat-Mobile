package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * User-pinned agents, models and model specs (v0.8.5 favorites) surfaced in [ModelSelectorSheet]. Owned by
 * [com.garfiec.librechat.feature.chat.viewmodel.delegate.FavoritesDelegate].
 */
@Immutable
data class FavoritesState(
    /**
     * User-pinned agent IDs. Pinned agents sort to the top of the "My Agents" group
     * in [ModelSelectorSheet] and get a filled star.
     */
    val favoriteAgentIds: Set<String> = emptySet(),
    /**
     * User-pinned model keys. Each key is `"$endpoint::$model"` — compare with
     * `FavoritesDelegate.favoriteModelKey(endpoint, model)`.
     */
    val favoriteModelKeys: Set<String> = emptySet(),
    /** User-pinned model spec names, in pin order. A pin outlives its spec leaving the config. */
    val favoriteSpecNames: Set<String> = emptySet(),
)
