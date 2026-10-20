package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.chat.viewmodel.delegate.FavoritesDelegate

/*
 * The model selector's search, starred list and key gate, shared by the full grouped list and the
 * wide Compare setup's short list so the two can't answer the same query differently.
 */

private const val FUZZY_MATCH_THRESHOLD = 55

/** Queries this short match by substring; longer ones fuzzily. */
private const val SUBSTRING_QUERY_MAX = 2

/**
 * The [items] whose name matches [query]: a substring match for one or two characters (in list
 * order), a fuzzy match ranked best first beyond that. [rawQuery] is trimmed here, so every caller
 * reads "gp " as "gp"; it is expected non-blank.
 */
fun <T> searchByName(items: List<T>, rawQuery: String, nameOf: (T) -> String): List<T> {
    val query = rawQuery.trim()
    if (query.length <= SUBSTRING_QUERY_MAX) return items.filter { nameOf(it).contains(query, ignoreCase = true) }
    return items.map { it to FuzzyMatch.partialRatio(query, nameOf(it)) }
        .filter { (_, score) -> score >= FUZZY_MATCH_THRESHOLD }
        .sortedByDescending { (_, score) -> score }
        .map { (item, _) -> item }
}

/**
 * Starred models as (endpoint, model), in pin order ([favoriteModelKeys] is a LinkedHashSet), keeping
 * only those [models] still offers.
 */
fun starredModels(favoriteModelKeys: Set<String>, models: Map<String, List<String>>): List<Pair<String, String>> =
    favoriteModelKeys.mapNotNull { key ->
        val (endpoint, model) = FavoritesDelegate.parseFavoriteModelKey(key) ?: return@mapNotNull null
        if (models[endpoint]?.contains(model) == true) endpoint to model else null
    }

/**
 * A user-provided-key endpoint with no usable key: the selector greys it out behind "Set API Key".
 * Loading and absent (built-in) states fail open.
 */
fun KeyState?.blocksSelection(): Boolean = this is KeyState.Unset || this is KeyState.Expired
