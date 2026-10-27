package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.config.ModelSpec
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
 * Starred specs in pin order ([favoriteSpecNames] is a LinkedHashSet), keeping only those among
 * [visibleSpecs], the picker's spec rows. A pin whose spec is hidden or gone shows nothing but stays
 * pinned. Web keeps a hidden one too, but its sidebar deletes a pin whose spec left the config.
 */
fun starredSpecs(favoriteSpecNames: Set<String>, visibleSpecs: List<ModelSpec>): List<ModelSpec> {
    val byName = visibleSpecs.associateBy { it.name }
    return favoriteSpecNames.mapNotNull { byName[it] }
}

/**
 * A user-provided-key endpoint with no usable key: the selector greys it out behind "Add key".
 * Loading and absent (built-in) states fail open.
 */
fun KeyState?.blocksSelection(): Boolean = keyHint() != null

/** What the model picker says under an endpoint or spec whose key [blocksSelection]. */
enum class KeyHint {
    /** No key on file: "Needs your key". */
    NEEDS_KEY,

    /** Past its expiry, in the minute before the server deletes it: "Key expired". */
    EXPIRED,
}

/** The [KeyHint] for this key state, or null when the endpoint is selectable. */
fun KeyState?.keyHint(): KeyHint? = when (this) {
    KeyState.Unset -> KeyHint.NEEDS_KEY
    is KeyState.Expired -> KeyHint.EXPIRED
    is KeyState.Set, KeyState.Loading, null -> null
}
